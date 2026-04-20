package com.rizzoplayer.iptv

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.request.CachePolicy
import coil.memory.MemoryCache
import com.rizzoplayer.iptv.data.api.NetworkClient
import com.rizzoplayer.iptv.data.local.PlaybackPositionStore
import com.rizzoplayer.iptv.data.local.PreferencesStore
import com.rizzoplayer.iptv.ui.player.PlayerPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RizzoApp : Application() {

    lateinit var playbackPositionStore: PlaybackPositionStore
        private set
    lateinit var preferencesStore: PreferencesStore
        private set

    override fun onCreate() {
        super.onCreate()

        playbackPositionStore = PlaybackPositionStore(this)
        preferencesStore = PreferencesStore(this)

        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.15)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(100L * 1024 * 1024)
                        .build()
                }
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .crossfade(200)
                .respectCacheHeaders(false)
                .build()
        )

        NetworkClient.prewarm(
            this,
            listOf(
                "api.themoviedb.org",
                "api.torbox.app",
                "torrentio.strem.fun",
                "image.tmdb.org"
            )
        )

        // Warm up ExoPlayer pool off-main before first playback
        CoroutineScope(Dispatchers.IO).launch {
            PlayerPool.warmUp(applicationContext)
        }
    }
}
