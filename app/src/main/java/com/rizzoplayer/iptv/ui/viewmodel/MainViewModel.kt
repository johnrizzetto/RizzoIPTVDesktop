package com.rizzoplayer.iptv.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizzoplayer.iptv.RizzoApp
import com.rizzoplayer.iptv.data.local.PlaybackPositionStore
import com.rizzoplayer.iptv.data.local.PreferencesStore
import com.rizzoplayer.iptv.data.local.ServersStore
import com.rizzoplayer.iptv.data.model.*
import com.rizzoplayer.iptv.data.repository.IPTVRepository
import com.rizzoplayer.iptv.data.repository.TmdbRepository
import com.rizzoplayer.iptv.data.repository.TorBoxRepository
import com.rizzoplayer.iptv.data.repository.StreamResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshotFlow

enum class Section { LIVE, VOD, SERIES, FAVORITES }

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
}

data class EpgInfo(
    val channelName: String = "",
    val nowTitle: String = "",
    val nowStart: String = "",
    val nextTitle: String = "",
    val nextStart: String = ""
)

data class PlaybackPrep(
    val title: String,
    val stage: Stage,
    val message: String
) {
    enum class Stage { SEARCHING, QUEUING, CACHING, READY, FAILED }
}

data class StreamSelectionState(
    val streams: List<TorrentioStream>,
    val title: String,
    val contentType: String,
    val contentId: String,
    val icon: String?
)

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
    val streamSelection: StreamSelectionState? = null,
    val playbackPrep: PlaybackPrep? = null
)

