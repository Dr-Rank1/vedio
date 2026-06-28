package com.videosaver.util

object PlatformDetector {
    val TIKTOK = Platform.TIKTOK.id
    val TWITTER = Platform.TWITTER.id
    val INSTAGRAM = Platform.INSTAGRAM.id
    val UNKNOWN = Platform.UNKNOWN.id

    fun detect(url: String): String = UrlNormalizer.detectPlatform(url).id

    fun toPlatform(id: String): Platform = Platform.fromId(id)
}
