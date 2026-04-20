package com.rizzoplayer.iptv.data.api

import android.content.Context
import com.rizzoplayer.iptv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.Request

class TraktService(
    context: Context,
    baseUrl: String = "https://api.trakt.tv"
) {
    private val client = NetworkClient.base(context)

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val baseUrl = baseUrl.trimEnd('/')

    private var accessToken: String? = null
    private var refreshToken: String? = null

    @Serializable
    data class DeviceCodeResponse(
        @SerialName("device_code") val deviceCode: String,
        @SerialName("user_code") val userCode: String,
        @SerialName("verification_url") val verificationUrl: String,
        @SerialName("expires_in") val expiresIn: Int,
        @SerialName("interval") val interval: Int
    )

    @Serializable
    data class TokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Int? = null,
        @SerialName("created_at") val createdAt: Long? = null
    )

    @Serializable
    data class ScrobbleResponse(
        val id: Long,
        val action: String,
        val progress: Float,
        val movie: TraktMovie? = null,
        val episode: TraktEpisode? = null
    )

    @Serializable
    data class TraktMovie(
        val title: String,
        val year: Int,
        val ids: TraktIds
    )

    @Serializable
    data class TraktEpisode(
        val title: String,
        val season: Int,
        val number: Int,
        val ids: TraktIds
    )

    @Serializable
    data class TraktIds(
        val trakt: Long,
        val imdb: String? = null,
        val tmdb: Int? = null
    )

    @Serializable
    data class HistoryItem(
        val id: Long,
        @SerialName("watched_at") val watchedAt: String? = null,
        val movie: TraktMovie? = null,
        val episode: TraktEpisode? = null
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
        type: String,
        imdbId: String,
        progress: Float,
        action: String = "pause"
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
        this.json.decodeFromString<DeviceCodeResponse>(json)
    } catch (_: Exception) { null }

    private fun parseToken(json: String) = try {
        this.json.decodeFromString<TokenResponse>(json)
    } catch (_: Exception) { null }

    private fun parseScrobble(json: String) = try {
        this.json.decodeFromString<ScrobbleResponse>(json)
    } catch (_: Exception) { null }

    private fun parseHistory(json: String) = try {
        this.json.decodeFromString<List<HistoryItem>>(json)
    } catch (_: Exception) { null }

    private fun String.toRequestBody(): okhttp3.RequestBody =
        okhttp3.RequestBody.create(null, this)
}