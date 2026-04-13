package com.rizzoplayer.iptv

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
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
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("logo_cache"))
                        .maxSizeBytes(50L * 1024 * 1024)
                        .build()
                }
                .respectCacheHeaders(false)
                .crossfade(false)
                .build()
        )
    }
}
