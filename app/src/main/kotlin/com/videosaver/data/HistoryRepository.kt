package com.videosaver.data

import android.content.Context
import com.videosaver.HistoryItem
import com.videosaver.prefs.AppPrefs
import com.videosaver.storage.StorageHelper
import com.videosaver.util.UrlNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.UUID

class HistoryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.get(appContext).historyDao()

    suspend fun ensureMigrated() = withContext(Dispatchers.IO) {
        if (AppPrefs.isRoomMigrated(appContext)) return@withContext
        val array = JSONArray(AppPrefs.getLegacyHistoryJson(appContext))
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val sourceUrl = obj.getString("sourceUrl")
            val platform = obj.optString("platform", "").ifEmpty {
                UrlNormalizer.detectPlatform(sourceUrl).id
            }
            dao.insert(
                HistoryEntity(
                    id = UUID.randomUUID().toString(),
                    filename = obj.getString("filename"),
                    sourceUrl = sourceUrl,
                    timestamp = obj.getLong("timestamp"),
                    localPath = obj.getString("localPath"),
                    platform = platform,
                )
            )
        }
        AppPrefs.setRoomMigrated(appContext)
    }

    fun observeAll(): Flow<List<HistoryItem>> =
        dao.observeAll().map { entities -> entities.map { it.toItem() } }

    suspend fun getAll(): List<HistoryItem> = withContext(Dispatchers.IO) {
        ensureMigrated()
        enrich(dao.getAll().map { it.toItem() })
    }

    suspend fun insert(item: HistoryItem) = withContext(Dispatchers.IO) {
        dao.insert(item.toEntity())
    }

    suspend fun delete(item: HistoryItem) = withContext(Dispatchers.IO) {
        dao.deleteById(item.id)
    }

    suspend fun clearHistoryOnly() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }

    suspend fun clearAllAndDeleteFiles(): Int = withContext(Dispatchers.IO) {
        val items = dao.getAll()
        var deleted = 0
        for (entity in items) {
            if (StorageHelper.delete(appContext, entity.localPath)) deleted++
        }
        dao.clearAll()
        deleted
    }

    suspend fun deleteMissingEntries(): Int = withContext(Dispatchers.IO) {
        val items = dao.getAll()
        var removed = 0
        for (entity in items) {
            if (!StorageHelper.exists(appContext, entity.localPath)) {
                dao.deleteById(entity.id)
                removed++
            }
        }
        removed
    }

    suspend fun totalStorageBytes(): Long = withContext(Dispatchers.IO) {
        ensureMigrated()
        dao.getAll().sumOf { StorageHelper.fileSize(appContext, it.localPath) }
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        ensureMigrated()
        dao.count()
    }

    suspend fun enrich(items: List<HistoryItem>): List<HistoryItem> = withContext(Dispatchers.IO) {
        items.map { item ->
            item.copy(
                fileSizeBytes = StorageHelper.fileSize(appContext, item.localPath),
                fileExists = StorageHelper.exists(appContext, item.localPath),
            )
        }
    }
}
