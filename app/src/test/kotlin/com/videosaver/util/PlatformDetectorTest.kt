package com.videosaver.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformDetectorTest {

    @Test
    fun detect_tikTokUrl() {
        assertEquals(PlatformDetector.TIKTOK, PlatformDetector.detect("https://www.tiktok.com/@user/video/123"))
    }

    @Test
    fun detect_twitterUrl() {
        assertEquals(PlatformDetector.TWITTER, PlatformDetector.detect("https://twitter.com/user/status/123"))
    }

    @Test
    fun detect_xUrl() {
        assertEquals(PlatformDetector.TWITTER, PlatformDetector.detect("https://x.com/user/status/123"))
    }

    @Test
    fun detect_instagramUrl() {
        assertEquals(PlatformDetector.INSTAGRAM, PlatformDetector.detect("https://www.instagram.com/reel/abc"))
    }

    @Test
    fun detect_shortTikTokLink() {
        assertEquals(PlatformDetector.TIKTOK, PlatformDetector.detect("https://vm.tiktok.com/abc123"))
    }

    @Test
    fun detect_unknownUrl() {
        assertEquals(PlatformDetector.UNKNOWN, PlatformDetector.detect("https://example.com/video"))
    }
}
