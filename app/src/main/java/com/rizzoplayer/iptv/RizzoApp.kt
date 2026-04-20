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
                        .maxSizePercent(0.30)
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
    }
}
