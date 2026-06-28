package com.videosaver.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun formatBytes_bytes() {
        assertEquals("512 B", FormatUtils.formatBytes(512))
    }

    @Test
    fun formatBytes_kilobytes() {
        assertEquals("1 KB", FormatUtils.formatBytes(1024))
    }

    @Test
    fun formatBytes_megabytes() {
        assertEquals("1.5 MB", FormatUtils.formatBytes((1.5 * 1024 * 1024).toLong()))
    }
}
