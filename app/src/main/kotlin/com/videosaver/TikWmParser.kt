package com.videosaver

import org.json.JSONObject

internal object TikWmParser {

    fun parse(body: String, options: DownloadOptions): String? {
        return try {
            val data = JSONObject(body).optJSONObject("data") ?: return null
            val play = data.optString("play").takeIf { it.startsWith("https://") }
            val wmPlay = data.optString("wmplay").takeIf { it.startsWith("https://") }
            if (options.removeTikTokWatermark) play ?: wmPlay else wmPlay ?: play
        } catch (_: Exception) {
            null
        }
    }
}
