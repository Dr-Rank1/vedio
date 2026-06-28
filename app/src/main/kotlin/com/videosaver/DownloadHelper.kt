package com.videosaver

import android.content.Context
import android.util.Log
import com.videosaver.prefs.AppPrefs
import com.videosaver.util.Platform
import com.videosaver.util.UrlNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder

data class DownloadOptions(
    val hdQuality: Boolean = true,
    val removeTikTokWatermark: Boolean = true,
)

enum class DownloadFailureReason {
    UNSUPPORTED,
    PRIVATE_OR_UNAVAILABLE,
    NETWORK,
    SERVICE_UNAVAILABLE,
    PARSE_ERROR,
}

sealed class DownloadResult {
    data class Success(val videoUrl: String, val provider: String) : DownloadResult()
    data class Failure(val message: String, val reason: DownloadFailureReason) : DownloadResult()
}

object DownloadHelper {

    private const val TAG = "DownloadHelper"
    private const val MAX_RETRIES = 2

    suspend fun resolveVideoUrl(
        context: Context,
        pageUrl: String,
        options: DownloadOptions = DownloadOptions(),
    ): DownloadResult = withContext(Dispatchers.IO) {
        val url = UrlNormalizer.normalize(pageUrl)
        when (UrlNormalizer.detectPlatform(url)) {
            Platform.TIKTOK -> resolveWithRetry { getTikTokVideoUrl(context, url, options) }
            Platform.TWITTER -> resolveWithRetry { getTwitterVideoUrl(url) }
            Platform.INSTAGRAM -> resolveWithRetry { getInstagramVideoUrl(context, url) }
            Platform.UNKNOWN -> DownloadResult.Failure(
                message = "Unsupported link. Paste a TikTok, Twitter/X, or Instagram URL.",
                reason = DownloadFailureReason.UNSUPPORTED,
            )
        }
    }

    private suspend fun resolveWithRetry(
        block: suspend () -> ProviderResult?,
    ): DownloadResult {
        var lastError = DownloadResult.Failure(
            message = "Could not fetch video. The link may be private, deleted, or temporarily unavailable.",
            reason = DownloadFailureReason.PRIVATE_OR_UNAVAILABLE,
        )
        repeat(MAX_RETRIES) { attempt ->
            Log.d(TAG, "Attempt ${attempt + 1}")
            val result = runCatching { block() }.getOrElse {
                Log.e(TAG, "Attempt ${attempt + 1} threw: ${it.message}")
                null
            }
            when {
                result?.url != null -> return DownloadResult.Success(result.url, result.provider)
                result?.failure != null -> lastError = result.failure
            }
            if (attempt < MAX_RETRIES - 1) delay(500)
        }
        return lastError
    }

    private data class ProviderResult(
        val url: String? = null,
        val provider: String = "",
        val failure: DownloadResult.Failure? = null,
    )

    private suspend fun getTikTokVideoUrl(
        context: Context,
        url: String,
        options: DownloadOptions,
    ): ProviderResult? {
        val cached = AppPrefs.getCachedEndpoint(context, AppPrefs.KEY_TIKTOK_ENDPOINT)
        val order = listOf(AppPrefs.ENDPOINT_TIKWM_POST, AppPrefs.ENDPOINT_TIKWM_GET)
            .sortedByDescending { it == cached }
        for (endpoint in order) {
            val resolved = when (endpoint) {
                AppPrefs.ENDPOINT_TIKWM_POST -> tryTikWmPost(url, options)
                else -> tryTikWmGet(url, options)
            }
            if (resolved != null) {
                AppPrefs.setCachedEndpoint(context, AppPrefs.KEY_TIKTOK_ENDPOINT, endpoint)
                return ProviderResult(resolved, endpoint)
            }
        }
        return ProviderResult(
            failure = DownloadResult.Failure(
                message = "TikTok download service is unavailable right now. Try again in a few minutes.",
                reason = DownloadFailureReason.SERVICE_UNAVAILABLE,
            )
        )
    }

