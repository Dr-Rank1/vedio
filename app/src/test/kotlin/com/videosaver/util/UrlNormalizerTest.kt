package com.videosaver.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlNormalizerTest {

    @Test
    fun normalize_addsHttpsScheme() {
        assertEquals(
            "https://tiktok.com/@user/video/1",
            UrlNormalizer.normalize("tiktok.com/@user/video/1")
        )
    }

    @Test
    fun detect_shortTikTokLink() {
        assertEquals(Platform.TIKTOK, UrlNormalizer.detectPlatform("https://vm.tiktok.com/abc123"))
    }

    @Test
    fun detect_instagramReel() {
        assertEquals(Platform.INSTAGRAM, UrlNormalizer.detectPlatform("https://instagram.com/reel/xyz"))
    }
}
