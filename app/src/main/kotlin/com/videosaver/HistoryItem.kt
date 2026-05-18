package com.videosaver

data class HistoryItem(
    val filename: String,
    val sourceUrl: String,
    val timestamp: Long,
    val localPath: String,
    val platform: String = ""
)
