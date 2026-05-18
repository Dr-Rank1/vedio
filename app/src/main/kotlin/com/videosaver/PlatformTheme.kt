package com.videosaver

import android.graphics.Color

data class PlatformTheme(
    val platform: String,
    val displayName: String,
    val primaryColor: Int,
    val secondaryColor: Int,
    val gradientStart: Int,
    val gradientEnd: Int,
    val logoResId: Int,
    val backgroundTint: Int
)

object PlatformThemeProvider {

    val TIKTOK = PlatformTheme(
        platform = "tiktok",
        displayName = "TikTok",
        primaryColor = Color.parseColor("#EE1D52"),
        secondaryColor = Color.parseColor("#69C9D0"),
        gradientStart = Color.parseColor("#EE1D52"),
        gradientEnd = Color.parseColor("#69C9D0"),
        logoResId = R.drawable.ic_tiktok,
        backgroundTint = Color.parseColor("#1A0A0D")
    )

    val TWITTER = PlatformTheme(
        platform = "twitter",
        displayName = "Twitter / X",
        primaryColor = Color.parseColor("#1DA1F2"),
        secondaryColor = Color.parseColor("#0D8ECF"),
        gradientStart = Color.parseColor("#1DA1F2"),
        gradientEnd = Color.parseColor("#0A66C2"),
        logoResId = R.drawable.ic_twitter,
        backgroundTint = Color.parseColor("#060D14")
    )

    val INSTAGRAM = PlatformTheme(
        platform = "instagram",
        displayName = "Instagram",
        primaryColor = Color.parseColor("#E1306C"),
        secondaryColor = Color.parseColor("#F77737"),
        gradientStart = Color.parseColor("#833AB4"),
        gradientEnd = Color.parseColor("#F77737"),
        logoResId = R.drawable.ic_instagram,
        backgroundTint = Color.parseColor("#100610")
    )

    val DEFAULT = PlatformTheme(
        platform = "unknown",
        displayName = "VideoSaver",
        primaryColor = Color.parseColor("#6C63FF"),
        secondaryColor = Color.parseColor("#FF6B9D"),
        gradientStart = Color.parseColor("#6C63FF"),
        gradientEnd = Color.parseColor("#FF6B9D"),
        logoResId = R.drawable.ic_download,
        backgroundTint = Color.parseColor("#0A0A0F")
    )

    fun fromUrl(url: String): PlatformTheme = when {
        url.contains("tiktok.com") -> TIKTOK
        url.contains("twitter.com") || url.contains("x.com") -> TWITTER
        url.contains("instagram.com") -> INSTAGRAM
        else -> DEFAULT
    }
}
