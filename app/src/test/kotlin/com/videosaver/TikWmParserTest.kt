package com.videosaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TikWmParserTest {

    @Test
    fun parse_prefersNoWatermarkByDefault() {
        val body = """
            {"data":{"play":"https://cdn.example.com/play.mp4","wmplay":"https://cdn.example.com/wm.mp4"}}
        """.trimIndent()
        val url = TikWmParser.parse(body, DownloadOptions(removeTikTokWatermark = true))
        assertEquals("https://cdn.example.com/play.mp4", url)
    }

    @Test
    fun parse_prefersWatermarkWhenRequested() {
        val body = """
            {"data":{"play":"https://cdn.example.com/play.mp4","wmplay":"https://cdn.example.com/wm.mp4"}}
        """.trimIndent()
        val url = TikWmParser.parse(body, DownloadOptions(removeTikTokWatermark = false))
        assertEquals("https://cdn.example.com/wm.mp4", url)
    }

    @Test
    fun parse_fallsBackWhenPrimaryMissing() {
        val body = """{"data":{"wmplay":"https://cdn.example.com/wm.mp4"}}"""
        val url = TikWmParser.parse(body, DownloadOptions(removeTikTokWatermark = true))
        assertEquals("https://cdn.example.com/wm.mp4", url)
    }

    @Test
    fun parse_returnsNullForInvalidJson() {
        assertNull(TikWmParser.parse("not-json", DownloadOptions()))
    }
}
