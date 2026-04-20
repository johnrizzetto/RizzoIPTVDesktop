package com.rizzoplayer.iptv.data.api

import com.google.gson.annotations.SerializedName
import okhttp3.Request

class OpenSubtitlesService(
    context: android.content.Context,
    private val apiKey: String,
    baseUrl: String = "https://api.opensubtitles.com/api/v1"
) {
    private val client = NetworkClient.opensubtitles(context, apiKey)
    private val baseUrl = baseUrl.trimEnd('/')

    data class SubtitleSearchResult(
        val fileId: String,
        val fileName: String,
        val language: String,
        val downloadUrl: String,
        val hearingImpaired: Boolean,
        val hd: Boolean
    )

    data class SearchResponse(
        @SerializedName("data") val data: List<SubtitleResult>
    )

    data class SubtitleResult(
        @SerializedName("id") val id: String,
        @SerializedName("type") val type: String,
        @SerializedName("attributes") val attributes: SubtitleAttributes
    )

    data class SubtitleAttributes(
        @SerializedName("files") val files: List<SubtitleFile>
    )

    data class SubtitleFile(
        @SerializedName("file_id") val fileId: String,
        @SerializedName("filename") val filename: String,
        @SerializedName("cd_number") val cdNumber: Int,
        @SerializedName("release") val release: String?,
        @SerializedName("language") val language: String,
        @SerializedName("download_count") val downloadCount: Int?,
        @SerializedName("hearing_impaired") val hearingImpaired: Boolean?,
        @SerializedName("hd") val hd: Boolean?,
        @SerializedName("download_url") val downloadUrl: String?,
        @SerializedName("files") val files: List<FileLink>?
    )

    data class FileLink(
        @SerializedName("file_id") val fileId: String,
        @SerializedName("cd_number") val cdNumber: Int?,
        @SerializedName("download_url") val downloadUrl: String?
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

            val searchResp = com.google.gson.Gson().fromJson(body, SearchResponse::class.java)

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