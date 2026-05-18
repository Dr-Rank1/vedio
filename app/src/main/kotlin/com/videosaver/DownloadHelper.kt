package com.videosaver

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder

object DownloadHelper {

    private const val TAG = "DownloadHelper"
    private const val MAX_RETRIES = 2

    suspend fun getVideoUrl(url: String): String? = withContext(Dispatchers.IO) {
        for (attempt in 1..MAX_RETRIES) {
            Log.d(TAG, "Attempt $attempt for: $url")
            val result = runCatching {
                when {
                    url.contains("tiktok.com") -> getTikTokVideoUrl(url)
                    url.contains("twitter.com") || url.contains("x.com") -> getTwitterVideoUrl(url)
                    url.contains("instagram.com") -> getInstagramVideoUrl(url)
                    else -> return@withContext null
                }
            }.getOrElse {
                Log.e(TAG, "Attempt $attempt threw: ${it.message}")
                null
            }
            if (result != null) return@withContext result
            if (attempt < MAX_RETRIES) {
                Log.d(TAG, "Attempt $attempt failed, retrying in 500ms…")
                delay(500)
            }
        }
        Log.e(TAG, "All $MAX_RETRIES attempts failed for: $url")
        null
    }

    private fun getTikTokVideoUrl(url: String): String? =
        tryTikWmPost(url) ?: tryTikWmGet(url)

    private fun tryTikWmPost(url: String): String? {
        return try {
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            val formBody = FormBody.Builder()
                .add("url", encodedUrl)
                .add("hd", "1")
                .build()
            val request = Request.Builder()
                .url("https://tikwm.com/api/")
                .post(formBody)
                .addHeader("Accept", "application/json")
                .build()
            val response = HttpClientProvider.client.newCall(request).execute()
            if (!response.isSuccessful) return null
            parseTikWmResponse(response.body?.string() ?: return null)
        } catch (e: Exception) {
            Log.e(TAG, "TikWM POST: ${e.message}")
            null
        }
    }

    private fun tryTikWmGet(url: String): String? {
        return try {
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            val request = Request.Builder()
                .url("https://www.tikwm.com/api/?url=$encodedUrl")
                .addHeader("Accept", "application/json")
                .build()
            val response = HttpClientProvider.client.newCall(request).execute()
            if (!response.isSuccessful) return null
            parseTikWmResponse(response.body?.string() ?: return null)
        } catch (e: Exception) {
            Log.e(TAG, "TikWM GET: ${e.message}")
            null
        }
    }

    private fun parseTikWmResponse(body: String): String? {
        return try {
            val data = JSONObject(body).optJSONObject("data") ?: return null
            val play = data.optString("play").takeIf { it.startsWith("http") }
            val wmPlay = data.optString("wmplay").takeIf { it.startsWith("http") }
            play ?: wmPlay
        } catch (e: Exception) {
            Log.e(TAG, "TikWM parse: ${e.message}")
            null
        }
    }

    private fun getTwitterVideoUrl(url: String): String? {
        return try {
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            val request = Request.Builder()
                .url("https://twitsave.com/info?url=$encodedUrl")
                .build()
            val response = HttpClientProvider.client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val html = response.body?.string() ?: return null
            val doc = Jsoup.parse(html)
            doc.select("a[href$=.mp4]").firstOrNull()?.attr("href")
                ?.takeIf { it.startsWith("http") }
        } catch (e: Exception) {
            Log.e(TAG, "Twitter: ${e.message}")
            null
        }
    }

    private fun getInstagramVideoUrl(url: String): String? =
        trySnapSave(url) ?: tryFastDL(url)

    private fun trySnapSave(url: String): String? {
        return try {
            val formBody = FormBody.Builder().add("url", url).build()
            val request = Request.Builder()
                .url("https://snapsave.app/action.php")
                .post(formBody)
                .addHeader("Referer", "https://snapsave.app/")
                .addHeader("Origin", "https://snapsave.app")
                .build()
            val response = HttpClientProvider.client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            JSONObject(body).optString("url").takeIf { it.startsWith("http") }
        } catch (e: Exception) {
            Log.e(TAG, "SnapSave: ${e.message}")
            null
        }
    }

    private fun tryFastDL(url: String): String? {
        return try {
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            val request = Request.Builder()
                .url("https://fastdl.app/api/instagram?url=$encodedUrl")
                .build()
            val response = HttpClientProvider.client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            JSONObject(body).optString("download_url").takeIf { it.startsWith("http") }
        } catch (e: Exception) {
            Log.e(TAG, "FastDL: ${e.message}")
            null
        }
    }
}
