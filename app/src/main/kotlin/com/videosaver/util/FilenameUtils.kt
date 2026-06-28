package com.videosaver.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FilenameUtils {

    fun forDownload(platform: Platform): String {
        val prefix = when (platform) {
            Platform.TIKTOK -> "TikTok"
            Platform.TWITTER -> "Twitter"
            Platform.INSTAGRAM -> "Instagram"
            Platform.UNKNOWN -> "Video"
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        return "${prefix}_$stamp.mp4"
    }
}
