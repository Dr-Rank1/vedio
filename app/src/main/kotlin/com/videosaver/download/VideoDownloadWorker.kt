package com.videosaver.download

import android.content.Context
import android.media.MediaScannerConnection
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.videosaver.DownloadFailureReason
import com.videosaver.DownloadHelper
import com.videosaver.DownloadResult
import com.videosaver.HistoryItem
import com.videosaver.HttpClientProvider
import com.videosaver.NotificationHelper
import com.videosaver.R
import com.videosaver.di.ServiceLocator
import com.videosaver.prefs.AppPrefs
import com.videosaver.storage.StorageHelper
import okhttp3.Request
import java.util.UUID

class VideoDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private var lastProgressReportMs = 0L

    override suspend fun doWork(): Result {
        val sourceUrl = inputData.getString(KEY_SOURCE_URL) ?: return failure(R.string.error_missing_url)
        val platform = inputData.getString(KEY_PLATFORM) ?: return failure(R.string.error_missing_platform)
        val filename = inputData.getString(KEY_FILENAME) ?: return failure(R.string.error_missing_filename)

        setProgress(workDataOf(KEY_PROGRESS to 5))

        when (val resolved = DownloadHelper.resolveVideoUrl(
            applicationContext,
            sourceUrl,
            AppPrefs.getDownloadOptions(applicationContext),
        )) {
            is DownloadResult.Failure -> return Result.failure(
                workDataOf(
                    KEY_ERROR_MESSAGE to resolved.message,
                    KEY_ERROR_REASON to resolved.reason.name,
                )
            )
            is DownloadResult.Success -> {
                setProgress(workDataOf(KEY_PROGRESS to 20, KEY_PROVIDER to resolved.provider))
                return downloadFile(
                    videoUrl = resolved.videoUrl,
                    sourceUrl = sourceUrl,
                    platform = platform,
                    filename = filename,
                )
            }
        }
    }

    private suspend fun downloadFile(
        videoUrl: String,
        sourceUrl: String,
        platform: String,
        filename: String,
    ): Result {
        val resolveResult = StorageHelper.resolveOutputTarget(applicationContext, filename)
        val target = resolveResult.target
        return try {
            val request = Request.Builder().url(videoUrl).build()
            val response = HttpClientProvider.client.newCall(request).execute()
            if (!response.isSuccessful) {
                StorageHelper.deleteTarget(applicationContext, target)
                return Result.failure(
                    workDataOf(
                        KEY_ERROR_MESSAGE to applicationContext.getString(
                            R.string.download_failed_http, response.code
                        ),
                        KEY_ERROR_REASON to DownloadFailureReason.NETWORK.name,
                    )
                )
            }
            val body = response.body ?: run {
                StorageHelper.deleteTarget(applicationContext, target)
                return failure(R.string.error_empty_response)
            }
            val total = body.contentLength()
            body.byteStream().use { input ->
                var downloaded = 0L
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                when (target) {
                    is StorageHelper.OutputTarget.FilePath -> {
                        target.file.outputStream().use { output ->
                            while (true) {
                                if (isStopped) {
                                    StorageHelper.deleteTarget(applicationContext, target)
                                    return Result.failure()
                                }
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                reportProgressThrottled(downloaded, total)
                            }
                        }
                    }
                    is StorageHelper.OutputTarget.DocumentUri -> {
                        applicationContext.contentResolver
                            .openOutputStream(target.uri)?.use { output ->
                                while (true) {
                                    if (isStopped) {
                                        StorageHelper.deleteTarget(applicationContext, target)
                                        return Result.failure()
                                    }
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    output.write(buffer, 0, read)
                                    downloaded += read
                                    reportProgressThrottled(downloaded, total)
                                }
                            } ?: run {
                            StorageHelper.deleteTarget(applicationContext, target)
                            return failure(R.string.error_cannot_write_folder)
                        }
                    }
                }
            }

            val localPath = when (target) {
                is StorageHelper.OutputTarget.FilePath -> {
                    MediaScannerConnection.scanFile(
                        applicationContext,
                        arrayOf(target.file.absolutePath),
                        arrayOf("video/mp4"),
                        null,
                    )
                    target.file.absolutePath
                }
                is StorageHelper.OutputTarget.DocumentUri -> target.uri.toString()
            }

            val item = HistoryItem(
                id = UUID.randomUUID().toString(),
                filename = filename,
                sourceUrl = sourceUrl,
                timestamp = System.currentTimeMillis(),
                localPath = localPath,
                platform = platform,
            )
            ServiceLocator.historyRepository(applicationContext).insert(item)
            NotificationHelper.showSuccess(applicationContext, filename, localPath)

            val output = workDataOf(
                KEY_PROGRESS to 100,
                KEY_LOCAL_PATH to localPath,
                KEY_FILENAME to filename,
                KEY_USED_FALLBACK to resolveResult.usedFallbackFolder,
            )
            Result.success(output)
        } catch (e: Exception) {
            StorageHelper.deleteTarget(applicationContext, target)
            Result.failure(
                workDataOf(
                    KEY_ERROR_MESSAGE to applicationContext.getString(R.string.download_failed_generic),
                    KEY_ERROR_REASON to DownloadFailureReason.NETWORK.name,
                )
            )
        }
    }

    private suspend fun reportProgressThrottled(downloaded: Long, total: Long) {
        val now = System.currentTimeMillis()
        if (now - lastProgressReportMs < 200 && total > 0) return
        lastProgressReportMs = now
        val progress = when {
            total > 0 -> (20 + (downloaded * 75 / total)).toInt().coerceIn(20, 95)
            else -> 50
        }
        setProgress(workDataOf(KEY_PROGRESS to progress))
    }

    private fun failure(messageResId: Int): Result =
        Result.failure(
            workDataOf(
                KEY_ERROR_MESSAGE to applicationContext.getString(messageResId),
                KEY_ERROR_REASON to DownloadFailureReason.NETWORK.name,
            )
        )

    companion object {
        const val WORK_NAME = "video_download"
        const val KEY_SOURCE_URL = "source_url"
        const val KEY_PLATFORM = "platform"
        const val KEY_FILENAME = "filename"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val KEY_ERROR_REASON = "error_reason"
        const val KEY_LOCAL_PATH = "local_path"
        const val KEY_PROVIDER = "provider"
        const val KEY_USED_FALLBACK = "used_fallback"
    }
}
