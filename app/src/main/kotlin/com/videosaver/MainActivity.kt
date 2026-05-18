package com.videosaver

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.videosaver.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var buttonGradient: GradientDrawable

    private val fullHistoryList = mutableListOf<HistoryItem>()
    private var currentFilter = "all"
    private var currentTheme = PlatformThemeProvider.DEFAULT

    private var downloadId = -1L
    private var currentFilename = ""
    private var currentSourceUrl = ""
    private var currentPlatform = ""
    private var downloadJob: Job? = null
    private var detectionJob: Job? = null

    private val isDarkMode get() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != downloadId) return

            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val cursor = dm.query(DownloadManager.Query().setFilterById(id))
            if (!cursor.moveToFirst()) { cursor.close(); return }
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            cursor.close()

            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    currentFilename
                )
                MediaScannerConnection.scanFile(
                    context, arrayOf(file.absolutePath), arrayOf("video/mp4"), null
                )
                val item = HistoryItem(
                    filename = currentFilename,
                    sourceUrl = currentSourceUrl,
                    timestamp = System.currentTimeMillis(),
                    localPath = file.absolutePath,
                    platform = currentPlatform
                )
                saveHistoryItem(item)
                NotificationHelper.showSuccess(context, currentFilename)
                hideProgressCard()
                binding.downloadButton.isEnabled = true
                refreshHistory()
                SnackbarHelper.showSuccess(binding.root, "Saved! \"$currentFilename\"")
            } else if (status == DownloadManager.STATUS_FAILED) {
                hideProgressCard()
                binding.downloadButton.isEnabled = true
                SnackbarHelper.showError(binding.root, "Download failed. Check your connection.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(ThemeManager.getSavedMode(this))
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyStatusBarForMode()
        setupButtonGradient()

        historyAdapter = HistoryAdapter(
            onPlay = { item -> playVideo(item) },
            onShare = { item -> shareVideo(item) },
            onDelete = { item -> confirmDelete(item) }
        )
        setupClickListeners()
        setupUrlWatcher()
        requestPermissionsIfNeeded()

        window.decorView.post {
            binding.historyRecyclerView.apply {
                layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
                adapter = historyAdapter
                setHasFixedSize(false)
                recycledViewPool.setMaxRecycledViews(0, 10)
                setItemViewCacheSize(10)
            }
            lifecycleScope.launch {
                val items = withContext(Dispatchers.IO) { loadHistoryFromPrefs() }
                fullHistoryList.addAll(items)
                applyFilter()
                updateStats()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(downloadReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(downloadReceiver) } catch (_: IllegalArgumentException) {}
    }

    // ─── Status Bar ───────────────────────────────────────────────────────────

    private fun applyStatusBarForMode() {
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            isAppearanceLightStatusBars = !isDarkMode
        }
        window.statusBarColor = if (isDarkMode)
            Color.parseColor("#0A0A0F") else Color.parseColor("#F2F2FA")
    }

    private fun applyStatusBarForTheme(theme: PlatformTheme) {
        val isPlatformDetected = theme.platform != "unknown"
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            // Dark backgrounds (platform tints or dark mode) need light icons
            isAppearanceLightStatusBars = !isPlatformDetected && !isDarkMode
        }
        window.statusBarColor = when {
            isPlatformDetected -> theme.backgroundTint
            isDarkMode -> Color.parseColor("#0A0A0F")
            else -> Color.parseColor("#F2F2FA")
        }
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private fun setupButtonGradient() {
        val gradEnd = if (isDarkMode) Color.parseColor("#FF6B9D") else Color.parseColor("#8B85FF")
        buttonGradient = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#6C63FF"), gradEnd)
        ).apply {
            cornerRadius = 29f * resources.displayMetrics.density
        }
        binding.downloadButton.background = buttonGradient
    }

    private fun setupClickListeners() {
        binding.settingsButton.setOnClickListener {
            SettingsBottomSheet().show(supportFragmentManager, "settings")
        }

        binding.pasteButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.let { text ->
                binding.urlInput.setText(text)
                binding.urlInput.setSelection(text.length)
            }
        }

        binding.downloadButton.setOnClickListener {
            val url = binding.urlInput.text.toString().trim()
            if (url.isEmpty()) {
                SnackbarHelper.showError(binding.root, getString(R.string.unsupported_url))
                return@setOnClickListener
            }
            animateButtonPress { startDownload(url) }
        }

        binding.cancelButton.setOnClickListener {
            downloadJob?.cancel()
            if (downloadId != -1L) {
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).remove(downloadId)
                downloadId = -1L
            }
            hideProgressCard()
            binding.downloadButton.isEnabled = true
            SnackbarHelper.showInfo(binding.root, "Download cancelled")
        }

        binding.clearAllButton.setOnClickListener {
            if (fullHistoryList.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("Clear All Downloads")
                .setMessage("Remove all download history? This won't delete the video files.")
                .setPositiveButton("Clear") { _, _ -> clearAllHistoryExternal() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        setupFilterChips()
    }

    private fun setupFilterChips() {
        val chips = listOf(binding.chipAll, binding.chipTikTok, binding.chipTwitter, binding.chipInstagram)
        val platforms = listOf("all", "tiktok", "twitter", "instagram")

        chips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                currentFilter = platforms[index]
                updateChipSelectionUI()
                historyAdapter.resetAnimationState()
                applyFilter()
            }
        }
    }

    private fun setupUrlWatcher() {
        binding.urlInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                detectionJob?.cancel()
                detectionJob = lifecycleScope.launch {
                    delay(400)
                    val theme = PlatformThemeProvider.fromUrl(s?.toString() ?: "")
                    applyPlatformTheme(theme)
                }
            }
        })
    }

    // ─── Chip State Management ────────────────────────────────────────────────

    /** Apply selected visual to a chip — each platform gets its own brand colour. */
    private fun applyChipSelected(chip: TextView, platform: String) {
        val r = resources.displayMetrics.density
        when (platform) {
            "instagram" -> {
                chip.background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.parseColor("#833AB4"), Color.parseColor("#E1306C"))
                ).apply { cornerRadius = 100f * r }
            }
            "tiktok" -> chip.setBackgroundColor(Color.parseColor("#EE1D52"))
            "twitter" -> chip.setBackgroundColor(Color.parseColor("#1DA1F2"))
            else -> chip.setBackgroundResource(R.drawable.chip_selected_bg) // "all" → accent purple
        }
        chip.setTextColor(Color.WHITE)
    }

    /** Apply unselected visual to a chip. */
    private fun applyChipUnselected(chip: TextView) {
        chip.setBackgroundResource(R.drawable.bg_chip_unselected)
        chip.setTextColor(getColor(R.color.colorChipText))
    }

    /** Redraws all four chips based solely on which filter is currently active. */
    private fun updateChipSelectionUI() {
        val chips = listOf(binding.chipAll, binding.chipTikTok, binding.chipTwitter, binding.chipInstagram)
        val platforms = listOf("all", "tiktok", "twitter", "instagram")
        chips.forEachIndexed { i, chip ->
            if (platforms[i] == currentFilter) applyChipSelected(chip, platforms[i])
            else applyChipUnselected(chip)
        }
    }

    /** Called during platform theming: highlight the detected platform chip. */
    private fun updateChipPlatformHighlight(theme: PlatformTheme) {
        val chips = listOf(binding.chipAll, binding.chipTikTok, binding.chipTwitter, binding.chipInstagram)
        val platforms = listOf("all", "tiktok", "twitter", "instagram")

        chips.forEachIndexed { index, chip ->
            when {
                platforms[index] == theme.platform && theme.platform != "unknown" -> {
                    // URL detected — highlight with live platform colour (solid for highlight)
                    chip.setBackgroundColor(theme.primaryColor)
                    chip.setTextColor(Color.WHITE)
                }
                platforms[index] == currentFilter -> applyChipSelected(chip, platforms[index])
                else -> applyChipUnselected(chip)
            }
        }
    }

    // ─── Platform Theming ─────────────────────────────────────────────────────

    private fun applyPlatformTheme(theme: PlatformTheme) {
        if (theme.platform == currentTheme.platform) return
        val previous = currentTheme
        currentTheme = theme

        // 1. Toolbar background tint (animated)
        ValueAnimator.ofObject(ArgbEvaluator(), previous.backgroundTint, theme.backgroundTint).apply {
            duration = 400
            addUpdateListener { binding.toolbar.setBackgroundColor(it.animatedValue as Int) }
            start()
        }

        // 2. Platform label
        binding.platformLabel.text = if (theme.platform == "unknown") "" else theme.displayName
        binding.platformLabel.setTextColor(theme.primaryColor)

        // 3. Download button gradient morph
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            val ev = ArgbEvaluator()
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                buttonGradient.colors = intArrayOf(
                    ev.evaluate(f, previous.gradientStart, theme.gradientStart) as Int,
                    ev.evaluate(f, previous.gradientEnd, theme.gradientEnd) as Int
                )
            }
            start()
        }

        // 4. Progress tints
        binding.progressBar.progressTintList = ColorStateList.valueOf(theme.primaryColor)
        binding.progressSpinner.indeterminateTintList = ColorStateList.valueOf(theme.primaryColor)

        // 5. Platform icon crossfade + tint
        val iconTint = if (theme.platform == "unknown") getColor(R.color.colorTextSecondary) else theme.primaryColor
        binding.platformIcon.clearColorFilter()
        Glide.with(this)
            .load(theme.logoResId)
            .transition(DrawableTransitionOptions.withCrossFade(250))
            .into(binding.platformIcon)
        binding.platformIcon.setColorFilter(iconTint)

        // 6. Input card animated border
        val strokePx = if (theme.platform == "unknown") 0
        else (1.5f * resources.displayMetrics.density).toInt()
        binding.inputCard.strokeWidth = strokePx
        ValueAnimator.ofObject(ArgbEvaluator(), previous.primaryColor, theme.primaryColor).apply {
            duration = 400
            addUpdateListener {
                binding.inputCard.setStrokeColor(ColorStateList.valueOf(it.animatedValue as Int))
            }
            start()
        }

        // 7. Chip highlight
        updateChipPlatformHighlight(theme)

        // 8. Status bar
        applyStatusBarForTheme(theme)
    }

    // ─── Download Flow ────────────────────────────────────────────────────────

    private fun startDownload(url: String) {
        val platform = detectPlatform(url)
        if (platform.isEmpty()) {
            SnackbarHelper.showError(binding.root, getString(R.string.unsupported_url))
            return
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
        ) {
            SnackbarHelper.showError(binding.root, "Storage permission is required to save videos.")
            return
        }

        currentSourceUrl = url
        currentPlatform = platform
        currentFilename = "VideoSaver_${System.currentTimeMillis()}.mp4"

        binding.downloadButton.isEnabled = false
        showProgressCard(currentTheme)

        downloadJob = lifecycleScope.launch {
            val videoUrl = withContext(Dispatchers.IO) { DownloadHelper.getVideoUrl(url) }

            if (videoUrl == null) {
                withContext(Dispatchers.Main) {
                    hideProgressCard()
                    binding.downloadButton.isEnabled = true
                    SnackbarHelper.showError(
                        binding.root, "Could not fetch video. It may be private or unavailable."
                    )
                }
                return@launch
            }

            val request = DownloadManager.Request(Uri.parse(videoUrl))
                .setTitle("VideoSaver")
                .setDescription("Downloading video…")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, currentFilename)
                .addRequestHeader("User-Agent", "Mozilla/5.0")

            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            downloadId = dm.enqueue(request)
            pollDownloadProgress(downloadId)
        }
    }

    private suspend fun pollDownloadProgress(dmId: Long) {
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        while (true) {
            delay(300)
            val cursor = dm.query(DownloadManager.Query().setFilterById(dmId))
            if (!cursor.moveToFirst()) { cursor.close(); break }
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            cursor.close()

            val progress = if (total > 0) (downloaded * 100 / total).toInt() else 0
            withContext(Dispatchers.Main) {
                binding.progressBar.progress = progress
                binding.progressPercent.text = "$progress%"
            }
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL, DownloadManager.STATUS_FAILED -> break
            }
        }
    }

    private fun showProgressCard(theme: PlatformTheme) {
        binding.progressPlatformIcon.setImageResource(theme.logoResId)
        binding.progressLabel.text = getString(R.string.downloading_from, theme.displayName)
        binding.progressBar.progress = 0
        binding.progressPercent.text = "0%"
        binding.progressBar.progressTintList = ColorStateList.valueOf(theme.primaryColor)
        binding.progressSpinner.indeterminateTintList = ColorStateList.valueOf(theme.primaryColor)

        binding.progressCard.visibility = View.VISIBLE
        binding.progressCard.alpha = 0f
        binding.progressCard.translationY = 80f
        binding.progressCard.animate()
            .alpha(1f).translationY(0f)
            .setDuration(300).setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun hideProgressCard() {
        binding.progressCard.animate()
            .alpha(0f).translationY(80f)
            .setDuration(300).setInterpolator(DecelerateInterpolator())
            .withEndAction {
                binding.progressCard.visibility = View.GONE
                binding.progressCard.alpha = 1f
                binding.progressCard.translationY = 0f
            }.start()
    }

    private fun animateButtonPress(onComplete: () -> Unit) {
        binding.downloadButton.animate()
            .scaleX(0.96f).scaleY(0.96f).setDuration(80)
            .withEndAction {
                binding.downloadButton.animate()
                    .scaleX(1f).scaleY(1f).setDuration(80)
                    .withEndAction { onComplete() }.start()
            }.start()
    }

    // ─── Filter & Stats ───────────────────────────────────────────────────────

    private fun applyFilter() {
        val filtered = if (currentFilter == "all") fullHistoryList.toList()
        else fullHistoryList.filter { it.platform == currentFilter }
        historyAdapter.submitList(filtered)
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        updateChipCounts()
    }

    private fun updateChipCounts() {
        fun label(base: String, count: Int) = if (count > 0) "$base ($count)" else base
        binding.chipAll.text = label("All", fullHistoryList.size)
        binding.chipTikTok.text = label("TikTok", fullHistoryList.count { it.platform == "tiktok" })
        binding.chipTwitter.text = label("Twitter", fullHistoryList.count { it.platform == "twitter" })
        binding.chipInstagram.text = label("Instagram", fullHistoryList.count { it.platform == "instagram" })
        // Re-apply chip visuals after text change (text change can reset view state)
        updateChipSelectionUI()
    }

    private fun updateStats() {
        var totalBytes = 0L
        var todayCount = 0
        val today = Calendar.getInstance()
        for (item in fullHistoryList) {
            val f = File(item.localPath)
            if (f.exists()) totalBytes += f.length()
            val cal = Calendar.getInstance().apply { timeInMillis = item.timestamp }
            if (cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
            ) todayCount++
        }
        binding.statTotalCount.text = fullHistoryList.size.toString()
        binding.statStorageCount.text = formatBytes(totalBytes)
        binding.statTodayCount.text = todayCount.toString()
    }

    // ─── History CRUD ─────────────────────────────────────────────────────────

    private fun loadHistoryFromPrefs(): List<HistoryItem> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
        val items = mutableListOf<HistoryItem>()
        for (i in array.length() - 1 downTo 0) {
            val obj = array.getJSONObject(i)
            val src = obj.getString("sourceUrl")
            val platform = obj.optString("platform", "").ifEmpty {
                when {
                    src.contains("tiktok.com") -> "tiktok"
                    src.contains("twitter.com") || src.contains("x.com") -> "twitter"
                    src.contains("instagram.com") -> "instagram"
                    else -> ""
                }
            }
            items += HistoryItem(
                filename = obj.getString("filename"),
                sourceUrl = src,
                timestamp = obj.getLong("timestamp"),
                localPath = obj.getString("localPath"),
                platform = platform
            )
        }
        return items
    }

    private fun refreshHistory() {
        fullHistoryList.clear()
        fullHistoryList.addAll(loadHistoryFromPrefs())
        historyAdapter.resetAnimationState()
        applyFilter()
        updateStats()
    }

    private fun saveHistoryItem(item: HistoryItem) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
        array.put(JSONObject().apply {
            put("filename", item.filename)
            put("sourceUrl", item.sourceUrl)
            put("timestamp", item.timestamp)
            put("localPath", item.localPath)
            put("platform", item.platform)
        })
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    private fun removeHistoryItem(item: HistoryItem) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
        val filtered = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getString("localPath") != item.localPath) filtered.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, filtered.toString()).apply()
    }

    fun clearAllHistoryExternal() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_HISTORY, "[]").apply()
        fullHistoryList.clear()
        historyAdapter.resetAnimationState()
        historyAdapter.submitList(emptyList())
        updateStats()
        updateChipCounts()
        binding.emptyState.visibility = View.VISIBLE
    }

    // ─── Media Actions ────────────────────────────────────────────────────────

    private fun playVideo(item: HistoryItem) {
        val file = File(item.localPath)
        if (!file.exists()) { SnackbarHelper.showError(binding.root, "File not found."); return }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Open with"
        ))
    }

    private fun shareVideo(item: HistoryItem) {
        val file = File(item.localPath)
        if (!file.exists()) { SnackbarHelper.showError(binding.root, "File not found."); return }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share video"
        ))
    }

    private fun confirmDelete(item: HistoryItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Video")
            .setMessage("Delete \"${item.filename}\"?")
            .setPositiveButton("Delete") { _, _ ->
                File(item.localPath).takeIf { it.exists() }?.delete()
                removeHistoryItem(item)
                refreshHistory()
                SnackbarHelper.showInfo(binding.root, "Deleted")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Permissions ──────────────────────────────────────────────────────────

    private fun requestPermissionsIfNeeded() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
        ) perms += Manifest.permission.WRITE_EXTERNAL_STORAGE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) perms += Manifest.permission.POST_NOTIFICATIONS

        if (perms.isNotEmpty())
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            val idx = permissions.indexOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (idx >= 0 && grantResults[idx] != PackageManager.PERMISSION_GRANTED)
                SnackbarHelper.showError(binding.root, "Storage permission is required to save videos.")
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun detectPlatform(url: String): String = when {
        url.contains("tiktok.com") -> "tiktok"
        url.contains("twitter.com") || url.contains("x.com") -> "twitter"
        url.contains("instagram.com") -> "instagram"
        else -> ""
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        bytes < 1024L * 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 100
        private const val PREFS_NAME = "videosaver"
        private const val KEY_HISTORY = "download_history"
    }
}
