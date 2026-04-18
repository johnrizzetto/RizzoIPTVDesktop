package com.rizzoplayer.iptv.data.model

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.annotations.SerializedName

data class TorBoxAddResult(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("detail")  val detail: String? = null,
    @SerializedName("error")   val error: String? = null,
    @SerializedName("data")    val data: JsonElement? = null
) {
    val torrentId: Int? get() = when {
        data == null || data is JsonNull -> null
        data is JsonObject -> data.get("torrent_id")?.asInt ?: data.get("id")?.asInt
        else -> null
    }
    val hash: String? get() = when {
        data == null || data is JsonNull -> null
        data is JsonObject -> data.get("hash")?.asString
        data is JsonPrimitive -> data.asString
        else -> null
    }
    val message: String? get() = error ?: detail
}

data class TorBoxFile(
    @SerializedName("id")   val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("size") val size: Long
)

data class TorBoxTorrent(
    @SerializedName("id")                val torrentId: Int,
    @SerializedName("name")              val name: String,
    @SerializedName("hash")              val hash: String,
    @SerializedName("size")             val size: Long = 0,
    @SerializedName("progress")          val progress: Double = 0.0,
    @SerializedName("download_finished")  val downloadFinished: Boolean = false,
    @SerializedName("download_state")     val downloadState: String = "",
    @SerializedName("cached")            val cached: Boolean = false,
    @SerializedName("files")            val files: List<TorBoxFile> = emptyList()
) {
    val isCompleted: Boolean get() = downloadFinished || downloadState == "cached" || cached
    val percentDone: Double get() = progress.coerceIn(0.0, 1.0)
}
