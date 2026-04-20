package com.rizzoplayer.iptv.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

class OpenSubtitlesService(
    context: android.content.Context,
    private val apiKey: String,
    baseUrl: String = "https://api.opensubtitles.com/api/v1"
) {
    private val client = NetworkClient.opensubtitles(context, apiKey)
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val baseUrl = baseUrl.trimEnd('/')

    data class SubtitleSearchResult(
        val fileId: String,
        val fileName: String,
        val language: String,
        val downloadUrl: String,
        val hearingImpaired: Boolean,
        val hd: Boolean
    )

    @Serializable
    data class SearchResponse(
        val data: List<SubtitleResult> = emptyList()
    )

    @Serializable
    data class SubtitleResult(
        val id: String,
        val type: String,
        val attributes: SubtitleAttributes
    )

    @Serializable
    data class SubtitleAttributes(
        val files: List<SubtitleFile> = emptyList()
    )

    @Serializable
    data class SubtitleFile(
        @SerialName("file_id") val fileId: String,
        val filename: String,
        @SerialName("cd_number") val cdNumber: Int,
        val release: String? = null,
        val language: String,
        @SerialName("download_count") val downloadCount: Int? = null,
        @SerialName("hearing_impaired") val hearingImpaired: Boolean? = null,
        val hd: Boolean? = null,
        @SerialName("download_url") val downloadUrl: String? = null,
        val files: List<FileLink>? = null
    )

    @Serializable
    data class FileLink(
        @SerialName("file_id") val fileId: String,
        @SerialName("cd_number") val cdNumber: Int? = null,
        @SerialName("download_url") val downloadUrl: String? = null
    )

    suspend fun search(
        imdbId: String,
        season: Int? = null,
        episode: Int? = null,
        languages: List<String> = listOf("en")
    ): List<SubtitleSearchResult> {
        val episodeStr = if (episode != null && season != null) {
            "&episode_number=$episode&season_number=$season"
        } else ""

        val langFilter = languages.joinToString(",") { it }

        val url = "$baseUrl/subtitles?imdb_id=$imdbId$episodeStr&languages=$langFilter"

        return try {
            val req = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return emptyList()

            val searchResp = json.decodeFromString<SearchResponse>(body)

            searchResp.data
                .filter { it.attributes.files.isNotEmpty() }
                .flatMap { result ->
                    result.attributes.files.firstOrNull()?.let { file ->
                        val downloadUrl = file.downloadUrl
                            ?: file.files?.firstOrNull()?.downloadUrl

                        if (downloadUrl != null) {
                            listOf(
                                SubtitleSearchResult(
                                    fileId = file.fileId,
                                    fileName = file.filename,
                                    language = file.language,
                                    downloadUrl = downloadUrl,
                                    hearingImpaired = file.hearingImpaired ?: false,
                                    hd = file.hd ?: false
                                )
                            )
                        } else {
                            emptyList()
                        }
                    } ?: emptyList()
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun download(fileId: String): ByteArray? {
        return try {
            val req = Request.Builder()
                .url("$baseUrl/download?file_id=$fileId")
                .get()
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return null
            resp.body?.bytes()
        } catch (e: Exception) {
            null
        }
    }
}