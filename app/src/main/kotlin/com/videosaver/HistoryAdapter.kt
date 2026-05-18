package com.videosaver

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.videosaver.databinding.ItemHistoryBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onPlay: (HistoryItem) -> Unit,
    private val onShare: (HistoryItem) -> Unit,
    private val onDelete: (HistoryItem) -> Unit,
) : ListAdapter<HistoryItem, HistoryAdapter.ViewHolder>(DiffCallback) {

    private var lastAnimatedPosition = -1

    companion object DiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem) =
            oldItem.timestamp == newItem.timestamp

        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem) =
            oldItem == newItem
    }

    inner class ViewHolder(val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            filename.text = item.filename
            date.text = formatDate(item.timestamp)

            val file = File(item.localPath)
            fileSize.text = if (file.exists()) formatFileSize(file.length()) else ""

            Glide.with(thumbnail.context)
                .load(file)
                .thumbnail(0.1f)
                .transition(DrawableTransitionOptions.withCrossFade(150))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(320, 180)
                .centerCrop()
                .placeholder(R.color.colorSurfaceVariant)
                .error(R.drawable.ic_empty_download)
                .into(thumbnail)

            applyPlatformBadge(platformBadge, item.platform)

            playButton.setOnClickListener { onPlay(item) }
            root.setOnClickListener { onPlay(item) }
            root.setOnLongClickListener {
                showBottomSheet(item, root.context)
                true
            }
        }

        if (position > lastAnimatedPosition) {
            lastAnimatedPosition = position
            val delay = (position * 60L).coerceAtMost(600L)
            holder.itemView.alpha = 0f
            holder.itemView.translationY = 30f
            holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(250)
                .setStartDelay(delay)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    fun resetAnimationState() {
        lastAnimatedPosition = -1
    }

    private fun applyPlatformBadge(badge: android.widget.TextView, platform: String) {
        val density = badge.context.resources.displayMetrics.density
        val radius = 50f * density
        when (platform) {
            "tiktok" -> {
                badge.text = "TikTok"
                badge.visibility = View.VISIBLE
                badge.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#010101"))
                    cornerRadius = radius
                }
            }
            "twitter" -> {
                badge.text = "Twitter"
                badge.visibility = View.VISIBLE
                badge.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1DA1F2"))
                    cornerRadius = radius
                }
            }
            "instagram" -> {
                badge.text = "Instagram"
                badge.visibility = View.VISIBLE
                badge.background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.parseColor("#E1306C"), Color.parseColor("#F77737"))
                ).apply { cornerRadius = radius }
            }
            else -> badge.visibility = View.GONE
        }
    }

    private fun showBottomSheet(item: HistoryItem, context: Context) {
        val dialog = BottomSheetDialog(context, R.style.BottomSheetTheme)
        val view = LayoutInflater.from(context)
            .inflate(R.layout.layout_history_bottom_sheet, null)

        view.findViewById<View>(R.id.optionPlay).setOnClickListener {
            dialog.dismiss(); onPlay(item)
        }
        view.findViewById<View>(R.id.optionShare).setOnClickListener {
            dialog.dismiss(); onShare(item)
        }
        view.findViewById<View>(R.id.optionDelete).setOnClickListener {
            dialog.dismiss(); onDelete(item)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun formatDate(timestamp: Long): String {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        return if (now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
        ) "Today ${timeFormat.format(Date(timestamp))}"
        else SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(timestamp))
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
