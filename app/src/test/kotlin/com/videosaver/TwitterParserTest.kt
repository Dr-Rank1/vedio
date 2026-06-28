package com.videosaver

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jsoup.Jsoup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class TwitterParserTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun parseMp4LinkFromHtml() {
        val html = """
            <html><body>
            <a href="https://cdn.example.com/video.mp4">Download</a>
            </body></html>
        """.trimIndent()
        val href = Jsoup.parse(html).select("a[href$=.mp4]").firstOrNull()?.attr("href")
        assertNotNull(href)
        assertEquals("https://cdn.example.com/video.mp4", href)
    }

    @Test
    fun mockServer_returnsHtml() {
        server.enqueue(MockResponse().setBody("<a href=\"https://cdn.test/v.mp4\">x</a>"))
        val url = server.url("/info").toString()
        assertNotNull(url)
    }
}
