package com.rizzoplayer.iptv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Immutable

@Stable
@Serializable
data class Credentials(
    val url: String,
    val username: String,
    val password: String
) {
    val baseUrl get() = url.trimEnd('/')
    fun isValid() = url.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

@Immutable
@Serializable
data class ServerConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val label: String,
    val url: String,
    val username: String,
    val password: String
) {
    fun toCredentials() = Credentials(url = url, username = username, password = password)
}

@Immutable
@Serializable
data class ChannelRef(
    val name: String,
    val url: String,
    val icon: String? = null
)

@Immutable
@Serializable
data class Category(
    @SerialName("category_id") val id: String,
    @SerialName("category_name") val name: String,
    @SerialName("parent_id") val parentId: Int = 0
)

@Immutable
@Serializable
data class LiveStream(
    @SerialName("stream_id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("stream_icon") val icon: String? = null
)

@Immutable
@Serializable
data class VodStream(
    @SerialName("stream_id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("stream_icon") val icon: String? = null,
    @SerialName("container_extension") val containerExtension: String = "mp4"
)

@Immutable
@Serializable
data class Series(
    @SerialName("series_id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("cover") val cover: String? = null
)

@Immutable
@Serializable
data class SeriesInfo(
    @SerialName("episodes") val episodes: Map<String, List<Episode>>? = null
)

@Immutable
@Serializable
data class Episode(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String? = null,
    @SerialName("container_extension") val containerExtension: String = "mp4",
    @SerialName("episode_num") val episodeNum: Int = 0
)

@Immutable
@Serializable
data class VodInfo(
    @SerialName("movie_data") val movieData: MovieData? = null
)

@Immutable
@Serializable
data class MovieData(
    @SerialName("stream_id") val streamId: Int = 0,
    @SerialName("container_extension") val containerExtension: String = "mp4"
)

@Immutable
@Serializable
data class EpgResponse(
    @SerialName("epg_listings") val listings: List<EpgListing>? = null
)

@Immutable
@Serializable
data class EpgListing(
    val title: String = "",
    val start: String = "",
    val end: String = ""
)

@Immutable
@Serializable
data class Favorite(
    val id: String,
    val name: String,
    val type: String,
    val ext: String? = null,
    val icon: String? = null,
    val addedAt: String = ""
)

@Immutable
@Serializable
data class RecentItem(
    val id: String,
    val name: String,
    val type: String,
    val icon: String? = null,
    val ext: String? = null,
    val watchedAt: Long = System.currentTimeMillis(),
    val watchedMs: Long = 0,
    val durationMs: Long = 0
)

@Immutable
@Serializable
data class PlayEvent(
    val url: String,
    val title: String,
    val contentType: String = "live",
    val recentChannels: List<ChannelRef> = emptyList(),
    val favoriteChannels: List<ChannelRef> = emptyList(),
    val resumeMs: Long = 0,
    val nextUrl: String = "",
    val nextTitle: String = "",
    val contentId: String = ""
)

@Immutable
@Serializable
sealed class WatchHistoryItem {
    abstract val id: String // movieId or seriesId
    abstract val title: String
    abstract val posterPath: String?
    abstract val watchedAt: Long
    abstract val isCompleted: Boolean

    @Immutable
    @Serializable
    @SerialName("movie")
    data class Movie(
        override val id: String,
        override val title: String,
        override val posterPath: String? = null,
        override val watchedAt: Long = System.currentTimeMillis(),
        override val isCompleted: Boolean = false,
        val watchedMs: Long = 0,
        val durationMs: Long = 0
    ) : WatchHistoryItem()

    @Immutable
    @Serializable
    @SerialName("series")
    data class Series(
        override val id: String,
        override val title: String,
        override val posterPath: String? = null,
        override val watchedAt: Long = System.currentTimeMillis(),
        override val isCompleted: Boolean = false, // True if series is finished
        val seasonNumber: Int = 1,
        val episodeNumber: Int = 1,
        val episodeWatchedMs: Long = 0,
        val episodeDurationMs: Long = 0,
        val isEpisodeCompleted: Boolean = false
    ) : WatchHistoryItem()
}