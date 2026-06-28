package com.videosaver.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val id: String,
    val filename: String,
    val sourceUrl: String,
    val localPath: String,
    val platform: String,
    val timestamp: Long,
)

fun HistoryEntity.toItem() = com.videosaver.HistoryItem(
    id = id,
    filename = filename,
    sourceUrl = sourceUrl,
    timestamp = timestamp,
    localPath = localPath,
    platform = platform,
)

fun com.videosaver.HistoryItem.toEntity() = HistoryEntity(
    id = id,
    filename = filename,
    sourceUrl = sourceUrl,
    localPath = localPath,
    platform = platform,
    timestamp = timestamp,
)
