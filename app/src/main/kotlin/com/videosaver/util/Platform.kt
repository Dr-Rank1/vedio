package com.videosaver.util

enum class Platform(val id: String, val displayName: String) {
    TIKTOK("tiktok", "TikTok"),
    TWITTER("twitter", "Twitter / X"),
    INSTAGRAM("instagram", "Instagram"),
    UNKNOWN("unknown", "");

    companion object {
        fun fromId(id: String): Platform = entries.find { it.id == id } ?: UNKNOWN
    }
}