class MainViewModel(
    val repository: IPTVRepository,
    private val tmdbRepository: TmdbRepository,
    private val torBoxRepository: TorBoxRepository,
    private val serversStore: ServersStore,
    private val preferencesStore: PreferencesStore,
    application: Application
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

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
    private var playbackPrepJob: Job? = null

    private val recentUrlCache = mutableMapOf<String, String>() // id → url
    private val favoriteUrlCache = mutableMapOf<String, String>() // id → url
    private fun launchUrlCacheRefresh() {
        val creds = _state.value.credentials ?: return
        viewModelScope.launch(Dispatchers.IO) {
            // Refresh recent URLs
            val newRecentCache = mutableMapOf<String, String>()
            recentlyWatched.value.take(8).forEach { item ->
                try {
                    val id = item.id.toIntOrNull() ?: return@forEach
                    val url = when (item.type) {
                        "live" -> repository.getLiveUrl(creds, id)
                        "vod" -> repository.getVodUrl(creds, id)
                        "episode" -> repository.getEpisodeUrl(creds, id, item.ext ?: "mp4")
                        else -> return@forEach
                    }
                    newRecentCache[item.id] = url
                } catch (_: Exception) {}
            }
            synchronized(recentUrlCache) {
                recentUrlCache.clear()
                recentUrlCache.putAll(newRecentCache)
            }
            // Refresh favorite URLs
            val newFavCache = mutableMapOf<String, String>()
            favorites.value.values.forEach { fav ->
                try {
                    val id = fav.id.toIntOrNull() ?: return@forEach
                    val url = when (fav.type) {
                        "live" -> repository.getLiveUrl(creds, id)
                        "vod" -> repository.getVodUrl(creds, id)
                        "episode" -> repository.getEpisodeUrl(creds, id, fav.ext ?: "mp4")
                        else -> return@forEach
                    }
                    newFavCache[fav.id] = url
                } catch (_: Exception) {}
            }
            synchronized(favoriteUrlCache) {
                favoriteUrlCache.clear()
                favoriteUrlCache.putAll(newFavCache)
            }
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
                .debounce(400)
                .distinctUntilChanged()
                .collectLatest { query ->
                    val section = _state.value.section
                    if (query.length < 2 || (section != Section.VOD && section != Section.SERIES)) return@collectLatest
                    loadTmdb {
                        if (section == Section.VOD)
                            BrowseContent.TmdbMovies(tmdbRepository.searchMovies(query), "Search: $query")
                        else
                            BrowseContent.TmdbShows(tmdbRepository.searchShows(query), "Search: $query")
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
            Category("-1", "🔥 Popular"),
            Category("-2", "⭐ Top Rated"),
            Category("-3", "🎬 Now Playing"),
            Category("-4", "📈 Trending"),
            Category("-5", "🍿 Top Netflix"),
            Category("-6", "🍎 Top Apple TV+")
        ) + genres
        BrowseContent.Categories(all, Section.VOD)
    }

    private fun loadSeriesCategories() = loadTmdb {
        val genres = tmdbRepository.getTvGenres().map { Category(it.id.toString(), it.name) }
        val all = listOf(
            Category("-1", "🔥 Popular"),
            Category("-2", "⭐ Top Rated"),
            Category("-3", "📺 Airing Today"),
            Category("-4", "📈 Trending"),
            Category("-5", "🍿 Top Netflix"),
            Category("-6", "🍎 Top Apple TV+")
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
        backStack.addLast(_state.value.content to scrollPosition)
        if (mode == Section.LIVE) {
            load(pushBack = true) { creds ->
                BrowseContent.LiveStreams(repository.getLiveStreams(creds, category.id))
            }
        } else {
            val genreId = category.id.toIntOrNull() ?: return
            if (genreId < 0) {
                loadTmdb(pushBack = true) {
                    if (mode == Section.VOD) {
                        val items = when (genreId) {
                            -1 -> tmdbRepository.getPopularMovies()
                            -2 -> tmdbRepository.getTopRatedMovies()
                            -3 -> tmdbRepository.getNowPlayingMovies()
                            -4 -> tmdbRepository.getTrendingMovies()
                            -5 -> tmdbRepository.getNetflixMovies()
                            -6 -> tmdbRepository.getAppleMovies()
                            else -> tmdbRepository.getPopularMovies()
                        }
                        BrowseContent.TmdbMovies(items, category.name)
                    } else {
                        val items = when (genreId) {
                            -1 -> tmdbRepository.getPopularShows()
                            -2 -> tmdbRepository.getTopRatedShows()
                            -3 -> tmdbRepository.getOnTheAirShows()
                            -4 -> tmdbRepository.getTrendingShows()
                            -5 -> tmdbRepository.getNetflixShows()
                            -6 -> tmdbRepository.getAppleShows()
                            else -> tmdbRepository.getPopularShows()
                        }
                        BrowseContent.TmdbShows(items, category.name)
                    }
                }
            } else {
                loadTmdb(pushBack = true) {
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
        loadTmdb(pushBack = true) {
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
    _state.update { it.copy(content = savedContent, canGoBack = backStack.isNotEmpty(), searchQuery = "", restoreScrollIndex = scrollPos) }
}

    fun clearScrollRestore() {
        if (_state.value.restoreScrollIndex >= 0) {
            _state.update { it.copy(restoreScrollIndex = -1) }
        }
    }

    fun retry() {
        _state.update { it.copy(error = null) }
        selectSection(_state.value.section)
    }

    /** Dismiss transient errors while keeping the user on the current page. */
    fun retryCurrent() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Re-execute the last content load block, refetching data.
     * Used when user explicitly taps "Reload" after a failed load.
     */
    fun retryReload() {
        _state.update { it.copy(error = null) }
        val block = lastLoadBlock ?: return selectSection(_state.value.section)
        val creds = _state.value.credentials ?: return
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) { block(creds) }
                _state.update { it.copy(isLoading = false, content = content, canGoBack = backStack.isNotEmpty()) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
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
                _state.update { it.copy(isLoading = false) }
                val title = detail.title
                playbackPrepJob?.cancel()
                playbackPrepJob = viewModelScope.launch {
                    torBoxRepository.resolveMovie(imdbId).collect { resolution ->
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
                                val recentRefs = buildRecentRefsFromCache(excludeId = detail.id.toString())
                                val favRefs = buildFavoriteRefsFromCache(excludeId = detail.id.toString())
                                _playEvent.tryEmit(PlayEvent(resolution.url, title, "vod", recentRefs, favRefs))
                            }
                            is StreamResolution.Failed -> {
                                _state.update { it.copy(playbackPrep = null, error = resolution.reason) }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to play movie") }
            }
        }
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

    fun playSelectedStream(stream: TorrentioStream) {
        val selection = _state.value.streamSelection ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, streamSelection = null) }
            val resolvedUrl = resolveStreamUrl(stream.url)
            val recent = RecentItem(selection.contentId, selection.title, selection.contentType, selection.icon)
            _state.update { it.copy(isLoading = false, nowPlaying = recent) }
            repository.recentlyWatchedStore.add(recent)
            _playEvent.tryEmit(PlayEvent(
                url = resolvedUrl,
                title = selection.title,
                contentType = selection.contentType,
                contentId = selection.contentId
            ))
        }
    }

    fun dismissStreamSelection() {
        _state.update { it.copy(streamSelection = null) }
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

    // ── Private helpers ───────────────────────────────────────────────────

    private fun load(pushBack: Boolean = false, block: suspend (Credentials) -> BrowseContent) {
        val creds = _state.value.credentials ?: return
        _state.update { it.copy(isLoading = true, error = null, searchQuery = "") }
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

    private fun loadTmdb(pushBack: Boolean = false, block: suspend () -> BrowseContent) {
        _state.update { it.copy(isLoading = true, error = null, searchQuery = "") }
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) { block() }
                _state.update { it.copy(isLoading = false, content = content, canGoBack = backStack.isNotEmpty()) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
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