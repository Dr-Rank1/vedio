package com.videosaver

import android.app.Application
import android.media.MediaScannerConnection
import android.net.Uri
import com.bumptech.glide.Glide
import com.videosaver.di.ServiceLocator
import com.videosaver.storage.StorageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class VideoSaverApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        ThemeManager.applyTheme(ThemeManager.getSavedMode(this))

        NotificationHelper.createChannel(this)

        appScope.launch(Dispatchers.IO) {
            ServiceLocator.historyRepository(this@VideoSaverApp).ensureMigrated()
            preloadThumbnails()
        }
    }

    private suspend fun preloadThumbnails() {
        try {
            val items = ServiceLocator.historyRepository(this).getAll().take(6)
            for (item in items) {
                if (!StorageHelper.exists(this, item.localPath)) continue
                val loadTarget: Any = if (item.localPath.startsWith("content://")) {
                    Uri.parse(item.localPath)
                } else {
                    File(item.localPath)
                }
                Glide.with(applicationContext).load(loadTarget).override(320, 180).preload()
            }
        } catch (_: Exception) {}
    }
}
