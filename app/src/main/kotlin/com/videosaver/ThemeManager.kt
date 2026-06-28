package com.videosaver

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.videosaver.prefs.AppPrefs

object ThemeManager {
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"
    const val MODE_SYSTEM = "system"

    fun applyTheme(mode: String) {
        when (mode) {
            MODE_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            MODE_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun getSavedMode(context: Context): String = AppPrefs.getThemeMode(context)

    fun saveMode(context: Context, mode: String) = AppPrefs.saveThemeMode(context, mode)
}
