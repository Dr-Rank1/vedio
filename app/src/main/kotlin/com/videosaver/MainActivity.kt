package com.videosaver

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.animation.DecelerateInterpolator
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.videosaver.databinding.ActivityMainBinding
import com.videosaver.download.DownloadWork
import com.videosaver.download.VideoDownloadWorker
import com.videosaver.prefs.AppPrefs
import com.videosaver.storage.StorageHelper
import com.videosaver.ui.MainViewModel
import com.videosaver.ui.SortOrder
import com.videosaver.util.FilenameUtils
import com.videosaver.util.FormatUtils
import com.videosaver.util.PlatformDetector
import com.videosaver.util.UrlNormalizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), HistoryClearListener {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var buttonGradient: GradientDrawable

    private var currentTheme = PlatformThemeProvider.DEFAULT
    private var detectionJob: Job? = null
    private var lastClipboardText: String? = null
    private var activeDownload = false

    private val isDarkMode
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(ThemeManager.getSavedMode(this))
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets()
        applyStatusBarForMode()
        setupButtonGradient()

        historyAdapter = HistoryAdapter(
            onPlay = { playVideo(it) },
            onShare = { shareVideo(it) },
            onDelete = { confirmDelete(it) },
            onRedownload = { redownload(it) },
        )

        binding.historyRecyclerView.apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            adapter = historyAdapter
            setHasFixedSize(false)
            recycledViewPool.setMaxRecycledViews(0, 10)
            setItemViewCacheSize(10)
        }

        setupClickListeners()
        setupUrlWatcher()
        setupSearchWatcher()
        setupDownloadObserver()
        observeViewModel()
        requestPermissionsIfNeeded()
        handleShareIntent(intent)
        PrivacyDisclaimerDialog.showIfNeeded(supportFragmentManager, this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        detectClipboardLink()
    }

    override fun onHistoryCleared(message: String?) {
        historyAdapter.resetAnimationState()
        message?.let { SnackbarHelper.showInfo(binding.root, it) }
    }

    private fun setupEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.filteredHistory.collect { items ->
                        historyAdapter.submitList(items)
                        updateEmptyStates(items)
                    }
                }
                launch {
                    viewModel.stats.collect { stats ->
                        binding.statTotalCount.text = stats.totalCount.toString()
                        binding.statStorageCount.text = FormatUtils.formatBytes(stats.totalBytes)
                        binding.statTodayCount.text = stats.todayCount.toString()
                        updateChipCounts(stats)
                    }
                }
                launch {
                    viewModel.filter.collect { updateChipSelectionUI(it) }
                }
            }
        }
    }

    private fun updateEmptyStates(filtered: List<HistoryItem>) {
        val hasAny = viewModel.allHistory.value.isNotEmpty()
        val showEmpty = !hasAny
        val showFilterEmpty = hasAny && filtered.isEmpty()
        binding.emptyState.visibility = if (showEmpty) View.VISIBLE else View.GONE
        binding.filterEmptyState.visibility = if (showFilterEmpty) View.VISIBLE else View.GONE
        binding.historyRecyclerView.visibility =
            if (!showEmpty && !showFilterEmpty) View.VISIBLE else View.GONE
    }

    private fun setupDownloadObserver() {
        lifecycleScope.launch {
            WorkManager.getInstance(this@MainActivity)
                .getWorkInfosByTagFlow(DownloadWork.DOWNLOAD_TAG)
                .collect { infos ->
                    val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                    val enqueued = infos.any { it.state == WorkInfo.State.ENQUEUED }

                    when {
                        running != null -> {
                            if (!activeDownload) {
                                activeDownload = true
                                val url = running.progress.getString(VideoDownloadWorker.KEY_SOURCE_URL)
                                    ?: binding.urlInput.text.toString()
                                showProgressCard(PlatformThemeProvider.fromUrl(url))
                            }
                            val progress = running.progress.getInt(VideoDownloadWorker.KEY_PROGRESS, 0)
                            binding.progressBar.progress = progress
                            binding.progressPercent.text = "$progress%"
                            binding.downloadButton.isEnabled = false
                        }
                        enqueued && running == null -> {
                            if (!activeDownload) {
                                activeDownload = true
                                showProgressCard(currentTheme)
                            }
                            binding.downloadButton.isEnabled = false
                        }
                    }

                    infos.filter { it.state.isFinished }.forEach { info ->
                        when (info.state) {
                            WorkInfo.State.SUCCEEDED -> {
                                activeDownload = false
                                hideProgressCard()
                                binding.downloadButton.isEnabled = true
                                val filename = info.outputData.getString(VideoDownloadWorker.KEY_FILENAME)
                                    ?: getString(R.string.app_name)
                                SnackbarHelper.showSuccess(
                                    binding.root,
                                    getString(R.string.download_saved, filename),
                                )
                                if (info.outputData.getBoolean(VideoDownloadWorker.KEY_USED_FALLBACK, false)) {
                                    SnackbarHelper.showInfo(
                                        binding.root,
                                        getString(R.string.fallback_folder_used),
                                    )
                                }
                            }
                            WorkInfo.State.FAILED -> {
                                activeDownload = false
                                hideProgressCard()
                                binding.downloadButton.isEnabled = true
                                val message = info.outputData.getString(VideoDownloadWorker.KEY_ERROR_MESSAGE)
                                    ?: getString(R.string.download_failed_generic)
                                SnackbarHelper.showError(binding.root, message)
                            }
                            WorkInfo.State.CANCELLED -> {
                                activeDownload = false
                                hideProgressCard()
                                binding.downloadButton.isEnabled = true
                            }
                            else -> Unit
                        }
                    }

                    if (infos.none { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }) {
                        activeDownload = false
                    }
                }
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                        binding.urlInput.setText(text.trim())
                        binding.urlInput.setSelection(binding.urlInput.text.length)
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                intent.dataString?.let { url ->
                    binding.urlInput.setText(url.trim())
                    binding.urlInput.setSelection(binding.urlInput.text.length)
                }
            }
        }
    }

    private fun detectClipboardLink() {
        if (!AppPrefs.isClipboardPromptEnabled(this)) return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
            ?: return
        if (text == lastClipboardText || text.isEmpty()) return
        val platform = PlatformDetector.detect(text)
        if (platform == PlatformDetector.UNKNOWN) return
        if (binding.urlInput.text.toString().trim().isNotEmpty()) return
        lastClipboardText = text
        binding.urlInput.setText(text)
        binding.urlInput.setSelection(text.length)
        SnackbarHelper.showInfo(binding.root, getString(R.string.clipboard_detected))
    }

    private fun applyStatusBarForMode() {
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            isAppearanceLightStatusBars = !isDarkMode
        }
        window.statusBarColor = if (isDarkMode) {
            Color.parseColor("#0A0A0F")
        } else {
            Color.parseColor("#F2F2FA")
        }
    }

    private fun applyStatusBarForTheme(theme: PlatformTheme) {
        val isPlatformDetected = theme.platform != PlatformDetector.UNKNOWN
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            isAppearanceLightStatusBars = !isPlatformDetected && !isDarkMode
        }
        window.statusBarColor = when {
            isPlatformDetected -> theme.backgroundTint
            isDarkMode -> Color.parseColor("#0A0A0F")
            else -> Color.parseColor("#F2F2FA")
        }
    }

    private fun setupButtonGradient() {
        val gradEnd = if (isDarkMode) Color.parseColor("#FF6B9D") else Color.parseColor("#8B85FF")
        buttonGradient = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#6C63FF"), gradEnd),
        ).apply {
            cornerRadius = 29f * resources.displayMetrics.density
        }
        binding.downloadButton.background = buttonGradient
    }

    private fun setupClickListeners() {
        binding.settingsButton.setOnClickListener {
            SettingsBottomSheet().show(supportFragmentManager, "settings")
        }

        binding.sortButton.setOnClickListener { showSortMenu(it) }

        binding.pasteButton.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.let { text ->
                binding.urlInput.setText(text)
                binding.urlInput.setSelection(text.length)
            }
        }

        binding.urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                triggerDownload()
                true
            } else {
                false
            }
        }

        binding.downloadButton.setOnClickListener {
            animateButtonPress { triggerDownload() }
        }

        binding.cancelButton.setOnClickListener {
            DownloadWork.cancelAll(this)
            hideProgressCard()
            binding.downloadButton.isEnabled = true
            SnackbarHelper.showInfo(binding.root, getString(R.string.download_cancelled))
        }

        binding.clearAllButton.setOnClickListener {
            if (viewModel.allHistory.value.isEmpty()) return@setOnClickListener
            showClearAllDialog()
        }

        setupFilterChips()
    }

    private fun triggerDownload() {
        val url = binding.urlInput.text.toString().trim()
        if (url.isEmpty()) {
            SnackbarHelper.showError(binding.root, getString(R.string.empty_url_hint))
            return
        }
        startDownload(url)
    }

    private fun showSortMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 0, 0, R.string.sort_newest)
        popup.menu.add(0, 1, 1, R.string.sort_oldest)
        popup.menu.add(0, 2, 2, R.string.sort_name_asc)
        popup.menu.add(0, 3, 3, R.string.sort_name_desc)
        popup.menu.add(0, 4, 4, R.string.sort_size_desc)
        popup.menu.add(0, 5, 5, R.string.sort_size_asc)
        popup.setOnMenuItemClickListener { item ->
            val order = when (item.itemId) {
                0 -> SortOrder.NEWEST
                1 -> SortOrder.OLDEST
                2 -> SortOrder.NAME_ASC
                3 -> SortOrder.NAME_DESC
                4 -> SortOrder.SIZE_DESC
                else -> SortOrder.SIZE_ASC
            }
            viewModel.setSortOrder(order)
            historyAdapter.resetAnimationState()
            true
        }
        popup.show()
    }

    private fun showClearAllDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_all_title)
            .setMessage(R.string.clear_all_message)
            .setPositiveButton(R.string.clear_history_only) { _, _ ->
                viewModel.clearHistoryOnly {
                    onHistoryCleared(getString(R.string.history_cleared))
                }
            }
            .setNeutralButton(R.string.clear_all_with_files) { _, _ ->
                viewModel.clearAllAndDeleteFiles { count ->
                    onHistoryCleared(getString(R.string.files_deleted_count, count))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupFilterChips() {
        val chips = listOf(binding.chipAll, binding.chipTikTok, binding.chipTwitter, binding.chipInstagram)
        val platforms = listOf("all", PlatformDetector.TIKTOK, PlatformDetector.TWITTER, PlatformDetector.INSTAGRAM)
        chips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                viewModel.setFilter(platforms[index])
                historyAdapter.resetAnimationState()
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

    private fun setupSearchWatcher() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchQuery(s?.toString() ?: "")
                historyAdapter.resetAnimationState()
            }
        })
    }

    private fun applyChipSelected(chip: TextView, platform: String) {
        val r = resources.displayMetrics.density
        when (platform) {
            PlatformDetector.INSTAGRAM -> {
                chip.background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.parseColor("#833AB4"), Color.parseColor("#E1306C")),
                ).apply { cornerRadius = 100f * r }
            }
            PlatformDetector.TIKTOK -> chip.setBackgroundColor(Color.parseColor("#EE1D52"))
            PlatformDetector.TWITTER -> chip.setBackgroundColor(Color.parseColor("#1DA1F2"))
            else -> chip.setBackgroundResource(R.drawable.chip_selected_bg)
        }
        chip.setTextColor(Color.WHITE)
    }

    private fun applyChipUnselected(chip: TextView) {
        chip.setBackgroundResource(R.drawable.chip_unselected_bg)
        chip.setTextColor(getColor(R.color.colorChipText))
    }

    private fun updateChipSelectionUI(currentFilter: String) {
        val chips = listOf(binding.chipAll, binding.chipTikTok, binding.chipTwitter, binding.chipInstagram)
        val platforms = listOf("all", PlatformDetector.TIKTOK, PlatformDetector.TWITTER, PlatformDetector.INSTAGRAM)
        chips.forEachIndexed { i, chip ->
            if (platforms[i] == currentFilter) applyChipSelected(chip, platforms[i])
            else applyChipUnselected(chip)
        }
    }

    private fun updateChipCounts(stats: com.videosaver.ui.HistoryStats) {
        fun label(base: String, count: Int) = if (count > 0) "$base ($count)" else base
        binding.chipAll.text = label(getString(R.string.chip_all), stats.totalCount)
        binding.chipTikTok.text = label(getString(R.string.chip_tiktok), stats.tiktokCount)
        binding.chipTwitter.text = label(getString(R.string.chip_twitter), stats.twitterCount)
        binding.chipInstagram.text = label(getString(R.string.chip_instagram), stats.instagramCount)
    }

    private fun updateChipPlatformHighlight(theme: PlatformTheme) {
        val chips = listOf(binding.chipAll, binding.chipTikTok, binding.chipTwitter, binding.chipInstagram)
        val platforms = listOf("all", PlatformDetector.TIKTOK, PlatformDetector.TWITTER, PlatformDetector.INSTAGRAM)
        val currentFilter = viewModel.filter.value
        chips.forEachIndexed { index, chip ->
            when {
                platforms[index] == theme.platform && theme.platform != PlatformDetector.UNKNOWN -> {
                    chip.setBackgroundColor(theme.primaryColor)
                    chip.setTextColor(Color.WHITE)
                }
                platforms[index] == currentFilter -> applyChipSelected(chip, platforms[index])
                else -> applyChipUnselected(chip)
            }
        }
    }

    private fun applyPlatformTheme(theme: PlatformTheme) {
        if (theme.platform == currentTheme.platform) return
        val previous = currentTheme
        currentTheme = theme

        ValueAnimator.ofObject(ArgbEvaluator(), previous.backgroundTint, theme.backgroundTint).apply {
            duration = 400
            addUpdateListener { binding.toolbar.setBackgroundColor(it.animatedValue as Int) }
            start()
        }

        binding.platformLabel.text = if (theme.platform == PlatformDetector.UNKNOWN) "" else theme.displayName
        binding.platformLabel.setTextColor(theme.primaryColor)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            val ev = ArgbEvaluator()
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                buttonGradient.colors = intArrayOf(
                    ev.evaluate(f, previous.gradientStart, theme.gradientStart) as Int,
                    ev.evaluate(f, previous.gradientEnd, theme.gradientEnd) as Int,
                )
            }
            start()
        }

        binding.progressBar.progressTintList = ColorStateList.valueOf(theme.primaryColor)
        binding.progressSpinner.indeterminateTintList = ColorStateList.valueOf(theme.primaryColor)

        val iconTint = if (theme.platform == PlatformDetector.UNKNOWN) {
            getColor(R.color.colorTextSecondary)
        } else {
            theme.primaryColor
        }
        binding.platformIcon.clearColorFilter()
        Glide.with(this)
            .load(theme.logoResId)
            .transition(DrawableTransitionOptions.withCrossFade(250))
            .into(binding.platformIcon)
        binding.platformIcon.setColorFilter(iconTint)

        val strokePx = if (theme.platform == PlatformDetector.UNKNOWN) {
            0
        } else {
            (1.5f * resources.displayMetrics.density).toInt()
        }
        binding.inputCard.strokeWidth = strokePx
        ValueAnimator.ofObject(ArgbEvaluator(), previous.primaryColor, theme.primaryColor).apply {
            duration = 400
            addUpdateListener {
                binding.inputCard.setStrokeColor(ColorStateList.valueOf(it.animatedValue as Int))
            }
            start()
        }

        updateChipPlatformHighlight(theme)
        applyStatusBarForTheme(theme)
    }

    private fun startDownload(url: String) {
        val normalized = UrlNormalizer.normalize(url)
        val platform = PlatformDetector.detect(normalized)
        if (platform.isEmpty() || platform == PlatformDetector.UNKNOWN) {
            SnackbarHelper.showError(binding.root, getString(R.string.unsupported_url))
            return
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            SnackbarHelper.showError(binding.root, getString(R.string.storage_permission_required))
            return
        }

        val filename = FilenameUtils.forDownload(PlatformDetector.toPlatform(platform))
        binding.downloadButton.isEnabled = false
        showProgressCard(currentTheme)
        DownloadWork.enqueue(this, normalized, platform, filename)
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

    private fun playVideo(item: HistoryItem) {
        if (!item.fileExists) {
            SnackbarHelper.showError(binding.root, getString(R.string.file_not_found))
            return
        }
        if (VideoPlayerActivity.canPlay(this, item.localPath)) {
            startActivity(VideoPlayerActivity.intent(this, item.localPath))
            return
        }
        val uri = StorageHelper.openUri(this, item.localPath) ?: run {
            SnackbarHelper.showError(binding.root, getString(R.string.file_not_found))
            return
        }
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                getString(R.string.open_with),
            ),
        )
    }

    private fun shareVideo(item: HistoryItem) {
        val uri = StorageHelper.openUri(this, item.localPath)
        if (uri == null) {
            SnackbarHelper.showError(binding.root, getString(R.string.file_not_found))
            return
        }
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                getString(R.string.share_video),
            ),
        )
    }

    private fun redownload(item: HistoryItem) {
        viewModel.redownload(item)
        SnackbarHelper.showInfo(binding.root, getString(R.string.redownload_started))
    }

    private fun confirmDelete(item: HistoryItem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_video_title)
            .setMessage(getString(R.string.delete_video_message, item.filename))
            .setPositiveButton(R.string.option_delete) { _, _ ->
                viewModel.deleteItem(item)
                SnackbarHelper.showUndo(
                    binding.root,
                    getString(R.string.deleted),
                    getString(R.string.undo_delete),
                ) { viewModel.undoDelete() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun requestPermissionsIfNeeded() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            perms += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            val idx = permissions.indexOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (idx >= 0 && grantResults[idx] != PackageManager.PERMISSION_GRANTED) {
                SnackbarHelper.showError(binding.root, getString(R.string.storage_permission_required))
            }
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 100
    }
}
