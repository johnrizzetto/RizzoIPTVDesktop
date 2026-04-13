package com.rizzoplayer.iptv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizzoplayer.iptv.data.local.PreferencesStore
import com.rizzoplayer.iptv.data.local.ServersStore
import com.rizzoplayer.iptv.data.model.*
import com.rizzoplayer.iptv.data.repository.IPTVRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Section { LIVE, VOD, SERIES, FAVORITES }

sealed class BrowseContent {
    object Empty : BrowseContent()
    data class Categories(val items: List<Category>, val mode: Section) : BrowseContent()
    data class LiveStreams(val items: List<LiveStream>) : BrowseContent()
    data class VodStreams(val items: List<VodStream>) : BrowseContent()
    data class SeriesList(val items: List<Series>) : BrowseContent()
    data class Episodes(val seasons: Map<String, List<Episode>>) : BrowseContent()
    data class Favorites(val items: Map<String, Favorite>) : BrowseContent()
}

data class EpgInfo(
    val channelName: String = "",
    val nowTitle: String = "",
    val nowStart: String = "",
    val nextTitle: String = "",
    val nextStart: String = ""
)

data class MainUiState(
    val section: Section = Section.LIVE,
    val content: BrowseContent = BrowseContent.Empty,
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val epgInfo: EpgInfo? = null,
    val canGoBack: Boolean = false,
    val credentials: Credentials? = null,
    val nowPlaying: RecentItem? = null,
    val restoreScrollIndex: Int = -1
)

