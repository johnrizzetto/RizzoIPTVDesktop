package com.rizzoplayer.iptv.data.api

import android.content.Context
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.File
import java.util.concurrent.TimeUnit

object NetworkClient {
    private const val CACHE_SIZE = 100L * 1024 * 1024 // 100MB shared cache

    @Volatile
    private var _baseClient: OkHttpClient? = null

    @Volatile
    private var _iptvClient: OkHttpClient? = null

    @Volatile
    private var _opensubtitlesClient: OkHttpClient? = null

    private var _cacheDir: File? = null

    private fun init(context: Context) {
        _cacheDir = context.cacheDir
    }

    private fun baseClient(): OkHttpClient {
        _baseClient?.let { return it }
        val cacheDir = _cacheDir ?: throw IllegalStateException(
            "NetworkClient not initialized. Call base(context) or iptx(context) first."
        )
        val client = OkHttpClient.Builder().apply {
            cache(Cache(File(cacheDir, "okhttp_shared_cache"), CACHE_SIZE))
            connectTimeout(15, TimeUnit.SECONDS)
            readTimeout(20, TimeUnit.SECONDS)
            protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            connectionPool(ConnectionPool(16, 2, TimeUnit.MINUTES))
            dispatcher(Dispatcher().apply {
                maxRequests = 48
                maxRequestsPerHost = 16
            })
        }.build()

        prewarmIfSupported(client)
        _baseClient = client
        return client
    }

    fun base(context: Context): OkHttpClient {
        init(context)
        return baseClient()
    }

    fun iptx(context: Context): OkHttpClient {
        init(context)
        _iptvClient?.let { return it }
        baseClient()
        val client = baseClient().newBuilder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    )
                    .build()
                chain.proceed(req)
            })
            .build()
        _iptvClient = client
        return client
    }

    fun opensubtitles(context: Context, apiKey: String): OkHttpClient {
        init(context)
        _opensubtitlesClient?.let { return it }
        baseClient()
        val client = baseClient().newBuilder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Api-Key", apiKey)
                    .header("Content-Type", "application/json")
                    .build()
                chain.proceed(req)
            })
            .build()
        _opensubtitlesClient = client
        return client
    }

    private fun prewarmIfSupported(client: OkHttpClient) {
        try {
            val connectionPoolField = client.javaClass.getDeclaredField("connectionPool")
            connectionPoolField.isAccessible = true
            val connectionPool = connectionPoolField.get(client)
            val prewarmMethod = connectionPool.javaClass.getMethod("prewarm")
            prewarmMethod.invoke(connectionPool)
        } catch (_: Exception) {
            // prewarm not available — silently skip
        }
    }
}
