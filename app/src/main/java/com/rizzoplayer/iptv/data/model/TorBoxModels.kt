package com.rizzoplayer.iptv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class TorBoxAddResult(
    val success: Boolean = false,
    val detail: String? = null,
    val error: String? = null,
    val data: JsonElement? = null
) {
    val torrentId: Int? get() {
        val d = data ?: return null
        if (d is JsonObject) {
            val tid = d["torrent_id"]
            if (tid is JsonPrimitive && tid.isString) return tid.content.toIntOrNull()
            val i = d["id"]
            if (i is JsonPrimitive && i.isString) return i.content.toIntOrNull()
        }
        return null
    }
    val hash: String? get() {
        val d = data ?: return null
        return when (d) {
            is JsonObject -> {
                val h = d["hash"]
                if (h is JsonPrimitive) h.content else null
            }
            is JsonPrimitive -> d.content
            else -> null
        }
    }
    val message: String? get() = error ?: detail
}

@Serializable
data class TorBoxFile(
    val id: Int,
    val name: String,
    val size: Long
)

@Serializable
data class TorBoxTorrent(
    @SerialName("id") val torrentId: Int,
    @SerialName("name") val name: String,
    val hash: String,
    val size: Long = 0,
    val progress: Double = 0.0,
    @SerialName("download_finished") val downloadFinished: Boolean = false,
    @SerialName("download_state") val downloadState: String = "",
    val cached: Boolean = false,
    val files: List<TorBoxFile> = emptyList()
) {
    val isCompleted: Boolean get() = downloadFinished || downloadState == "cached" || cached
    val percentDone: Double get() = progress.coerceIn(0.0, 1.0)
}