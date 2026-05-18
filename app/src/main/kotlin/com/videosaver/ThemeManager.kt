package com.videosaver

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

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

    fun getSavedMode(context: Context): String {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("theme_mode", MODE_SYSTEM) ?: MODE_SYSTEM
    }

    fun saveMode(context: Context, mode: String) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString("theme_mode", mode).apply()
    }
}
