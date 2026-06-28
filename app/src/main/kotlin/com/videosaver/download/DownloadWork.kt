package com.videosaver.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.videosaver.prefs.AppPrefs
import com.videosaver.download.VideoDownloadWorker.Companion.KEY_FILENAME
import com.videosaver.download.VideoDownloadWorker.Companion.KEY_PLATFORM
import com.videosaver.download.VideoDownloadWorker.Companion.KEY_SOURCE_URL
import java.util.concurrent.TimeUnit

object DownloadWork {

    const val DOWNLOAD_TAG = "video_download"

    fun enqueue(context: Context, sourceUrl: String, platform: String, filename: String) {
        val request = OneTimeWorkRequestBuilder<VideoDownloadWorker>()
            .setInputData(
                workDataOf(
                    KEY_SOURCE_URL to sourceUrl,
                    KEY_PLATFORM to platform,
                    KEY_FILENAME to filename,
                )
            )
            .setConstraints(buildConstraints(context))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(DOWNLOAD_TAG)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(DOWNLOAD_TAG)
    }

    private fun buildConstraints(context: Context): Constraints {
        val builder = Constraints.Builder()
            .setRequiredNetworkType(
                if (AppPrefs.isWifiOnly(context)) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
        return builder.build()
    }
}
