package com.rizzoplayer.iptv.data.model

import com.google.gson.annotations.SerializedName

data class Credentials(
    val url: String,
    val username: String,
    val password: String
) {
    val baseUrl get() = url.trimEnd('/')
    fun isValid() = url.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

data class ServerConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val label: String,
    val url: String,
    val username: String,
    val password: String
) {
    fun toCredentials() = Credentials(url = url, username = username, password = password)
}

data class ChannelRef(
    val name: String,
    val url: String,
    val icon: String? = null
)

data class Category(
    @SerializedName("category_id") val id: String,
    @SerializedName("category_name") val name: String,
    @SerializedName("parent_id") val parentId: Int = 0
)

data class LiveStream(
    @SerializedName("stream_id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("stream_icon") val icon: String? = null
)

data class VodStream(
    @SerializedName("stream_id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("stream_icon") val icon: String? = null,
    @SerializedName("container_extension") val containerExtension: String = "mp4"
)

data class Series(
    @SerializedName("series_id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("cover") val cover: String? = null
)

data class SeriesInfo(
    @SerializedName("episodes") val episodes: Map<String, List<Episode>>? = null
)

data class Episode(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String? = null,
    @SerializedName("container_extension") val containerExtension: String = "mp4",
    @SerializedName("episode_num") val episodeNum: Int = 0
)

data class VodInfo(
    @SerializedName("movie_data") val movieData: MovieData? = null
)

data class MovieData(
    @SerializedName("stream_id") val streamId: Int = 0,
    @SerializedName("container_extension") val containerExtension: String = "mp4"
)

data class EpgResponse(
    @SerializedName("epg_listings") val listings: List<EpgListing>? = null
)

data class EpgListing(
    @SerializedName("title") val title: String = "",
    @SerializedName("start") val start: String = "",
    @SerializedName("end") val end: String = ""
)

data class Favorite(
    val id: String,
    val name: String,
    val type: String,   // "live", "vod", "series", "episode"
    val ext: String? = null,
    val icon: String? = null,
    val addedAt: String = ""
)

data class RecentItem(
    val id: String,
    val name: String,
    val type: String,   // "live", "vod", "episode"
    val icon: String? = null,
    val ext: String? = null,
    val watchedAt: Long = System.currentTimeMillis(),
    val watchedMs: Long = 0,   // playback resume position in milliseconds
    val durationMs: Long = 0   // total duration for progress display
)

data class PlayEvent(
    val url: String,
    val title: String,
    val contentType: String = "live",   // "live", "vod", "episode"
    val recentChannels: List<ChannelRef> = emptyList(),
    val favoriteChannels: List<ChannelRef> = emptyList(),
    val resumeMs: Long = 0,
    val nextUrl: String = "",           // next episode URL for auto-advance
    val nextTitle: String = ""          // next episode title
)
