package com.videosaver

import java.util.UUID

data class HistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val filename: String,
    val sourceUrl: String,
    val timestamp: Long,
    val localPath: String,
    val platform: String = "",
    val fileSizeBytes: Long = -1L,
    val fileExists: Boolean = true,
)
