package com.rizzoplayer.iptv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rizzoplayer.iptv.data.api.IPTVApiService
import com.rizzoplayer.iptv.data.local.CredentialsStore
import com.rizzoplayer.iptv.data.local.DiskCache
import com.rizzoplayer.iptv.data.local.FavoritesStore
import com.rizzoplayer.iptv.data.local.RecentlyWatchedStore
import com.rizzoplayer.iptv.data.local.ServersStore
import com.rizzoplayer.iptv.data.model.PlayEvent
import com.rizzoplayer.iptv.data.repository.IPTVRepository
import com.rizzoplayer.iptv.data.repository.TmdbRepository
import com.rizzoplayer.iptv.data.api.TmdbApiService
import com.rizzoplayer.iptv.data.api.TorrentioService
import com.rizzoplayer.iptv.data.api.TorBoxApiService
import com.rizzoplayer.iptv.data.api.TorBoxSearchService
import com.rizzoplayer.iptv.data.model.Credentials
import com.rizzoplayer.iptv.data.repository.TorBoxRepository
import com.rizzoplayer.iptv.ui.player.PlayerActivity
import com.rizzoplayer.iptv.ui.screens.HomeScreen
import com.rizzoplayer.iptv.ui.screens.LoginScreen
import com.rizzoplayer.iptv.ui.theme.RizzoIPTVTheme
import com.rizzoplayer.iptv.ui.viewmodel.BrowseContent
import com.rizzoplayer.iptv.ui.viewmodel.LoginViewModel
import com.rizzoplayer.iptv.ui.viewmodel.MainViewModel
import com.rizzoplayer.iptv.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object DefaultCredentials {
    const val USERNAME = "87bcb5ed3f"
    const val PASSWORD = "c46e2b805d"
    val URLS = listOf(
        "http://line.trexgaminghub.xyz",
        "http://line.gaminghubott.xyz",
        "http://vpn.gaminghubott.xyz",
        "http://line.gaminghubpro.xyz",
        "http://vpn.gaminghubpro.xyz"
    )
    const val DEFAULT_URL = "http://line.trexgaminghub.xyz"
}

class MainActivity : ComponentActivity() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var pendingPlayEvent: PlayEvent? = null
    private var mainVm: MainViewModel? = null

    companion object {
        private const val REQUEST_PLAY = 100
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PLAY && resultCode == PlayerActivity.RESULT_PLAYBACK_ERROR) {
            pendingPlayEvent = null
            mainVm?.onPlaybackError()
        }
    }

    private suspend fun autoLoginIfNeeded(repository: IPTVRepository): Boolean {
        val existing = repository.credentialsStore.credentials.first()
        if (existing != null) return existing.isValid()
        repository.credentialsStore.save(
            Credentials(
                url = DefaultCredentials.DEFAULT_URL,
                username = DefaultCredentials.USERNAME,
                password = DefaultCredentials.PASSWORD
            )
        )
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as RizzoApp

        val repository = IPTVRepository(
            credentialsStore      = CredentialsStore(applicationContext),
            favoritesStore        = FavoritesStore(applicationContext),
            recentlyWatchedStore  = RecentlyWatchedStore(applicationContext),
            watchHistoryStore     = com.rizzoplayer.iptv.data.local.WatchHistoryStore(applicationContext),
            diskCache             = DiskCache(applicationContext),
            api                  = IPTVApiService(applicationContext)
        )
        val tmdbRepository = TmdbRepository(
            tmdb = TmdbApiService(applicationContext),
            torrentio = TorrentioService(applicationContext),
            diskCache = DiskCache(applicationContext, "tmdb_api")
        )
        val torBoxRepository = TorBoxRepository(
            torBox = TorBoxApiService(applicationContext),
            torrentio = TorrentioService(applicationContext),
            torBoxSearch = TorBoxSearchService(applicationContext)
        )
        val serversStore = ServersStore(applicationContext)
        val factory = ViewModelFactory(repository, tmdbRepository, torBoxRepository, serversStore, app.preferencesStore, app)

        setContent {
            RizzoIPTVTheme {
                var isLoggedIn by remember { mutableStateOf<Boolean?>(null) }

                LaunchedEffect(Unit) {
                    isLoggedIn = repository.credentialsStore.credentials.first() != null
                    if (!isLoggedIn!!) {
                        isLoggedIn = autoLoginIfNeeded(repository)
                    }
                }

                if (isLoggedIn == null) return@RizzoIPTVTheme

                if (isLoggedIn == false) {
                    val loginVm: LoginViewModel = viewModel(factory = factory)
                    LoginScreen(
                        viewModel = loginVm,
                        onLoginSuccess = { isLoggedIn = true }
                    )
                } else {
                    val mainVm: MainViewModel = viewModel(factory = factory)
                    this.mainVm = mainVm

                    LaunchedEffect(Unit) {
                        if (mainVm.state.value.content is BrowseContent.Empty) {
                            mainVm.selectSection(mainVm.restoreLastSection())
                        }
                    }

                    val credentials by mainVm.repository.credentialsStore.credentials.collectAsState(null)
                    var credentialsSeen by remember { mutableStateOf(false) }
                    LaunchedEffect(credentials) {
                        if (credentials != null) credentialsSeen = true
                        if (credentialsSeen && credentials == null) isLoggedIn = false
                    }

                    LaunchedEffect(Unit) {
                        mainVm.playEvent.collect { event ->
                            val positionStore = app.playbackPositionStore
                            val contentId = if (event.contentType.startsWith("tmdb_")) {
                                event.contentId
                            } else {
                                event.url.substringAfterLast("/").substringBefore(".")
                            }
                            val posKey = "${event.contentType}:$contentId"
                            val resumeMs = positionStore.getPosition(posKey)
                            val recentJson = if (event.recentChannels.isNotEmpty())
                                json.encodeToString(event.recentChannels) else ""
                            val favJson = if (event.favoriteChannels.isNotEmpty())
                                json.encodeToString(event.favoriteChannels) else ""
                            pendingPlayEvent = event
                            startActivityForResult(
                                Intent(this@MainActivity, PlayerActivity::class.java).apply {
                                    putExtra(PlayerActivity.EXTRA_URL, event.url)
                                    putExtra(PlayerActivity.EXTRA_TITLE, event.title)
                                    putExtra(PlayerActivity.EXTRA_CONTENT_TYPE, event.contentType)
                                    putExtra(PlayerActivity.EXTRA_CONTENT_ID, contentId)
                                    putExtra(PlayerActivity.EXTRA_RESUME_MS, resumeMs)
                                    putExtra(PlayerActivity.EXTRA_RECENT_CHANNELS, recentJson)
                                    putExtra(PlayerActivity.EXTRA_FAVORITE_CHANNELS, favJson)
                                    putExtra(PlayerActivity.EXTRA_NEXT_URL, event.nextUrl)
                                    putExtra(PlayerActivity.EXTRA_NEXT_TITLE, event.nextTitle)
                                },
                                REQUEST_PLAY
                            )
                        }
                    }

                    HomeScreen(viewModel = mainVm)
                }
            }
        }
    }
}