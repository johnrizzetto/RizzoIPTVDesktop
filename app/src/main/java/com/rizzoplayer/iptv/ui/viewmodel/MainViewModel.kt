package com.rizzoplayer.iptv.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizzoplayer.iptv.RizzoApp
import com.rizzoplayer.iptv.data.local.PlaybackPositionStore
import kotlinx.coroutines.FlowPreview
import com.rizzoplayer.iptv.data.local.PreferencesStore
import com.rizzoplayer.iptv.data.local.ServersStore
import com.rizzoplayer.iptv.data.model.*
import com.rizzoplayer.iptv.data.repository.IPTVRepository
import com.rizzoplayer.iptv.data.repository.TmdbRepository
import com.rizzoplayer.iptv.data.repository.TorBoxRepository
import com.rizzoplayer.iptv.data.repository.StreamResolution
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@Stable
enum class Section { LIVE, VOD, SERIES, FAVORITES }

@Stable
sealed class BrowseContent {
    object Empty : BrowseContent()
    data class Categories(val items: List<Category>, val mode: Section) : BrowseContent()
    data class LiveStreams(val items: List<LiveStream>) : BrowseContent()
    data class VodStreams(val items: List<VodStream>) : BrowseContent()
    data class SeriesList(val items: List<Series>) : BrowseContent()
    data class Episodes(val seasons: Map<String, List<Episode>>) : BrowseContent()
    data class Favorites(val items: Map<String, Favorite>) : BrowseContent()
    data class TmdbMovies(val items: List<TmdbMovie>, val genreName: String) : BrowseContent()
    data class TmdbShows(val items: List<TmdbShow>, val genreName: String) : BrowseContent()
    data class TmdbShowDetail(val show: TmdbShow, val seasons: List<TmdbSeason>) : BrowseContent()
    data class TmdbMovieDetail(val movie: TmdbMovie) : BrowseContent()
    data class TmdbSearchResults(val movies: List<TmdbMovie>, val shows: List<TmdbShow>, val query: String) : BrowseContent()
}

@Immutable
data class EpgInfo(
    val channelName: String = "",
    val nowTitle: String = "",
    val nowStart: String = "",
    val nextTitle: String = "",
    val nextStart: String = ""
)

@Immutable
data class PlaybackPrep(
    val title: String,
    val stage: Stage,
    val message: String
) {
    enum class Stage { SEARCHING, QUEUING, CACHING, READY, FAILED }
}

@Immutable
data class TmdbStreamSelectionState(
    val streams: List<UnifiedTorrent>,
    val title: String,
    val imdbId: String,
    val tmdbId: String,
    val contentId: String
)

@Stable
data class MainUiState(
    val section: Section = Section.LIVE,
    val content: BrowseContent = BrowseContent.Empty,
    val isLoading: Boolean = false,
    val error: String? = null,
    val toastMessage: String? = null,
    val searchQuery: String = "",
    val epgInfo: EpgInfo? = null,
    val canGoBack: Boolean = false,
    val credentials: Credentials? = null,
    val nowPlaying: RecentItem? = null,
    val restoreScrollIndex: Int = -1,
    val restoreGridScrollIndex: Int = -1,
    val currentGridScrollPosition: Int = 0,
    val isGridLoading: Boolean = false,
    val isSearchLoading: Boolean = false,
    val tmdbStreamSelection: TmdbStreamSelectionState? = null,
    val playbackPrep: PlaybackPrep? = null,
    val parentalLockActive: Boolean = false,
)

// ── Constants ─────────────────────────────────────────────────────────────────
private const val SEARCH_DEBOUNCE_MS = 150L

