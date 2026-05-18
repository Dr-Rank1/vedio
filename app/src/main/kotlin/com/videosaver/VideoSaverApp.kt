package com.videosaver

import android.app.Application
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

class VideoSaverApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        ThemeManager.applyTheme(ThemeManager.getSavedMode(this))

        appScope.launch(Dispatchers.IO) {
            NotificationHelper.createChannel(this@VideoSaverApp)
        }

        appScope.launch(Dispatchers.IO) {
            preloadThumbnails()
        }
    }

    private fun preloadThumbnails() {
        try {
            val prefs = getSharedPreferences("videosaver", MODE_PRIVATE)
            val array = JSONArray(prefs.getString("download_history", "[]") ?: "[]")
            val count = minOf(6, array.length())
            for (i in array.length() - 1 downTo array.length() - count) {
                val path = array.getJSONObject(i).getString("localPath")
                val file = File(path)
                if (file.exists()) {
                    Glide.with(applicationContext).load(file).override(320, 180).preload()
                }
            }
        } catch (_: Exception) {}
    }
}
