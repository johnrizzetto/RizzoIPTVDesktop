package com.rizzoplayer.iptv.data.model

import com.google.gson.annotations.SerializedName

data class TorBoxAddResult(
    @SerializedName("success")     val success: Boolean = false,
    @SerializedName("torrent_id")   val torrentId: Int? = null,
    @SerializedName("message")      val message: String? = null
)

data class TorBoxFile(
    @SerializedName("id")     val id: Int,
    @SerializedName("name")   val name: String,
    @SerializedName("size")   val size: Long,
    @SerializedName("progress") val progress: Int = 0
)

data class TorBoxTorrent(
    @SerializedName("torrent_id")      val torrentId: Int,
    @SerializedName("name")            val name: String,
    @SerializedName("hash")             val hash: String,
    @SerializedName("bytes_total")     val bytesTotal: Long = 0,
    @SerializedName("bytes_done")      val bytesDone: Long = 0,
    @SerializedName("download_present") val downloadPresent: Double = 0.0,
    @SerializedName("status")           val status: String = "",
    @SerializedName("files")            val files: List<TorBoxFile> = emptyList()
) {
    val isCompleted: Boolean get() = status == "completed" || status == "cached" || status == "seeding"
    val percentDone: Double get() = if (bytesTotal > 0) bytesDone.toDouble() / bytesTotal else downloadPresent
}