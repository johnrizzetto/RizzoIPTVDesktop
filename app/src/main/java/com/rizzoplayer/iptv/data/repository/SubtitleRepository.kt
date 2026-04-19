package com.rizzoplayer.iptv.data.repository

import android.content.Context
import com.rizzoplayer.iptv.data.api.OpenSubtitlesService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SubtitleRepository(
    private val context: Context,
    private val apiKey: String
) {
    private val service = OpenSubtitlesService(context, apiKey)
    private val subsDir = File(context.cacheDir, "subs").also { it.mkdirs() }

    fun subtitleFile(imdbId: String, language: String, season: Int? = null, episode: Int? = null): File {
        val seasonStr = if (season != null && episode != null) "_s${season}e$episode" else ""
        return File(subsDir, "${imdbId}_${language}${seasonStr}.srt")
    }

    suspend fun getSubtitles(
        imdbId: String,
        languages: List<String> = listOf("en"),
        season: Int? = null,
        episode: Int? = null
    ): List<File> = withContext(Dispatchers.IO) {
        val cached = languages.mapNotNull { lang ->
            val file = subtitleFile(imdbId, lang, season, episode)
            if (file.exists()) file else null
        }
        if (cached.isNotEmpty()) return@withContext cached

        val results = service.search(imdbId, season, episode, languages)
        results.firstOrNull()?.let { result ->
            val lang = result.language
            val file = subtitleFile(imdbId, lang, season, episode)
            val bytes = service.download(result.fileId)
            if (bytes != null) {
                FileOutputStream(file).use { stream -> stream.write(bytes) }
                return@withContext listOf(file)
            }
        }
        emptyList()
    }
}