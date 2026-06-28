package com.videosaver.util

import java.net.URI

object UrlNormalizer {

    fun normalize(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return url
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            url = "https://$url"
        }
        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return url
            val path = uri.path ?: ""
            val query = uri.query?.let { "?$it" } ?: ""
            "https://$host$path$query"
        } catch (_: Exception) {
            url
        }
    }

    fun detectPlatform(url: String): Platform {
        val normalized = normalize(url).lowercase()
        return when {
            normalized.contains("tiktok.com") ||
                normalized.contains("vm.tiktok.com") ||
                normalized.contains("vt.tiktok.com") -> Platform.TIKTOK
            normalized.contains("twitter.com") ||
                normalized.contains("x.com") ||
                normalized.contains("t.co") -> Platform.TWITTER
            normalized.contains("instagram.com") -> Platform.INSTAGRAM
            else -> Platform.UNKNOWN
        }
    }
}
