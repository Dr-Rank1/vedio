package com.videosaver.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videosaver.HistoryItem
import com.videosaver.di.ServiceLocator
import com.videosaver.download.DownloadWork
import com.videosaver.storage.StorageHelper
import com.videosaver.util.FilenameUtils
import com.videosaver.util.PlatformDetector
import com.videosaver.util.UrlNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class HistoryStats(
    val totalCount: Int = 0,
    val totalBytes: Long = 0L,
    val todayCount: Int = 0,
    val tiktokCount: Int = 0,
    val twitterCount: Int = 0,
    val instagramCount: Int = 0,
)

enum class SortOrder {
    NEWEST, OLDEST, NAME_ASC, NAME_DESC, SIZE_DESC, SIZE_ASC,
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServiceLocator.historyRepository(application)
    private val appContext = application.applicationContext

    private val _allHistory = MutableStateFlow<List<HistoryItem>>(emptyList())
    val allHistory: StateFlow<List<HistoryItem>> = _allHistory.asStateFlow()

    private val _filter = MutableStateFlow("all")
    val filter: StateFlow<String> = _filter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    val filteredHistory: StateFlow<List<HistoryItem>> = combine(
        _allHistory, _filter, _searchQuery, _sortOrder,
    ) { items, filter, query, sort ->
        applyFilterAndSort(items, filter, query, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<HistoryStats> = _allHistory.map { items ->
        val today = Calendar.getInstance()
        var todayCount = 0
        var totalBytes = 0L
        for (item in items) {
            if (item.fileSizeBytes >= 0) totalBytes += item.fileSizeBytes
            val cal = Calendar.getInstance().apply { timeInMillis = item.timestamp }
            if (cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
            ) todayCount++
        }
        HistoryStats(
            totalCount = items.size,
            totalBytes = totalBytes,
            todayCount = todayCount,
            tiktokCount = items.count { it.platform == PlatformDetector.TIKTOK },
            twitterCount = items.count { it.platform == PlatformDetector.TWITTER },
            instagramCount = items.count { it.platform == PlatformDetector.INSTAGRAM },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryStats())

    val lastDeleted = MutableStateFlow<HistoryItem?>(null)

    private var undoJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeAll()
                .mapLatest { items ->
                    withContext(Dispatchers.IO) { repository.enrich(items) }
                }
                .collect { _allHistory.value = it }
        }
    }

    fun setFilter(value: String) {
        _filter.value = value
    }

    fun setSearchQuery(value: String) {
        _searchQuery.value = value
    }

    fun setSortOrder(value: SortOrder) {
        _sortOrder.value = value
    }

    fun deleteItem(item: HistoryItem) {
        undoJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(item)
            lastDeleted.value = item
            undoJob = viewModelScope.launch {
                delay(5_000)
                lastDeleted.value?.let { pending ->
                    lastDeleted.value = null
                    StorageHelper.delete(appContext, pending.localPath)
                }
            }
        }
    }

    fun undoDelete() {
        val item = lastDeleted.value ?: return
        undoJob?.cancel()
        lastDeleted.value = null
        viewModelScope.launch(Dispatchers.IO) {
            repository.insert(item)
        }
    }

    fun clearHistoryOnly(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistoryOnly()
            withContext(Dispatchers.Main) { onComplete?.invoke() }
        }
    }

    fun clearAllAndDeleteFiles(onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = repository.clearAllAndDeleteFiles()
            withContext(Dispatchers.Main) { onComplete?.invoke(deleted) }
        }
    }

    fun deleteMissingEntries(onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val removed = repository.deleteMissingEntries()
            withContext(Dispatchers.Main) { onComplete?.invoke(removed) }
        }
    }

    fun redownload(item: HistoryItem) {
        val normalized = UrlNormalizer.normalize(item.sourceUrl)
        val platform = item.platform.ifEmpty { PlatformDetector.detect(normalized) }
        val filename = FilenameUtils.forDownload(PlatformDetector.toPlatform(platform))
        DownloadWork.enqueue(appContext, normalized, platform, filename)
    }

    private fun applyFilterAndSort(
        items: List<HistoryItem>,
        filter: String,
        query: String,
        sort: SortOrder,
    ): List<HistoryItem> {
        var result = items
        if (filter != "all") result = result.filter { it.platform == filter }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            result = result.filter {
                it.filename.lowercase().contains(q) || it.sourceUrl.lowercase().contains(q)
            }
        }
        return when (sort) {
            SortOrder.NEWEST -> result.sortedByDescending { it.timestamp }
            SortOrder.OLDEST -> result.sortedBy { it.timestamp }
            SortOrder.NAME_ASC -> result.sortedBy { it.filename.lowercase() }
            SortOrder.NAME_DESC -> result.sortedByDescending { it.filename.lowercase() }
            SortOrder.SIZE_DESC -> result.sortedByDescending { it.fileSizeBytes }
            SortOrder.SIZE_ASC -> result.sortedBy {
                if (it.fileSizeBytes >= 0) it.fileSizeBytes else Long.MAX_VALUE
            }
        }
    }
}
