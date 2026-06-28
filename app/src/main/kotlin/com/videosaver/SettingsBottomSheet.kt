package com.videosaver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.videosaver.databinding.BottomSheetSettingsBinding
import com.videosaver.prefs.AppPrefs
import com.videosaver.storage.StorageHelper
import com.videosaver.ui.MainViewModel
import com.videosaver.util.FormatUtils
import kotlinx.coroutines.launch

class SettingsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        requireContext().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        AppPrefs.setDownloadTreeUri(requireContext(), uri)
        updateSaveLocationLabel()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupThemeToggle()
        setupQualityToggle()
        setupWatermarkSwitch()
        setupWifiSwitch()
        setupStorageSection()
        updateStats()
        binding.txtVersion.text = getString(R.string.version_format, BuildConfig.VERSION_NAME)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupThemeToggle() {
        val currentMode = ThemeManager.getSavedMode(requireContext())
        updateThemeButtons(currentMode)
        binding.btnThemeLight.setOnClickListener {
            applyMode(ThemeManager.MODE_LIGHT)
        }
        binding.btnThemeSystem.setOnClickListener {
            applyMode(ThemeManager.MODE_SYSTEM)
        }
        binding.btnThemeDark.setOnClickListener {
            applyMode(ThemeManager.MODE_DARK)
        }
    }

    private fun updateThemeButtons(mode: String) {
        val buttons = listOf(
            binding.btnThemeLight to ThemeManager.MODE_LIGHT,
            binding.btnThemeSystem to ThemeManager.MODE_SYSTEM,
            binding.btnThemeDark to ThemeManager.MODE_DARK,
        )
        buttons.forEach { (btn, btnMode) ->
            if (btnMode == mode) {
                btn.setBackgroundResource(R.drawable.chip_selected_bg)
                btn.setTextColor(requireContext().getColor(android.R.color.white))
            } else {
                btn.setBackgroundResource(R.drawable.chip_unselected_bg)
                btn.setTextColor(requireContext().getColor(R.color.colorTextSecondary))
            }
        }
    }

    private fun applyMode(mode: String) {
        ThemeManager.saveMode(requireContext(), mode)
        ThemeManager.applyTheme(mode)
        dismiss()
        val activity = requireActivity()
        activity.recreate()
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun setupQualityToggle() {
        val currentQuality = AppPrefs.getDownloadQuality(requireContext())
        updateQualityButtons(currentQuality)
        binding.btnQualityHD.setOnClickListener {
            AppPrefs.setDownloadQuality(requireContext(), "hd")
            updateQualityButtons("hd")
        }
        binding.btnQualitySD.setOnClickListener {
            AppPrefs.setDownloadQuality(requireContext(), "sd")
            updateQualityButtons("sd")
        }
    }

    private fun updateQualityButtons(quality: String) {
        val ctx = requireContext()
        if (quality == "hd") {
            binding.btnQualityHD.setBackgroundResource(R.drawable.chip_selected_bg)
            binding.btnQualityHD.setTextColor(ctx.getColor(android.R.color.white))
            binding.btnQualitySD.setBackgroundResource(R.drawable.chip_unselected_bg)
            binding.btnQualitySD.setTextColor(ctx.getColor(R.color.colorTextSecondary))
        } else {
            binding.btnQualitySD.setBackgroundResource(R.drawable.chip_selected_bg)
            binding.btnQualitySD.setTextColor(ctx.getColor(android.R.color.white))
            binding.btnQualityHD.setBackgroundResource(R.drawable.chip_unselected_bg)
            binding.btnQualityHD.setTextColor(ctx.getColor(R.color.colorTextSecondary))
        }
    }

    private fun setupWatermarkSwitch() {
        binding.switchWatermark.isChecked = AppPrefs.isTikTokWatermarkRemoved(requireContext())
        binding.switchWatermark.setOnCheckedChangeListener { _, checked ->
            AppPrefs.setTikTokWatermarkRemoved(requireContext(), checked)
        }
    }

    private fun setupWifiSwitch() {
        binding.switchWifiOnly.isChecked = AppPrefs.isWifiOnly(requireContext())
        binding.switchWifiOnly.setOnCheckedChangeListener { _, checked ->
            AppPrefs.setWifiOnly(requireContext(), checked)
        }
    }

    private fun setupStorageSection() {
        updateSaveLocationLabel()

        binding.rowSaveLocation.setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        binding.btnResetSaveLocation.setOnClickListener {
            AppPrefs.clearDownloadTreeUri(requireContext())
            updateSaveLocationLabel()
            SnackbarHelper.showInfo(requireView(), getString(R.string.save_location_reset))
        }

        binding.btnClearHistory.setOnClickListener {
            showClearAllDialog()
        }

        binding.btnDeleteMissing.setOnClickListener {
            viewModel.deleteMissingEntries { removed ->
                SnackbarHelper.showInfo(
                    requireView(),
                    getString(R.string.missing_entries_removed, removed),
                )
                updateStats()
            }
        }

        binding.btnClearCache.setOnClickListener {
            Glide.get(requireContext()).clearMemory()
            Thread { Glide.get(requireContext()).clearDiskCache() }.start()
            SnackbarHelper.showInfo(requireView(), getString(R.string.cache_cleared))
        }
    }

    private fun updateSaveLocationLabel() {
        binding.saveLocationPath.text = StorageHelper.folderDisplayName(requireContext())
    }

    private fun showClearAllDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_all_title)
            .setMessage(R.string.clear_all_message)
            .setPositiveButton(R.string.clear_history_only) { _, _ ->
                viewModel.clearHistoryOnly {
                    (activity as? HistoryClearListener)?.onHistoryCleared(
                        getString(R.string.history_cleared),
                    )
                    updateStats()
                    dismiss()
                }
            }
            .setNeutralButton(R.string.clear_all_with_files) { _, _ ->
                viewModel.clearAllAndDeleteFiles { count ->
                    (activity as? HistoryClearListener)?.onHistoryCleared(
                        getString(R.string.files_deleted_count, count),
                    )
                    updateStats()
                    dismiss()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateStats() {
        lifecycleScope.launch {
            val stats = viewModel.stats.value
            binding.txtStorageStats.text = getString(
                R.string.storage_stats,
                stats.totalCount,
                FormatUtils.formatBytes(stats.totalBytes),
            )
        }
    }

    override fun getTheme() = R.style.BottomSheetTheme
}
