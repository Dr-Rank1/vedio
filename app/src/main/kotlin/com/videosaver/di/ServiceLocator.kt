package com.videosaver.di

import android.content.Context
import com.videosaver.data.HistoryRepository

object ServiceLocator {

    @Volatile
    private var historyRepository: HistoryRepository? = null

    fun historyRepository(context: Context): HistoryRepository =
        historyRepository ?: synchronized(this) {
            historyRepository ?: HistoryRepository(context.applicationContext).also {
                historyRepository = it
            }
        }
}