class MainViewModel(
    val repository: IPTVRepository,
    private val serversStore: ServersStore,
    private val preferencesStore: PreferencesStore
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val _playEvent = MutableSharedFlow<PlayEvent>(extraBufferCapacity = 1)
    val playEvent: SharedFlow<PlayEvent> = _playEvent.asSharedFlow()

    val favorites: StateFlow<Map<String, Favorite>> = repository.favoritesStore.favorites
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val recentlyWatched: StateFlow<List<RecentItem>> = repository.recentlyWatchedStore.items
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val savedServers: StateFlow<List<ServerConfig>> = serversStore.servers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var backStack: Pair<BrowseContent, Int>? = null
    private var preloaded = false

    init {
        viewModelScope.launch {
            repository.credentialsStore.credentials.collect { creds ->
                _state.update { it.copy(credentials = creds) }
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
            backStack = null
            preloaded = false
            selectSection(Section.LIVE)
        }
    }

    // ── Section navigation ────────────────────────────────────────────────

    fun selectSection(section: Section) {
        backStack = null
        _state.update { it.copy(section = section, canGoBack = false, searchQuery = "", epgInfo = null, restoreScrollIndex = -1) }
        preferencesStore.saveLastSection(section.name)
        when (section) {
            Section.LIVE      -> loadLiveCategories()
            Section.VOD       -> loadVodCategories()
            Section.SERIES    -> loadSeriesCategories()
            Section.FAVORITES -> loadFavorites()
        }
        preloadOtherCategories(section)
    }

    /** Preload categories for other sections in background so switching is instant. */
    private fun preloadOtherCategories(current: Section) {
        if (preloaded) return
        preloaded = true
        val creds = _state.value.credentials ?: return
        viewModelScope.launch {
            listOf(Section.LIVE, Section.VOD, Section.SERIES)
                .filter { it != current }
                .forEach { section ->
                    launch {
                        try {
                            when (section) {
                                Section.LIVE   -> repository.getLiveCategories(creds)
                                Section.VOD    -> repository.getVodCategories(creds)
                                Section.SERIES -> repository.getSeriesCategories(creds)
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

    private fun loadVodCategories() = load {
        BrowseContent.Categories(repository.getVodCategories(it).sortedByUS(), Section.VOD)
    }

    private fun loadSeriesCategories() = load {
        BrowseContent.Categories(repository.getSeriesCategories(it).sortedByUS(), Section.SERIES)
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            val favs = repository.favoritesStore.favorites.first()
            _state.update { it.copy(content = BrowseContent.Favorites(favs), isLoading = false) }
        }
    }

    // ── Content selection ─────────────────────────────────────────────────

    fun selectCategory(category: Category, mode: Section, scrollPosition: Int = 0) {
        backStack = _state.value.content to scrollPosition
        load(pushBack = true) { creds ->
            when (mode) {
                Section.LIVE   -> BrowseContent.LiveStreams(repository.getLiveStreams(creds, category.id))
                Section.VOD    -> BrowseContent.VodStreams(repository.getVodStreams(creds, category.id))
                Section.SERIES -> BrowseContent.SeriesList(repository.getSeries(creds, category.id))
                else           -> BrowseContent.Empty
            }
        }
    }

    fun selectSeries(series: Series) {
        load(pushBack = false) { creds ->
            val info = repository.getSeriesInfo(creds, series.id)
            BrowseContent.Episodes(info?.episodes ?: emptyMap())
        }
    }

    fun goBack() {
        val (savedContent, scrollPos) = backStack ?: run {
            selectSection(_state.value.section)
            return
        }
        backStack = null
        _state.update { it.copy(content = savedContent, canGoBack = false, searchQuery = "", restoreScrollIndex = scrollPos) }
    }

    fun clearScrollRestore() {
        if (_state.value.restoreScrollIndex >= 0) {
            _state.update { it.copy(restoreScrollIndex = -1) }
        }
    }

    // ── Retry ────────────────────────────────────────────────────────────

    fun retry() {
        _state.update { it.copy(error = null) }
        selectSection(_state.value.section)
    }

    // ── Play helpers ────────────────────────────────────────────────────

    private suspend fun buildRecentRefs(creds: Credentials, excludeId: String? = null): List<ChannelRef> =
        recentlyWatched.value
            .filter { it.id != excludeId }
            .take(8)
            .mapNotNull { item ->
                val id = item.id.toIntOrNull() ?: return@mapNotNull null
                val url = when (item.type) {
                    "live"    -> repository.getLiveUrl(creds, id)
                    "vod"     -> repository.getVodUrl(creds, id)
                    "episode" -> repository.getEpisodeUrl(creds, id, item.ext ?: "mp4")
                    else      -> return@mapNotNull null
                }
                ChannelRef(item.name, url, item.icon)
            }

    private suspend fun buildFavoriteRefs(creds: Credentials, excludeId: String? = null): List<ChannelRef> =
        favorites.value.values
            .filter { it.id != excludeId }
            .take(8)
            .mapNotNull { fav ->
                val id = fav.id.toIntOrNull() ?: return@mapNotNull null
                val url = when (fav.type) {
                    "live"    -> repository.getLiveUrl(creds, id)
                    "vod"     -> repository.getVodUrl(creds, id)
                    "episode" -> repository.getEpisodeUrl(creds, id, fav.ext ?: "mp4")
                    else      -> return@mapNotNull null
                }
                ChannelRef(fav.name, url, fav.icon)
            }

    // ── Play actions ──────────────────────────────────────────────────────

    fun onPlayLive(stream: LiveStream) {
        val creds = _state.value.credentials ?: return
        val url = repository.getLiveUrl(creds, stream.id)
        val recent = RecentItem(stream.id.toString(), stream.name, "live", stream.icon)
        _state.update { it.copy(nowPlaying = recent) }
        loadEpg(stream.id, stream.name)
        viewModelScope.launch {
            repository.recentlyWatchedStore.add(recent)
            val recentRefs = buildRecentRefs(creds, stream.id.toString())
            val favRefs = buildFavoriteRefs(creds, stream.id.toString())
            _playEvent.tryEmit(PlayEvent(url, stream.name, "live", recentRefs, favRefs))
        }
    }

    fun onPlayVod(stream: VodStream) {
        val creds = _state.value.credentials ?: return
        viewModelScope.launch {
            val url = repository.getVodUrl(creds, stream.id)
            val recent = RecentItem(stream.id.toString(), stream.name, "vod", stream.icon)
            _state.update { it.copy(nowPlaying = recent) }
            repository.recentlyWatchedStore.add(recent)
            val recentRefs = buildRecentRefs(creds, stream.id.toString())
            val favRefs = buildFavoriteRefs(creds, stream.id.toString())
            _playEvent.tryEmit(PlayEvent(url, stream.name, "vod", recentRefs, favRefs))
        }
    }

    fun onPlayEpisode(episode: Episode) {
        val creds = _state.value.credentials ?: return
        val title = episode.title ?: "Episode ${episode.episodeNum}"
        val url = repository.getEpisodeUrl(creds, episode.id, episode.containerExtension)
        val recent = RecentItem(episode.id.toString(), title, "episode", ext = episode.containerExtension)
        _state.update { it.copy(nowPlaying = recent) }

        // Find next episode in current season for auto-advance
        val episodes = (_state.value.content as? BrowseContent.Episodes)?.seasons
        var nextEp: Episode? = null
        if (episodes != null) {
            for ((_, seasonEps) in episodes.entries.sortedWith(compareBy { it.key.toIntOrNull() ?: Int.MAX_VALUE })) {
                val idx = seasonEps.indexOfFirst { it.id == episode.id }
                if (idx >= 0 && idx < seasonEps.size - 1) {
                    nextEp = seasonEps[idx + 1]
                    break
                }
            }
        }

        viewModelScope.launch {
            repository.recentlyWatchedStore.add(recent)
            val recentRefs = buildRecentRefs(creds, episode.id.toString())
            val favRefs = buildFavoriteRefs(creds, episode.id.toString())
            val nextUrl = nextEp?.let { repository.getEpisodeUrl(creds, it.id, it.containerExtension) } ?: ""
            val nextTitle = nextEp?.let { it.title ?: "Episode ${it.episodeNum}" } ?: ""
            _playEvent.tryEmit(PlayEvent(url, title, "episode", recentRefs, favRefs,
                nextUrl = nextUrl, nextTitle = nextTitle))
        }
    }

    fun onPlayRecent(item: RecentItem) {
        val creds = _state.value.credentials ?: return
        viewModelScope.launch {
            val url = when (item.type) {
                "live"    -> repository.getLiveUrl(creds, item.id.toIntOrNull() ?: return@launch)
                "vod"     -> repository.getVodUrl(creds, item.id.toIntOrNull() ?: return@launch)
                "episode" -> repository.getEpisodeUrl(creds, item.id.toIntOrNull() ?: return@launch, item.ext ?: "mp4")
                else      -> return@launch
            }
            _state.update { it.copy(nowPlaying = item) }
            repository.recentlyWatchedStore.add(item)
            val recentRefs = buildRecentRefs(creds, item.id)
            val favRefs = buildFavoriteRefs(creds, item.id)
            _playEvent.tryEmit(PlayEvent(url, item.name, item.type, recentRefs, favRefs))
        }
    }

    fun onPlayFavorite(fav: Favorite) {
        val creds = _state.value.credentials ?: return
        viewModelScope.launch {
            val url = when (fav.type) {
                "live"    -> repository.getLiveUrl(creds, fav.id.toIntOrNull() ?: return@launch)
                "vod"     -> repository.getVodUrl(creds, fav.id.toIntOrNull() ?: return@launch)
                "episode" -> repository.getEpisodeUrl(creds, fav.id.toIntOrNull() ?: return@launch, fav.ext ?: "mp4")
                else      -> return@launch
            }
            val recentRefs = buildRecentRefs(creds, fav.id)
            val favRefs = buildFavoriteRefs(creds, fav.id)
            _playEvent.tryEmit(PlayEvent(url, fav.name, fav.type, recentRefs, favRefs))
        }
    }

    // ── EPG ──────────────────────────────────────────────────────────────

    fun loadEpg(streamId: Int, channelName: String) {
        val creds = _state.value.credentials ?: return
        viewModelScope.launch {
            try {
                val epg = repository.getShortEpg(creds, streamId) ?: return@launch
                val listings = epg.listings ?: return@launch
                val now  = listings.getOrNull(0)
                val next = listings.getOrNull(1)
                _state.update {
                    it.copy(
                        epgInfo = EpgInfo(
                            channelName = channelName,
                            nowTitle  = now?.let  { l -> repository.decodeEpgTitle(l.title) } ?: "",
                            nowStart  = now?.start?.drop(11)?.take(5) ?: "",
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
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) { block(creds) }
                _state.update { it.copy(isLoading = false, content = content, canGoBack = pushBack) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    private fun List<Category>.sortedByUS() = sortedWith { a, b ->
        val aUS = a.name.contains("US|") || a.name.startsWith("US") || a.name.contains("|US")
        val bUS = b.name.contains("US|") || b.name.startsWith("US") || b.name.contains("|US")
        when {
            aUS && !bUS  -> -1
            !aUS && bUS  ->  1
            else         -> a.name.compareTo(b.name, ignoreCase = true)
        }
    }
}
