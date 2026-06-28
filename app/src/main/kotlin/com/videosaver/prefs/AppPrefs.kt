package com.videosaver.prefs

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.videosaver.DownloadOptions
import com.videosaver.ThemeManager

object AppPrefs {

    private const val APP = "videosaver"
    private const val SETTINGS = "settings"

    const val KEY_HISTORY = "download_history"
    const val KEY_ROOM_MIGRATED = "room_migrated"

    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_DOWNLOAD_QUALITY = "download_quality"
    const val KEY_TIKTOK_REMOVE_WATERMARK = "tiktok_remove_watermark"
    const val KEY_DOWNLOAD_PATH = "download_path"
    const val KEY_WIFI_ONLY = "wifi_only"
    const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted"
    const val KEY_CLIPBOARD_PROMPT = "clipboard_prompt_enabled"

    const val KEY_TIKTOK_ENDPOINT = "endpoint_tiktok"
    const val KEY_INSTAGRAM_ENDPOINT = "endpoint_instagram"

    const val ENDPOINT_TIKWM_POST = "tikwm_post"
    const val ENDPOINT_TIKWM_GET = "tikwm_get"
    const val ENDPOINT_SNAPSAVE = "snapsave"
    const val ENDPOINT_FASTDL = "fastdl"

    private fun appPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(APP, Context.MODE_PRIVATE)

    private fun settingsPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context): String =
        settingsPrefs(context).getString(KEY_THEME_MODE, ThemeManager.MODE_SYSTEM)
            ?: ThemeManager.MODE_SYSTEM

    fun saveThemeMode(context: Context, mode: String) {
        settingsPrefs(context).edit().putString(KEY_THEME_MODE, mode).apply()
    }

    fun getDownloadOptions(context: Context): DownloadOptions {
        val prefs = settingsPrefs(context)
        return DownloadOptions(
            hdQuality = prefs.getString(KEY_DOWNLOAD_QUALITY, "hd") != "sd",
            removeTikTokWatermark = prefs.getBoolean(KEY_TIKTOK_REMOVE_WATERMARK, true),
        )
    }

    fun getDownloadQuality(context: Context): String =
        settingsPrefs(context).getString(KEY_DOWNLOAD_QUALITY, "hd") ?: "hd"

    fun setDownloadQuality(context: Context, quality: String) {
        settingsPrefs(context).edit().putString(KEY_DOWNLOAD_QUALITY, quality).apply()
    }

    fun isTikTokWatermarkRemoved(context: Context): Boolean =
        settingsPrefs(context).getBoolean(KEY_TIKTOK_REMOVE_WATERMARK, true)

    fun setTikTokWatermarkRemoved(context: Context, removed: Boolean) {
        settingsPrefs(context).edit().putBoolean(KEY_TIKTOK_REMOVE_WATERMARK, removed).apply()
    }

    fun isWifiOnly(context: Context): Boolean =
        settingsPrefs(context).getBoolean(KEY_WIFI_ONLY, false)

    fun setWifiOnly(context: Context, enabled: Boolean) {
        settingsPrefs(context).edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    fun isDisclaimerAccepted(context: Context): Boolean =
        appPrefs(context).getBoolean(KEY_DISCLAIMER_ACCEPTED, false)

    fun setDisclaimerAccepted(context: Context) {
        appPrefs(context).edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, true).apply()
    }

    fun isClipboardPromptEnabled(context: Context): Boolean =
        settingsPrefs(context).getBoolean(KEY_CLIPBOARD_PROMPT, true)

    fun setClipboardPromptEnabled(context: Context, enabled: Boolean) {
        settingsPrefs(context).edit().putBoolean(KEY_CLIPBOARD_PROMPT, enabled).apply()
    }

    fun getDownloadTreeUri(context: Context): Uri? {
        val raw = settingsPrefs(context).getString(KEY_DOWNLOAD_PATH, null) ?: return null
        return Uri.parse(raw)
    }

    fun setDownloadTreeUri(context: Context, uri: Uri) {
        settingsPrefs(context).edit().putString(KEY_DOWNLOAD_PATH, uri.toString()).apply()
    }

    fun clearDownloadTreeUri(context: Context) {
        settingsPrefs(context).edit().remove(KEY_DOWNLOAD_PATH).apply()
    }

    fun getCachedEndpoint(context: Context, platformKey: String): String? =
        appPrefs(context).getString(platformKey, null)

    fun setCachedEndpoint(context: Context, platformKey: String, endpoint: String) {
        appPrefs(context).edit().putString(platformKey, endpoint).apply()
    }

    fun isRoomMigrated(context: Context): Boolean =
        appPrefs(context).getBoolean(KEY_ROOM_MIGRATED, false)

    fun setRoomMigrated(context: Context) {
        appPrefs(context).edit()
            .putBoolean(KEY_ROOM_MIGRATED, true)
            .remove(KEY_HISTORY)
            .apply()
    }

    fun getLegacyHistoryJson(context: Context): String =
        appPrefs(context).getString(KEY_HISTORY, "[]") ?: "[]"
}
