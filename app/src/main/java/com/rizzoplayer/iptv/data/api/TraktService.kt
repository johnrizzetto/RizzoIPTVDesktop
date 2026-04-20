package com.rizzoplayer.iptv.data.api

import android.content.Context
import com.google.gson.annotations.SerializedName
import okhttp3.Request
import com.rizzoplayer.iptv.BuildConfig

class TraktService(
    context: Context,
    baseUrl: String = "https://api.trakt.tv"
) {
    private val client = NetworkClient.base(context)

    private val baseUrl = baseUrl.trimEnd('/')

    private var accessToken: String? = null
    private var refreshToken: String? = null

    data class DeviceCodeResponse(
        @SerializedName("device_code") val deviceCode: String,
        @SerializedName("user_code") val userCode: String,
        @SerializedName("verification_url") val verificationUrl: String,
        @SerializedName("expires_in") val expiresIn: Int,
        @SerializedName("interval") val interval: Int
    )

    data class TokenResponse(
        @SerializedName("access_token") val accessToken: String?,
        @SerializedName("refresh_token") val refreshToken: String?,
        @SerializedName("expires_in") val expiresIn: Int?,
        @SerializedName("created_at") val createdAt: Long?
    )

    data class ScrobbleResponse(
        @SerializedName("id") val id: Long,
        @SerializedName("action") val action: String,
        @SerializedName("progress") val progress: Float,
        @SerializedName("movie") val movie: TraktMovie?,
        @SerializedName("episode") val episode: TraktEpisode?
    )

    data class TraktMovie(
        @SerializedName("title") val title: String,
        @SerializedName("year") val year: Int,
        @SerializedName("ids") val ids: TraktIds
    )

    data class TraktEpisode(
        @SerializedName("title") val title: String,
        @SerializedName("season") val season: Int,
        @SerializedName("number") val number: Int,
        @SerializedName("ids") val ids: TraktIds
    )

    data class TraktIds(
        @SerializedName("trakt") val trakt: Long,
        @SerializedName("imdb") val imdb: String?,
        @SerializedName("tmdb") val tmdb: Int?
    )

    suspend fun getDeviceCode(): DeviceCodeResponse? {
        return try {
            val req = Request.Builder()
                .url("$baseUrl/oauth/device/code")
                .post("{\"client_id\": \"${BuildConfig.TRAKT_CLIENT_ID}\"}".toRequestBody())
                .header("Content-Type", "application/json")
                .build()
            val resp = client.newCall(req).execute()
            parseDeviceCode(resp.body?.string() ?: return null)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun pollForToken(deviceCode: String, expiresIn: Int, interval: Int): TokenResponse? {
        val deadline = System.currentTimeMillis() + expiresIn * 1000L
        while (System.currentTimeMillis() < deadline) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl/oauth/device/token")
                    .post("{\"client_id\": \"${BuildConfig.TRAKT_CLIENT_ID}\", \"code\": \"$deviceCode\", \"code_verifier\": \"\"}".toRequestBody())
                    .header("Content-Type", "application/json")
                    .build()
                val resp = client.newCall(req).execute()
                val bodyStr = resp.body?.string() ?: return null
                val tokenResp = parseToken(bodyStr)
                if (tokenResp?.accessToken != null) {
                    accessToken = tokenResp.accessToken
                    refreshToken = tokenResp.refreshToken
                    return tokenResp
                }
            } catch (_: Exception) {}
            Thread.sleep(interval * 1000L)
        }
        return null
    }

    suspend fun scrobble(
        type: String, // "movie" or "episode"
        imdbId: String,
        progress: Float,
        action: String = "pause" // "start", "pause", "scrobble"
    ): ScrobbleResponse? {
        val token = accessToken ?: return null
        val progressFloat = progress.coerceIn(0f, 100f)
        val jsonBody = when (type) {
            "movie" -> """{"movie": {"ids": {"imdb": "$imdbId"}}, "progress": $progressFloat, "action": "$action"}"""
            "episode" -> """{"episode": {"ids": {"imdb": "$imdbId"}}, "progress": $progressFloat, "action": "$action"}"""
            else -> return null
        }
        return try {
            val req = Request.Builder()
                .url("$baseUrl/scrobble/$type")
                .post(jsonBody.toRequestBody())
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("trakt-api-version", "2")
                .header("trakt-api-key", BuildConfig.TRAKT_CLIENT_ID)
                .build()
            val resp = client.newCall(req).execute()
            parseScrobble(resp.body?.string() ?: return null)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getHistory(): List<HistoryItem>? {
        val token = accessToken ?: return null
        return try {
            val req = Request.Builder()
                .url("$baseUrl/users/me/history?limit=100")
                .get()
                .header("Authorization", "Bearer $token")
                .header("trakt-api-version", "2")
                .header("trakt-api-key", BuildConfig.TRAKT_CLIENT_ID)
                .build()
            val resp = client.newCall(req).execute()
            parseHistory(resp.body?.string() ?: return null)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun refreshAccessToken(): Boolean {
        val refresh = refreshToken ?: return false
        return try {
            val req = Request.Builder()
                .url("$baseUrl/oauth/token")
                .post("""{"client_id": "${BuildConfig.TRAKT_CLIENT_ID}", "refresh_token": "$refresh", "grant_type": "refresh_token"}""".toRequestBody())
                .header("Content-Type", "application/json")
                .build()
            val resp = client.newCall(req).execute()
            val bodyStr = resp.body?.string() ?: return false
            val tokenResp = parseToken(bodyStr)
            if (tokenResp?.accessToken != null) {
                accessToken = tokenResp.accessToken
                refreshToken = tokenResp.refreshToken
                return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun isAuthenticated() = accessToken != null

    private fun parseDeviceCode(json: String) = try {
        com.google.gson.Gson().fromJson(json, DeviceCodeResponse::class.java)
    } catch (_: Exception) { null }

    private fun parseToken(json: String) = try {
        com.google.gson.Gson().fromJson(json, TokenResponse::class.java)
    } catch (_: Exception) { null }

    private fun parseScrobble(json: String) = try {
        com.google.gson.Gson().fromJson(json, ScrobbleResponse::class.java)
    } catch (_: Exception) { null }

    private fun parseHistory(json: String) = try {
        val type = object : com.google.gson.reflect.TypeToken<List<HistoryItem>>() {}.type
        com.google.gson.Gson().fromJson(json, type)
    } catch (_: Exception) { null }

    data class HistoryItem(
        @SerializedName("id") val id: Long,
        @SerializedName("watched_at") val watchedAt: String?,
        @SerializedName("movie") val movie: TraktMovie?,
        @SerializedName("episode") val episode: TraktEpisode?
    )

    private fun String.toRequestBody(): okhttp3.RequestBody =
        okhttp3.RequestBody.create(null, this)
}