@OptIn(FlowPreview::class)
class MainViewModel(
    val repository: IPTVRepository,
    private val tmdbRepository: TmdbRepository,
    private val torBoxRepository: TorBoxRepository,
    private val serversStore: ServersStore,
    val preferencesStore: PreferencesStore,
    application: Application
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    /** Derives NavHost route from state.section — stays in sync automatically. */
    val currentRoute: StateFlow<String> = _state.map { state ->
        when (state.section) {
            Section.LIVE      -> "live"
            Section.VOD       -> "movies"
            Section.SERIES    -> "shows"
            Section.FAVORITES -> "favorites"
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "live")

    fun screenToSection(route: String): Section = when (route) {
        "live"      -> Section.LIVE
        "movies"   -> Section.VOD
        "shows"    -> Section.SERIES
        "favorites" -> Section.FAVORITES
        else       -> Section.LIVE
    }

    private val _playEvent = MutableSharedFlow<PlayEvent>(extraBufferCapacity = 1)
    val playEvent: SharedFlow<PlayEvent> = _playEvent.asSharedFlow()

    val favorites: StateFlow<Map<String, Favorite>> = repository.favoritesStore.favorites
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Order-sensitive list for UI — emits when move() changes sort order. */
    val favoritesList: StateFlow<List<Favorite>> = repository.favoritesStore.favoritesList
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentlyWatched: StateFlow<List<RecentItem>> = repository.recentlyWatchedStore.items
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val savedServers: StateFlow<List<ServerConfig>> = serversStore.servers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _continueWatching = MutableStateFlow<List<RecentItem>>(emptyList())
    val continueWatching: StateFlow<List<RecentItem>> = _continueWatching.asStateFlow()

    // ── TMDB prefetch debounce ───────────────────────────────────────────
    private var lastPrefetchMovieId: Int = -1
    private var lastPrefetchMovieTime: Long = 0L
    private var lastPrefetchShowId: Int = -1
    private var lastPrefetchShowTime: Long = 0L

    fun prefetchMovie(id: Int) {
        val now = System.currentTimeMillis()
        if (id == lastPrefetchMovieId && now - lastPrefetchMovieTime < 500) return
        lastPrefetchMovieId = id
        lastPrefetchMovieTime = now
        viewModelScope.launch {
            try { tmdbRepository.getMovieDetail(id) } catch (_: Exception) {}
        }
    }

    fun prefetchShow(id: Int) {
        val now = System.currentTimeMillis()
        if (id == lastPrefetchShowId && now - lastPrefetchShowTime < 500) return
        lastPrefetchShowId = id
        lastPrefetchShowTime = now
        viewModelScope.launch {
            try { tmdbRepository.getShowDetail(id) } catch (_: Exception) {}
        }
    }

    // ── Speculative stream resolution ──────────────────────────────────
    // Deduplicates in-flight resolutions; entries expire after 60s
    private val speculativeStreams = ConcurrentHashMap<String, Deferred<Result<String>>>()
    private val SPECULATIVE_TTL_MS = 60_000L

    private fun streamKey(imdbId: String, season: Int?, episode: Int?) =
        if (season != null && episode != null) "$imdbId:S${season}E${episode}" else imdbId

    /**
     * Start resolving a stream URL speculatively. Idempotent — concurrent calls
     * for the same key share the same Deferred. Entries expire after 60s so
     * stale resolutions are not reused.
     */
    fun prefetchStream(imdbId: String, season: Int? = null, episode: Int? = null) {
        val key = streamKey(imdbId, season, episode)
        speculativeStreams[key]?.let { return } // already in-flight or cached
        speculativeStreams[key] = viewModelScope.async(start = CoroutineStart.LAZY) {
            val url = if (season != null && episode != null) {
                torBoxRepository.resolveFirst(imdbId, season, episode)
            } else {
                torBoxRepository.resolveFirstMovie(imdbId)
            }
            if (url != null) {
                Result.success(url)
            } else {
                Result.failure(Exception("No stream available"))
            }
        }.also { _ ->
            viewModelScope.launch {
                kotlinx.coroutines.delay(SPECULATIVE_TTL_MS)
                speculativeStreams.remove(key)
            }
        }
    }

    /**
     * Await a previously prefetched stream URL. Returns null if nothing is
     * in-flight or cached for this key.
     */
    suspend fun awaitStream(imdbId: String, season: Int? = null, episode: Int? = null): String? {
        val key = streamKey(imdbId, season, episode)
        return speculativeStreams[key]?.await()?.getOrNull()
    }

    // ── Playback fallback ────────────────────────────────────────────────
    private var currentFallbackHashes: List<String> = emptyList()
    private var currentPlaybackTitle: String = ""
    private var currentContentType: String = ""

    fun onPlaybackError() {
        val hashes = currentFallbackHashes
        if (hashes.isEmpty()) {
            // No fallbacks available — surface an error only if this was a TorBox play
            if (currentPlaybackTitle.isNotEmpty()) {
                _state.update { it.copy(error = "Playback failed — no alternative streams available") }
            }
            return
        }
        playbackPrepJob?.cancel()
        playbackPrepJob = viewModelScope.launch {
            _state.update { it.copy(playbackPrep = PlaybackPrep(currentPlaybackTitle, PlaybackPrep.Stage.SEARCHING, "Trying fallback stream...")) }
            try {
                torBoxRepository.resolveFallback(hashes.first()).collect { resolution ->
                    when (resolution) {
                        is StreamResolution.Searching -> {
                            _state.update { it.copy(playbackPrep = PlaybackPrep(currentPlaybackTitle, PlaybackPrep.Stage.SEARCHING, "Trying fallback stream...")) }
                        }
                        is StreamResolution.Queuing -> {
                            _state.update { it.copy(playbackPrep = PlaybackPrep(currentPlaybackTitle, PlaybackPrep.Stage.QUEUING, "Queuing fallback...")) }
                        }
                        is StreamResolution.Caching -> {
                            _state.update { it.copy(playbackPrep = PlaybackPrep(currentPlaybackTitle, PlaybackPrep.Stage.CACHING, "Caching fallback ${resolution.percent}%")) }
                        }
                        is StreamResolution.Ready -> {
                            currentFallbackHashes = emptyList()
                            _state.update { it.copy(playbackPrep = null) }
                            _playEvent.tryEmit(PlayEvent(resolution.url, currentPlaybackTitle, currentContentType, emptyList(), emptyList()))
                        }
                        is StreamResolution.Failed -> {
                            val remaining = hashes.drop(1)
                            if (remaining.isNotEmpty()) {
                                currentFallbackHashes = remaining
                                onPlaybackError()
                            } else {
                                currentFallbackHashes = emptyList()
                                _state.update { it.copy(playbackPrep = null, error = resolution.reason) }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(playbackPrep = null, error = e.message ?: "Fallback failed") }
            }
        }
    }

    private suspend fun resolveStreamUrl(url: String): String = withContext(Dispatchers.IO) {
        try {
            var current = url
            repeat(5) {
                val connection = java.net.URL(current).openConnection() as java.net.HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.requestMethod = "HEAD"
                connection.connect()
                val code = connection.responseCode
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (code in 300..399 && !location.isNullOrBlank()) {
                    current = if (location.startsWith("http")) location
                    else java.net.URL(java.net.URL(current), location).toString()
                } else {
                    return@withContext current
                }
            }
            current
        } catch (e: Exception) { url }
    }

    private val backStack = ArrayDeque<Pair<BrowseContent, Int>>()
    private val preloaded = mutableSetOf<Section>()
    private var lastLoadBlock: (suspend (Credentials) -> BrowseContent)? = null
    private var lastTmdbBlock: (suspend () -> BrowseContent)? = null
    private var searchJob: Job? = null
    private var playbackPrepJob: Job? = null

    private val recentUrlCache = mutableMapOf<String, String>() // id → url
    private val favoriteUrlCache = mutableMapOf<String, String>() // id → url
    private fun launchUrlCacheRefresh() {
        val creds = _state.value.credentials ?: return
        viewModelScope.launch(Dispatchers.IO) {
            // Refresh recent URLs in parallel
            val recentDeferreds = recentlyWatched.value.take(8).map { item ->
                async {
                    try {
                        val id = item.id.toIntOrNull() ?: return@async null
                        val url = when (item.type) {
                            "live" -> repository.getLiveUrl(creds, id)
                            "vod" -> repository.getVodUrl(creds, id)
                            "episode" -> repository.getEpisodeUrl(creds, id, item.ext ?: "mp4")
                            else -> return@async null
                        }
                        item.id to url
                    } catch (_: Exception) { null }
                }
            }
            val newRecentCache = recentDeferreds.awaitAll().filterNotNull().toMap()
            recentUrlCache.clear()
            recentUrlCache.putAll(newRecentCache)

            // Refresh favorite URLs in parallel
            val favDeferreds = favorites.value.values.map { fav ->
                async {
                    try {
                        val id = fav.id.toIntOrNull() ?: return@async null
                        val url = when (fav.type) {
                            "live" -> repository.getLiveUrl(creds, id)
                            "vod" -> repository.getVodUrl(creds, id)
                            "episode" -> repository.getEpisodeUrl(creds, id, fav.ext ?: "mp4")
                            else -> return@async null
                        }
                        fav.id to url
                    } catch (_: Exception) { null }
                }
            }
            val newFavCache = favDeferreds.awaitAll().filterNotNull().toMap()
            favoriteUrlCache.clear()
            favoriteUrlCache.putAll(newFavCache)
        }
    }

    init {
        viewModelScope.launch {
            repository.credentialsStore.credentials.collect { creds ->
                _state.update { it.copy(credentials = creds) }
            }
        }

        viewModelScope.launch {
            snapshotFlow { _state.value.searchQuery }
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { rawQuery ->
                    searchJob?.cancel()
                    val query = rawQuery.trim()
                    if (query.length < 2) {
                        _state.update {
                            if (it.content is BrowseContent.TmdbSearchResults)
                                it.copy(content = BrowseContent.Empty, isSearchLoading = false)
                            else it.copy(isSearchLoading = false)
                        }
                        return@collect
                    }
                    searchJob = viewModelScope.launch {
                        _state.update { it.copy(isSearchLoading = true, error = null) }
                        try {
                            val result = withContext(Dispatchers.IO) {
                                kotlinx.coroutines.withTimeout(18_000L) {
                                    tmdbRepository.searchAll(query)
                                }
                            }
                            val (movies, shows) = result
                            ensureActive()
                            _state.update {
                                if (it.searchQuery.trim() != query) it
                                else it.copy(
                                    content = BrowseContent.TmdbSearchResults(movies, shows, query),
                                    isSearchLoading = false,
                                    canGoBack = backStack.isNotEmpty()
                                )
                            }
                        } catch (_: CancellationException) {
                            // expected on next keystroke; do nothing
                        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                            _state.update { it.copy(isSearchLoading = false, error = "Search timed out") }
                        } catch (e: Exception) {
                            _state.update { it.copy(isSearchLoading = false, error = e.message ?: "Search failed") }
                        }
                    }
                }
        }

        viewModelScope.launch {
            recentlyWatched.drop(1).collect { launchUrlCacheRefresh() }
        }
        viewModelScope.launch {
            favorites.drop(1).collect { launchUrlCacheRefresh() }
        }
        viewModelScope.launch {
            state.mapNotNull { it.credentials }.distinctUntilChanged().collect { launchUrlCacheRefresh() }
        }

        viewModelScope.launch {
            recentlyWatched.collect { items ->
                val playbackStore = (application as RizzoApp).playbackPositionStore
                _continueWatching.value = items.filter { item ->
                    val key = "${item.type}:${item.id}"
                    val progress = playbackStore.getProgress(key)
                    progress != null && !progress.isWatched && progress.positionMs > 30_000L
                }
            }
        }
    }

    // ── Restore last section ───────────────────────────────────────────────

    fun restoreLastSection(): Section {
        return try {
            Section.valueOf(preferencesStore.getLastSection())
        } catch (_: Exception) {
            Section.LIVE
        }
    }

    // ── Server switching ──────────────────────────────────────────────────

    fun switchServer(server: ServerConfig) {
        viewModelScope.launch {
            repository.credentialsStore.save(server.toCredentials())
            repository.clearCache()
            backStack.clear()
            preloaded.clear()
            selectSection(Section.LIVE)
        }
    }

    // ── Section navigation ────────────────────────────────────────────────

    fun selectSection(section: Section) {
        backStack.clear()
        _state.update { it.copy(section = section, canGoBack = false, searchQuery = "", epgInfo = null, restoreScrollIndex = -1) }
        preferencesStore.saveLastSection(section.name)
        when (section) {
            Section.LIVE -> loadLiveCategories()
            Section.VOD -> loadVodCategories()
            Section.SERIES -> loadSeriesCategories()
            Section.FAVORITES -> loadFavorites()
        }
        preloadOtherCategories(section)
    }

    private fun preloadOtherCategories(current: Section) {
        val creds = _state.value.credentials ?: return
        viewModelScope.launch {
            listOf(Section.LIVE, Section.VOD, Section.SERIES)
                .filter { it != current && it !in preloaded }
                .forEach { section ->
                    preloaded.add(section)
                    launch {
                        try {
                            when (section) {
                                Section.LIVE -> repository.getLiveCategories(creds)
                                Section.VOD -> tmdbRepository.getMovieGenres()
                                Section.SERIES -> tmdbRepository.getTvGenres()
                                else -> {}
                            }
                        } catch (_: Exception) {}
                    }
                }
        }
    }

    private fun loadLiveCategories() = load {
        BrowseContent.Categories(repository.getLiveCategories(it).sortedByUS(), Section.LIVE)
    }

    private fun loadVodCategories() = loadTmdb {
        val genres = tmdbRepository.getMovieGenres().map { Category(it.id.toString(), it.name) }
        val all = listOf(
            // ── FEATURED ───
            Category("H:featured", "─── FEATURED ───"),
            Category("-1",  "🔥 Popular"),
            Category("-2",  "⭐ Top Rated"),
            Category("-3",  "🎬 Now Playing"),
            Category("-4",  "📈 Trending"),
            // ── STREAMING ───
            Category("H:platforms", "─── STREAMING ───"),
            Category("-5",  "🍿 Netflix"),
            Category("-6",  "🎬 Prime Video"),
            Category("-7",  "✨ Disney+"),
            Category("-8",  "📺 Hulu"),
            Category("-9",  "🎭 Paramount+"),
            // ── MOODS & DISCOVERY ───
            Category("H:moods", "─── MOODS & DISCOVERY ───"),
            Category("-10", "🆕 New Releases"),
            Category("-11", "🏆 Critically Acclaimed"),
            Category("-12", "💰 Box Office"),
            Category("-13", "🎞️ Classics"),
            Category("-14", "🇰🇷 Korean"),
            Category("-15", "🔮 Upcoming"),
            // ── GENRES ───
            Category("H:genres", "─── GENRES ───")
        ) + genres
        BrowseContent.Categories(all, Section.VOD)
    }

    private fun loadSeriesCategories() = loadTmdb {
        val genres = tmdbRepository.getTvGenres().map { Category(it.id.toString(), it.name) }
        val all = listOf(
            // ── FEATURED ───
            Category("H:featured", "─── FEATURED ───"),
            Category("-1",  "🔥 Popular"),
            Category("-2",  "⭐ Top Rated"),
            Category("-3",  "📺 Airing Today"),
            Category("-4",  "📈 Trending"),
            // ── STREAMING ───
            Category("H:platforms", "─── STREAMING ───"),
            Category("-5",  "🍿 Netflix"),
            Category("-6",  "🎬 Prime Video"),
            Category("-7",  "✨ Disney+"),
            Category("-8",  "📺 Hulu"),
            Category("-9",  "🎭 Paramount+"),
            Category("-10", "🦚 Peacock"),
            // ── MOODS & DISCOVERY ───
            Category("H:moods", "─── MOODS & DISCOVERY ───"),
            Category("-11", "🏆 Critically Acclaimed"),
            Category("-12", "🎌 Anime"),
            Category("-13", "📺 Reality TV"),
            Category("-14", "🎬 Documentaries"),
            Category("-15", "📱 Mini Series"),
            Category("-16", "👶 Kids"),
            Category("-17", "🇰🇷 Korean Dramas"),
            // ── GENRES ───
            Category("H:genres", "─── GENRES ───")
        ) + genres
        BrowseContent.Categories(all, Section.SERIES)
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            val favs = repository.favoritesStore.favorites.first()
            _state.update { it.copy(content = BrowseContent.Favorites(favs), isLoading = false) }
        }
    }

    // ── Content selection ─────────────────────────────────────────────────

    fun selectCategory(category: Category, mode: Section, scrollPosition: Int = 0) {
        if (category.id.startsWith("H:")) return
        val currentPos = _state.value.currentGridScrollPosition
        backStack.addLast(_state.value.content to currentPos)
        if (mode == Section.LIVE) {
            load { creds ->
                BrowseContent.LiveStreams(repository.getLiveStreams(creds, category.id))
            }
        } else {
            val genreId = category.id.toIntOrNull() ?: return
            if (genreId < 0) {
                loadTmdb {
                    if (mode == Section.VOD) {
                        val items = when (genreId) {
                            -1  -> tmdbRepository.getPopularMovies()
                            -2  -> tmdbRepository.getTopRatedMovies()
                            -3  -> tmdbRepository.getNowPlayingMovies()
                            -4  -> tmdbRepository.getTrendingMovies()
                            -5  -> tmdbRepository.getNetflixMovies()
                            -6  -> tmdbRepository.getPrimeMovies()
                            -7  -> tmdbRepository.getDisneyMovies()
                            -8  -> tmdbRepository.getHuluMovies()
                            -9  -> tmdbRepository.getParamountMovies()
                            -10 -> tmdbRepository.getNewReleaseMovies()
                            -11 -> tmdbRepository.getCriticallyAcclaimedMovies()
                            -12 -> tmdbRepository.getBoxOfficeMovies()
                            -13 -> tmdbRepository.getClassicMovies()
                            -14 -> tmdbRepository.getKoreanMovies()
                            -15 -> tmdbRepository.getUpcomingMovies()
                            else -> tmdbRepository.getPopularMovies()
                        }
                        BrowseContent.TmdbMovies(items, category.name)
                    } else {
                        val items = when (genreId) {
                            -1  -> tmdbRepository.getPopularShows()
                            -2  -> tmdbRepository.getTopRatedShows()
                            -3  -> tmdbRepository.getOnTheAirShows()
                            -4  -> tmdbRepository.getTrendingShows()
                            -5  -> tmdbRepository.getNetflixShows()
                            -6  -> tmdbRepository.getPrimeShows()
                            -7  -> tmdbRepository.getDisneyShows()
                            -8  -> tmdbRepository.getHuluShows()
                            -9  -> tmdbRepository.getParamountShows()
                            -10 -> tmdbRepository.getPeacockShows()
                            -11 -> tmdbRepository.getCriticallyAcclaimedShows()
                            -12 -> tmdbRepository.getAnimeShows()
                            -13 -> tmdbRepository.getRealityShows()
                            -14 -> tmdbRepository.getDocumentaryShows()
                            -15 -> tmdbRepository.getMiniSeries()
                            -16 -> tmdbRepository.getKidsShows()
                            -17 -> tmdbRepository.getKoreanDramas()
                            else -> tmdbRepository.getPopularShows()
                        }
                        BrowseContent.TmdbShows(items, category.name)
                    }
                }
            } else {
                loadTmdb {
                    if (mode == Section.VOD) {
                        BrowseContent.TmdbMovies(tmdbRepository.getMoviesByGenre(genreId), category.name)
                    } else {
                        BrowseContent.TmdbShows(tmdbRepository.getShowsByGenre(genreId), category.name)
                    }
                }
            }
        }
    }

    fun selectTmdbShow(show: TmdbShow) {
        backStack.addLast(_state.value.content to 0)
        loadTmdb {
            val detail = tmdbRepository.getShowDetail(show.id) ?: throw Exception("Show not found")
            val seasons = tmdbRepository.getSeasons(show.id, detail.numberOfSeasons)
            BrowseContent.TmdbShowDetail(detail, seasons)
        }
    }

    fun selectTmdbMovie(movie: TmdbMovie) {
        backStack.addLast(_state.value.content to 0)
        _state.update { it.copy(content = BrowseContent.TmdbMovieDetail(movie), canGoBack = true) }
    }

    fun goBack() {
    if (backStack.isEmpty()) { selectSection(_state.value.section); return }
    val (savedContent, scrollPos) = backStack.removeLast()
    val isGrid = savedContent is BrowseContent.TmdbMovies || savedContent is BrowseContent.TmdbShows
    _state.update {
        it.copy(
            content = savedContent,
            canGoBack = backStack.isNotEmpty(),
            searchQuery = "",
            restoreScrollIndex = if (isGrid) -1 else scrollPos,
            restoreGridScrollIndex = if (isGrid) scrollPos else -1
        )
    }
}

    fun clearGridScrollRestore() {
        if (_state.value.restoreGridScrollIndex >= 0) {
            _state.update { it.copy(restoreGridScrollIndex = -1) }
        }
    }

    fun updateGridScroll(position: Int) {
        _state.update { it.copy(currentGridScrollPosition = position) }
    }

    fun retry() {
        retryReload()
    }

    /** Dismiss transient errors while keeping the user on the current page. */
    fun retryCurrent() {
        _state.update { it.copy(error = null) }
    }

    /** Clear error state without reloading. */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Re-execute the last content load block, refetching data.
     * Used when user explicitly taps "Reload" after a failed load.
     */
    fun retryReload() {
        _state.update { it.copy(error = null) }
        val tmdbBlock = lastTmdbBlock
        val liveBlock = lastLoadBlock
        when {
            tmdbBlock != null -> {
                viewModelScope.launch {
                    try {
                        val content = withContext(Dispatchers.IO) { tmdbBlock() }
                        _state.update { it.copy(isLoading = false, content = content, canGoBack = backStack.isNotEmpty()) }
                    } catch (e: Exception) {
                        _state.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
                    }
                }
            }
            liveBlock != null -> {
                val creds = _state.value.credentials ?: return
                viewModelScope.launch {
                    try {
                        val content = withContext(Dispatchers.IO) { liveBlock(creds) }
                        _state.update { it.copy(isLoading = false, content = content, canGoBack = backStack.isNotEmpty()) }
                    } catch (e: Exception) {
                        _state.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
                    }
                }
            }
            else -> selectSection(_state.value.section)
        }
    }

    // ── Playback preparation overlay ──────────────────────────────────────

    val playbackPrep: StateFlow<PlaybackPrep?> = _state.map { it.playbackPrep }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun cancelPlaybackPrep() {
        playbackPrepJob?.cancel()
        playbackPrepJob = null
        _state.update { it.copy(playbackPrep = null) }
    }

    fun clearToast() {
        _state.update { it.copy(toastMessage = null) }
    }

    // ── Play actions ──────────────────────────────────────────────────────

    private fun buildRecentRefsFromCache(excludeId: String? = null): List<ChannelRef> = synchronized(recentUrlCache) {
        recentlyWatched.value
            .filter { it.id != excludeId }
            .take(8)
            .mapNotNull { item ->
                val url = recentUrlCache[item.id] ?: return@mapNotNull null
                ChannelRef(item.name, url, item.icon)
            }
    }

    private fun buildFavoriteRefsFromCache(excludeId: String? = null): List<ChannelRef> = synchronized(favoriteUrlCache) {
        favorites.value.values
            .filter { it.id != excludeId }
            .mapNotNull { fav ->
                val url = favoriteUrlCache[fav.id] ?: return@mapNotNull null
                ChannelRef(fav.name, url, fav.icon)
            }
    }

    fun onPlayLive(stream: LiveStream) {
        currentFallbackHashes = emptyList()
        currentPlaybackTitle = ""
        val creds = _state.value.credentials ?: return
        val url = repository.getLiveUrl(creds, stream.id)
        val recent = RecentItem(stream.id.toString(), stream.name, "live", stream.icon)
        _state.update { it.copy(nowPlaying = recent) }
        loadEpg(stream.id, stream.name)
        viewModelScope.launch {
            repository.recentlyWatchedStore.add(recent)
            val recentRefs = buildRecentRefsFromCache(excludeId = stream.id.toString())
            val favRefs = buildFavoriteRefsFromCache(excludeId = stream.id.toString())
            _playEvent.tryEmit(PlayEvent(url, stream.name, "live", recentRefs, favRefs))
        }
    }

    fun onPlayTmdbMovie(movie: TmdbMovie) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val detail = tmdbRepository.getMovieDetail(movie.id)
                if (detail == null) {
                    _state.update { it.copy(isLoading = false, toastMessage = "Failed to load movie details") }
                    return@launch
                }
                val imdbId = detail.imdbId
                if (imdbId == null) {
                    _state.update { it.copy(isLoading = false, toastMessage = "Not available on TorBox") }
                    return@launch
                }
                val title = detail.title
                // Fetch streams and show picker
                val streams = try {
                    torBoxRepository.fetchMovieStreams(imdbId)
                } catch (e: Exception) {
                    _state.update { it.copy(isLoading = false, error = "Failed to search: ${e.message}") }
                    return@launch
                }
                if (streams.isEmpty()) {
                    _state.update { it.copy(isLoading = false, error = "No streams found") }
                    return@launch
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        tmdbStreamSelection = TmdbStreamSelectionState(
                            streams = streams,
                            title = title,
                            imdbId = imdbId,
                            tmdbId = detail.id.toString(),
                            contentId = detail.id.toString()
                        )
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to play movie") }
            }
        }
    }

    fun playSelectedTmdbStream(stream: UnifiedTorrent) {
        val selection = _state.value.tmdbStreamSelection ?: return
        _state.update { it.copy(tmdbStreamSelection = null) }
        currentFallbackHashes = selection.streams
            .filter { it.url != stream.url }
            .mapNotNull { it.hash }
            .distinct()
            .take(5)
        currentPlaybackTitle = selection.title
        currentContentType = "vod"
        playbackPrepJob?.cancel()
        playbackPrepJob = viewModelScope.launch {
            torBoxRepository.resolveMovie(selection.imdbId).collect { resolution ->
                when (resolution) {
                    is StreamResolution.Searching -> {
                        _state.update { it.copy(isLoading = true, playbackPrep = PlaybackPrep(selection.title, PlaybackPrep.Stage.SEARCHING, "Searching torrent...")) }
                    }
                    is StreamResolution.Queuing -> {
                        _state.update { it.copy(playbackPrep = PlaybackPrep(selection.title, PlaybackPrep.Stage.QUEUING, "Queuing torrent...")) }
                    }
                    is StreamResolution.Caching -> {
                        _state.update { it.copy(playbackPrep = PlaybackPrep(selection.title, PlaybackPrep.Stage.CACHING, "Caching ${resolution.percent}%")) }
                    }
                    is StreamResolution.Ready -> {
                        _state.update { it.copy(isLoading = false, playbackPrep = null) }
                        val recent = RecentItem(selection.contentId, selection.title, "vod", null)
                        _state.update { it.copy(nowPlaying = recent) }
                        repository.recentlyWatchedStore.add(recent)
                        _playEvent.tryEmit(PlayEvent(resolution.url, selection.title, "vod", emptyList(), emptyList()))
                    }
                    is StreamResolution.Failed -> {
                        _state.update { it.copy(isLoading = false, playbackPrep = null, error = resolution.reason) }
                    }
                }
            }
        }
    }

    fun dismissTmdbStreamSelection() {
        _state.update { it.copy(tmdbStreamSelection = null) }
    }

    fun onPlayTmdbEpisode(show: TmdbShow, episode: TmdbEpisode) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val imdbId = show.imdbId
                if (imdbId == null) {
                    _state.update { it.copy(isLoading = false, toastMessage = "Not available on TorBox") }
                    return@launch
                }
                _state.update { it.copy(isLoading = false) }
                val title = "${show.name} S${episode.seasonNumber}E${episode.episodeNumber} — ${episode.name}"
                playbackPrepJob?.cancel()
                playbackPrepJob = viewModelScope.launch {
                    torBoxRepository.resolveEpisode(imdbId, episode.seasonNumber, episode.episodeNumber).collect { resolution ->
                        when (resolution) {
                            is StreamResolution.Searching -> {
                                _state.update { it.copy(playbackPrep = PlaybackPrep(title, PlaybackPrep.Stage.SEARCHING, "Searching torrent...")) }
                            }
                            is StreamResolution.Queuing -> {
                                _state.update { it.copy(playbackPrep = PlaybackPrep(title, PlaybackPrep.Stage.QUEUING, "Queuing torrent...")) }
                            }
                            is StreamResolution.Caching -> {
                                _state.update { it.copy(playbackPrep = PlaybackPrep(title, PlaybackPrep.Stage.CACHING, "Caching ${resolution.percent}%")) }
                            }
                            is StreamResolution.Ready -> {
                                _state.update { it.copy(playbackPrep = null) }
                                currentFallbackHashes = resolution.fallbackHashes
                                currentPlaybackTitle = title
                                currentContentType = "episode"
                                val stableId = "${show.id}:${episode.seasonNumber}:${episode.episodeNumber}"
                                val recentRefs = buildRecentRefsFromCache(excludeId = stableId)
                                val favRefs = buildFavoriteRefsFromCache(excludeId = stableId)
                                _playEvent.tryEmit(PlayEvent(resolution.url, title, "episode", recentRefs, favRefs))
                            }
                            is StreamResolution.Failed -> {
                                _state.update { it.copy(playbackPrep = null, error = resolution.reason) }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to play episode") }
            }
        }
    }

    fun onPlayRecent(item: RecentItem) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                if (item.type == "tmdb_movie") {
                    val tmdbId = item.id.toIntOrNull() ?: return@launch
                    val detail = tmdbRepository.getMovieDetail(tmdbId) ?: return@launch
                    val imdbId = detail.imdbId ?: return@launch
                    _state.update { it.copy(isLoading = false) }
                    playbackPrepJob?.cancel()
                    playbackPrepJob = viewModelScope.launch {
                        torBoxRepository.resolveMovie(imdbId).collect { resolution ->
                            when (resolution) {
                                is StreamResolution.Searching -> {
                                    _state.update { it.copy(playbackPrep = PlaybackPrep(item.name, PlaybackPrep.Stage.SEARCHING, "Searching torrent...")) }
                                }
                                is StreamResolution.Queuing -> {
                                    _state.update { it.copy(playbackPrep = PlaybackPrep(item.name, PlaybackPrep.Stage.QUEUING, "Queuing torrent...")) }
                                }
                                is StreamResolution.Caching -> {
                                    _state.update { it.copy(playbackPrep = PlaybackPrep(item.name, PlaybackPrep.Stage.CACHING, "Caching ${resolution.percent}%")) }
                                }
                                is StreamResolution.Ready -> {
                                    _state.update { it.copy(playbackPrep = null) }
                                    currentFallbackHashes = resolution.fallbackHashes
                                    currentPlaybackTitle = item.name
                                    currentContentType = item.type
                                    val recentRefs = buildRecentRefsFromCache(excludeId = item.id)
                                    val favRefs = buildFavoriteRefsFromCache(excludeId = item.id)
                                    _playEvent.tryEmit(PlayEvent(resolution.url, item.name, item.type, recentRefs, favRefs))
                                }
                                is StreamResolution.Failed -> {
                                    _state.update { it.copy(playbackPrep = null, error = resolution.reason) }
                                }
                            }
                        }
                    }
                    return@launch
                }
                if (item.type == "tmdb_episode") {
                    val parts = item.id.split(":")
                    if (parts.size != 3) { _state.update { it.copy(isLoading = false) }; return@launch }
                    val showId = parts[0].toIntOrNull() ?: return@launch
                    val season = parts[1].toIntOrNull() ?: return@launch
                    val episodeNum = parts[2].toIntOrNull() ?: return@launch
                    val detail = tmdbRepository.getShowDetail(showId) ?: return@launch
                    val imdbId = detail.imdbId ?: return@launch
                    _state.update { it.copy(isLoading = false) }
                    playbackPrepJob?.cancel()
                    playbackPrepJob = viewModelScope.launch {
                        torBoxRepository.resolveEpisode(imdbId, season, episodeNum).collect { resolution ->
                            when (resolution) {
                                is StreamResolution.Searching -> {
                                    _state.update { it.copy(playbackPrep = PlaybackPrep(item.name, PlaybackPrep.Stage.SEARCHING, "Searching torrent...")) }
                                }
                                is StreamResolution.Queuing -> {
                                    _state.update { it.copy(playbackPrep = PlaybackPrep(item.name, PlaybackPrep.Stage.QUEUING, "Queuing torrent...")) }
                                }
                                is StreamResolution.Caching -> {
                                    _state.update { it.copy(playbackPrep = PlaybackPrep(item.name, PlaybackPrep.Stage.CACHING, "Caching ${resolution.percent}%")) }
                                }
                                is StreamResolution.Ready -> {
                                    _state.update { it.copy(playbackPrep = null) }
                                    currentFallbackHashes = resolution.fallbackHashes
                                    currentPlaybackTitle = item.name
                                    currentContentType = item.type
                                    val recentRefs = buildRecentRefsFromCache(excludeId = item.id)
                                    val favRefs = buildFavoriteRefsFromCache(excludeId = item.id)
                                    _playEvent.tryEmit(PlayEvent(resolution.url, item.name, item.type, recentRefs, favRefs))
                                }
                                is StreamResolution.Failed -> {
                                    _state.update { it.copy(playbackPrep = null, error = resolution.reason) }
                                }
                            }
                        }
                    }
                    return@launch
                }
                currentFallbackHashes = emptyList()
                currentPlaybackTitle = ""
                val creds = _state.value.credentials ?: return@launch
                val url = when (item.type) {
                    "live" -> repository.getLiveUrl(creds, item.id.toIntOrNull() ?: return@launch)
                    "vod" -> repository.getVodUrl(creds, item.id.toIntOrNull() ?: return@launch)
                    "episode" -> repository.getEpisodeUrl(creds, item.id.toIntOrNull() ?: return@launch, item.ext ?: "mp4")
                    else -> return@launch
                }
                _state.update { it.copy(isLoading = false, nowPlaying = item) }
                repository.recentlyWatchedStore.add(item)
                val recentRefs = buildRecentRefsFromCache(excludeId = item.id)
                val favRefs = buildFavoriteRefsFromCache(excludeId = item.id)
                _playEvent.tryEmit(PlayEvent(url, item.name, item.type, recentRefs, favRefs))
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to play recent") }
            }
        }
    }

    fun onPlayFavorite(fav: Favorite) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                if (fav.type == "tmdb_movie") {
                    val tmdbId = fav.id.toIntOrNull() ?: return@launch
                    val detail = tmdbRepository.getMovieDetail(tmdbId) ?: return@launch
                    val imdbId = detail.imdbId ?: return@launch
                    _state.update { it.copy(isLoading = false) }
                    playbackPrepJob?.cancel()
                    playbackPrepJob = viewModelScope.launch {
                        torBoxRepository.resolveMovie(imdbId).collect { resolution ->
                            when (resolution) {
                                is StreamResolution.Searching -> {
                                    _state.update { it.copy(playbackPrep = PlaybackPrep(fav.name, PlaybackPrep.Stage.SEARCHING, "Searching torrent...")) }
                                }
                                is StreamResolution.Queuing -> {
                                    _state.update { it.copy(playbackPrep = PlaybackPrep(fav.name, PlaybackPrep.Stage.QUEUING, "Queuing torrent...")) }
                                }
                                is StreamResolution.Caching -> {
                                    _state.update { it.copy(playbackPrep = PlaybackPrep(fav.name, PlaybackPrep.Stage.CACHING, "Caching ${resolution.percent}%")) }
                                }
                                is StreamResolution.Ready -> {
                                    _state.update { it.copy(playbackPrep = null) }
                                    currentFallbackHashes = resolution.fallbackHashes
                                    currentPlaybackTitle = fav.name
                                    currentContentType = fav.type
                                    val recentRefs = buildRecentRefsFromCache(excludeId = fav.id)
                                    val favRefs = buildFavoriteRefsFromCache(excludeId = fav.id)
                                    _playEvent.tryEmit(PlayEvent(resolution.url, fav.name, fav.type, recentRefs, favRefs))
                                }
                                is StreamResolution.Failed -> {
                                    _state.update { it.copy(playbackPrep = null, error = resolution.reason) }
                                }
                            }
                        }
                    }
                    return@launch
                }
                if (fav.type == "tmdb_episode") {
                    val parts = fav.id.split(":")
                    if (parts.size != 3) { _state.update { it.copy(isLoading = false) }; return@launch }
                    val showId = parts[0].toIntOrNull() ?: return@launch
                    val season = parts[1].toIntOrNull() ?: return@launch
                    val episodeNum = parts[2].toIntOrNull() ?: return@launch
                    val detail = tmdbRepository.getShowDetail(showId) ?: return@launch
                    val imdbId = detail.imdbId ?: return@launch
                    _state.update { it.copy(isLoading = false) }
                    playbackPrepJob?.cancel()
                    playbackPrepJob = viewModelScope.launch {
                        torBoxRepository.resolveEpisode(imdbId, season, episodeNum).collect { resolution ->
                            when (resolution) {
                                is StreamResolution.Searching -> {
                                    _state.update { it.copy(playbackPrep = PlaybackPrep(fav.name, PlaybackPrep.Stage.SEARCHING, "Searching torrent...")) }
                                }
                                is StreamResolution.Queuing -> {
                                    _state.update { it.copy(playbackPrep = PlaybackPrep(fav.name, PlaybackPrep.Stage.QUEUING, "Queuing torrent...")) }
                                }
                                is StreamResolution.Caching -> {
                                    _state.update { it.copy(playbackPrep = PlaybackPrep(fav.name, PlaybackPrep.Stage.CACHING, "Caching ${resolution.percent}%")) }
                                }
                                is StreamResolution.Ready -> {
                                    _state.update { it.copy(playbackPrep = null) }
                                    currentFallbackHashes = resolution.fallbackHashes
                                    currentPlaybackTitle = fav.name
                                    currentContentType = fav.type
                                    val recentRefs = buildRecentRefsFromCache(excludeId = fav.id)
                                    val favRefs = buildFavoriteRefsFromCache(excludeId = fav.id)
                                    _playEvent.tryEmit(PlayEvent(resolution.url, fav.name, fav.type, recentRefs, favRefs))
                                }
                                is StreamResolution.Failed -> {
                                    _state.update { it.copy(playbackPrep = null, error = resolution.reason) }
                                }
                            }
                        }
                    }
                    return@launch
                }
                currentFallbackHashes = emptyList()
                currentPlaybackTitle = ""
                val creds = _state.value.credentials ?: return@launch
                val url = when (fav.type) {
                    "live" -> repository.getLiveUrl(creds, fav.id.toIntOrNull() ?: return@launch)
                    "vod" -> repository.getVodUrl(creds, fav.id.toIntOrNull() ?: return@launch)
                    "episode" -> repository.getEpisodeUrl(creds, fav.id.toIntOrNull() ?: return@launch, fav.ext ?: "mp4")
                    else -> return@launch
                }
                _state.update { it.copy(isLoading = false) }
                val recentRefs = buildRecentRefsFromCache(excludeId = fav.id)
                val favRefs = buildFavoriteRefsFromCache(excludeId = fav.id)
                _playEvent.tryEmit(PlayEvent(url, fav.name, fav.type, recentRefs, favRefs))
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to play favorite") }
            }
        }
    }

    // ── EPG ──────────────────────────────────────────────────────────────

    fun loadEpg(streamId: Int, channelName: String) {
        val creds = _state.value.credentials ?: return
        viewModelScope.launch {
            try {
                val epg = repository.getShortEpg(creds, streamId) ?: return@launch
                val listings = epg.listings ?: return@launch
                val now = listings.getOrNull(0)
                val next = listings.getOrNull(1)
                _state.update {
                    it.copy(
                        epgInfo = EpgInfo(
                            channelName = channelName,
                            nowTitle = now?.let { l -> repository.decodeEpgTitle(l.title) } ?: "",
                            nowStart = now?.start?.drop(11)?.take(5) ?: "",
                            nextTitle = next?.let { l -> repository.decodeEpgTitle(l.title) } ?: "",
                            nextStart = next?.start?.drop(11)?.take(5) ?: ""
                        )
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun clearEpg() = _state.update { it.copy(epgInfo = null) }

    // ── Search ────────────────────────────────────────────────────────────

    fun setSearchQuery(q: String) = _state.update { it.copy(searchQuery = q) }

    // ── Favorites ─────────────────────────────────────────────────────────

    fun toggleFavorite(id: String, name: String, type: String, ext: String? = null, icon: String? = null) {
        viewModelScope.launch {
            val current = repository.favoritesStore.favorites.first()
            if (current.containsKey(id)) {
                repository.favoritesStore.remove(id)
            } else {
                repository.favoritesStore.add(Favorite(id, name, type, ext, icon))
            }
        }
    }

    fun moveFavorite(id: String, direction: Int) {
        viewModelScope.launch { repository.favoritesStore.move(id, direction) }
    }

    // ── Auth ──────────────────────────────────────────────────────────────

    fun logout() {
        viewModelScope.launch {
            repository.credentialsStore.clear()
            repository.clearCache()
        }
    }

    fun isParentalLockEnabled(): Boolean = preferencesStore.isParentalLockEnabled()

    fun setParentalLockEnabled(enabled: Boolean) {
        preferencesStore.setParentalLockEnabled(enabled)
    }

    fun setParentalLockPin(pin: String) {
        preferencesStore.setParentalLockPin(pin)
    }

    fun unlockParentalLock() {
        _state.update { it.copy(parentalLockActive = false) }
    }

    fun lockParentalLock() {
        _state.update { it.copy(parentalLockActive = true) }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun load(block: suspend (Credentials) -> BrowseContent) {
        val creds = _state.value.credentials ?: return
        _state.update { it.copy(isLoading = true, error = null) }
        lastLoadBlock = block
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) { block(creds) }
                _state.update { it.copy(isLoading = false, content = content, canGoBack = backStack.isNotEmpty()) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    private fun loadTmdb(block: suspend () -> BrowseContent) {
        lastTmdbBlock = block
        _state.update { it.copy(isLoading = true, isGridLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) { block() }
                _state.update { it.copy(isLoading = false, isGridLoading = false, content = content, canGoBack = backStack.isNotEmpty()) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, isGridLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }


    private fun List<Category>.sortedByUS() = sortedWith { a, b ->
        val aUS = a.name.contains("US|") || a.name.startsWith("US") || a.name.contains("|US")
        val bUS = b.name.contains("US|") || b.name.startsWith("US") || b.name.contains("|US")
        when {
            aUS && !bUS -> -1
            !aUS && bUS -> 1
            else -> a.name.compareTo(b.name, ignoreCase = true)
        }
    }
}