    private fun tryTikWmPost(url: String, options: DownloadOptions): String? {
        return try {
            val formBody = FormBody.Builder()
                .add("url", url)
                .add("hd", if (options.hdQuality) "1" else "0")
                .build()
            val request = Request.Builder()
                .url("https://tikwm.com/api/")
                .post(formBody)
                .addHeader("Accept", "application/json")
                .build()
            val response = HttpClientProvider.client.newCall(request).execute()
            if (!response.isSuccessful) return null
            parseTikWmResponse(response.body?.string() ?: return null, options)
        } catch (e: Exception) {
            Log.e(TAG, "TikWM POST: ${e.message}")
            null
        }
    }

    private fun tryTikWmGet(url: String, options: DownloadOptions): String? {
        return try {
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            val request = Request.Builder()
                .url("https://www.tikwm.com/api/?url=$encodedUrl")
                .addHeader("Accept", "application/json")
                .build()
            val response = HttpClientProvider.client.newCall(request).execute()
            if (!response.isSuccessful) return null
            parseTikWmResponse(response.body?.string() ?: return null, options)
        } catch (e: Exception) {
            Log.e(TAG, "TikWM GET: ${e.message}")
            null
        }
    }

    internal fun parseTikWmResponse(body: String, options: DownloadOptions): String? =
        TikWmParser.parse(body, options)

    private fun getTwitterVideoUrl(url: String): ProviderResult? {
        return try {
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            val request = Request.Builder()
                .url("https://twitsave.com/info?url=$encodedUrl")
                .build()
            val response = HttpClientProvider.client.newCall(request).execute()
            if (!response.isSuccessful) {
                return ProviderResult(
                    failure = DownloadResult.Failure(
                        message = "Twitter/X download service is unavailable right now. Try again later.",
                        reason = DownloadFailureReason.SERVICE_UNAVAILABLE,
                    )
                )
            }
            val html = response.body?.string() ?: return null
            val videoUrl = Jsoup.parse(html).select("a[href$=.mp4]").firstOrNull()?.attr("href")
                ?.takeIf { it.startsWith("https://") }
            if (videoUrl != null) ProviderResult(videoUrl, "twitsave") else ProviderResult(
                failure = DownloadResult.Failure(
                    message = "No video found on this tweet. It may be text-only or protected.",
                    reason = DownloadFailureReason.PRIVATE_OR_UNAVAILABLE,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Twitter: ${e.message}")
            ProviderResult(
                failure = DownloadResult.Failure(
                    message = "Network error while fetching Twitter/X video. Check your connection.",
                    reason = DownloadFailureReason.NETWORK,
                )
            )
        }
    }

    private suspend fun getInstagramVideoUrl(context: Context, url: String): ProviderResult? {
        val cached = AppPrefs.getCachedEndpoint(context, AppPrefs.KEY_INSTAGRAM_ENDPOINT)
        val order = listOf(AppPrefs.ENDPOINT_SNAPSAVE, AppPrefs.ENDPOINT_FASTDL)
            .sortedByDescending { it == cached }
        for (endpoint in order) {
            val resolved = when (endpoint) {
                AppPrefs.ENDPOINT_SNAPSAVE -> trySnapSave(url)
                else -> tryFastDL(url)
            }
            if (resolved != null) {
                AppPrefs.setCachedEndpoint(context, AppPrefs.KEY_INSTAGRAM_ENDPOINT, endpoint)
                return ProviderResult(resolved, endpoint)
            }
        }
        return ProviderResult(
            failure = DownloadResult.Failure(
                message = "Instagram download service is unavailable right now. Try again in a few minutes.",
                reason = DownloadFailureReason.SERVICE_UNAVAILABLE,
            )
        )
    }

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
            JSONObject(body).optString("url").takeIf { it.startsWith("https://") }
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
            JSONObject(body).optString("download_url").takeIf { it.startsWith("https://") }
        } catch (e: Exception) {
            Log.e(TAG, "FastDL: ${e.message}")
            null
        }
    }
}
