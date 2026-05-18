package com.videosaver

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File

class SettingsBottomSheet : BottomSheetDialogFragment() {

    private val prefs by lazy {
        requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        requireContext().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.edit().putString("download_path", uri.toString()).apply()
        view?.findViewById<TextView>(R.id.saveLocationPath)?.text = uri.lastPathSegment ?: "Custom"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupThemeToggle(view)
        setupQualityToggle(view)
        setupWatermarkSwitch(view)
        setupStorageSection(view)
        updateStats(view)
    }

    private fun setupThemeToggle(view: View) {
        val btnLight = view.findViewById<TextView>(R.id.btnThemeLight)
        val btnSystem = view.findViewById<TextView>(R.id.btnThemeSystem)
        val btnDark = view.findViewById<TextView>(R.id.btnThemeDark)

        val currentMode = ThemeManager.getSavedMode(requireContext())
        updateThemeButtons(btnLight, btnSystem, btnDark, currentMode)

        btnLight.setOnClickListener { applyMode(ThemeManager.MODE_LIGHT, btnLight, btnSystem, btnDark) }
        btnSystem.setOnClickListener { applyMode(ThemeManager.MODE_SYSTEM, btnLight, btnSystem, btnDark) }
        btnDark.setOnClickListener { applyMode(ThemeManager.MODE_DARK, btnLight, btnSystem, btnDark) }
    }

    private fun updateThemeButtons(light: TextView, system: TextView, dark: TextView, mode: String) {
        listOf(light to ThemeManager.MODE_LIGHT, system to ThemeManager.MODE_SYSTEM, dark to ThemeManager.MODE_DARK)
            .forEach { (btn, btnMode) ->
                if (btnMode == mode) {
                    btn.setBackgroundResource(R.drawable.chip_selected_bg)
                    btn.setTextColor(requireContext().getColor(android.R.color.white))
                } else {
                    btn.setBackgroundResource(R.drawable.chip_unselected_bg)
                    btn.setTextColor(requireContext().getColor(R.color.colorTextSecondary))
                }
            }
    }

    private fun applyMode(mode: String, light: TextView, system: TextView, dark: TextView) {
        ThemeManager.saveMode(requireContext(), mode)
        ThemeManager.applyTheme(mode)
        updateThemeButtons(light, system, dark, mode)
        dismiss()
        val activity = requireActivity()
        activity.recreate()
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun setupQualityToggle(view: View) {
        val btnHD = view.findViewById<TextView>(R.id.btnQualityHD)
        val btnSD = view.findViewById<TextView>(R.id.btnQualitySD)
        val currentQuality = prefs.getString("download_quality", "hd") ?: "hd"
        updateQualityButtons(btnHD, btnSD, currentQuality)

        btnHD.setOnClickListener {
            prefs.edit().putString("download_quality", "hd").apply()
            updateQualityButtons(btnHD, btnSD, "hd")
        }
        btnSD.setOnClickListener {
            prefs.edit().putString("download_quality", "sd").apply()
            updateQualityButtons(btnHD, btnSD, "sd")
        }
    }

    private fun updateQualityButtons(hd: TextView, sd: TextView, quality: String) {
        val ctx = requireContext()
        if (quality == "hd") {
            hd.setBackgroundResource(R.drawable.chip_selected_bg)
            hd.setTextColor(ctx.getColor(android.R.color.white))
            sd.setBackgroundResource(R.drawable.chip_unselected_bg)
            sd.setTextColor(ctx.getColor(R.color.colorTextSecondary))
        } else {
            sd.setBackgroundResource(R.drawable.chip_selected_bg)
            sd.setTextColor(ctx.getColor(android.R.color.white))
            hd.setBackgroundResource(R.drawable.chip_unselected_bg)
            hd.setTextColor(ctx.getColor(R.color.colorTextSecondary))
        }
    }

    private fun setupWatermarkSwitch(view: View) {
        val switch = view.findViewById<SwitchCompat>(R.id.switchWatermark)
        switch.isChecked = prefs.getBoolean("tiktok_remove_watermark", true)
        switch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("tiktok_remove_watermark", checked).apply()
        }
    }

    private fun setupStorageSection(view: View) {
        view.findViewById<View>(R.id.rowSaveLocation).setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        val savedPath = prefs.getString("download_path", null)
        if (savedPath != null) {
            view.findViewById<TextView>(R.id.saveLocationPath)?.text =
                Uri.parse(savedPath).lastPathSegment ?: "Custom"
        }

        view.findViewById<TextView>(R.id.btnClearHistory).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear All History")
                .setMessage("Remove all download history? This won't delete the video files.")
                .setPositiveButton("Clear") { _, _ ->
                    requireContext().getSharedPreferences("videosaver", Context.MODE_PRIVATE)
                        .edit().putString("download_history", "[]").apply()
                    (requireActivity() as? MainActivity)?.clearAllHistoryExternal()
                    updateStats(view)
                    dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        view.findViewById<TextView>(R.id.btnClearCache).setOnClickListener {
            Glide.get(requireContext()).clearMemory()
            Thread { Glide.get(requireContext()).clearDiskCache() }.start()
            SnackbarHelper.showInfo(requireActivity().window.decorView, "Cache cleared")
        }
    }

    private fun updateStats(view: View) {
        val historyPrefs = requireContext().getSharedPreferences("videosaver", Context.MODE_PRIVATE)
        val rawJson = historyPrefs.getString("download_history", "[]") ?: "[]"
        val array = org.json.JSONArray(rawJson)
        var totalBytes = 0L
        for (i in 0 until array.length()) {
            val path = array.getJSONObject(i).optString("localPath")
            val f = File(path)
            if (f.exists()) totalBytes += f.length()
        }
        val size = formatBytes(totalBytes)
        view.findViewById<TextView>(R.id.txtStorageStats)?.text = "${array.length()} videos · $size"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        bytes < 1024L * 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }

    override fun getTheme() = R.style.BottomSheetTheme
}
