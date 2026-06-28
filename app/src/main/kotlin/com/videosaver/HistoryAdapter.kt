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
import com.videosaver.util.FormatUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onPlay: (HistoryItem) -> Unit,
    private val onShare: (HistoryItem) -> Unit,
    private val onDelete: (HistoryItem) -> Unit,
    private val onRedownload: (HistoryItem) -> Unit,
) : ListAdapter<HistoryItem, HistoryAdapter.ViewHolder>(DiffCallback) {

    private var lastAnimatedPosition = -1

    companion object DiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem) =
            oldItem == newItem
    }

    inner class ViewHolder(val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            filename.text = item.filename
            date.text = formatDate(item.timestamp)

            val context = thumbnail.context
            val missing = !item.fileExists
            missingBadge.visibility = if (missing) View.VISIBLE else View.GONE
            fileSize.text = when {
                missing -> context.getString(R.string.file_missing_badge)
                item.fileSizeBytes >= 0 -> FormatUtils.formatBytes(item.fileSizeBytes)
                else -> ""
            }

            val loadTarget: Any = if (item.localPath.startsWith("content://")) {
                android.net.Uri.parse(item.localPath)
            } else {
                File(item.localPath)
            }

            Glide.with(context)
                .load(loadTarget)
                .thumbnail(0.1f)
                .transition(DrawableTransitionOptions.withCrossFade(150))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(320, 180)
                .centerCrop()
                .placeholder(R.color.colorSurfaceVariant)
                .error(R.drawable.ic_empty_download)
                .into(thumbnail)

            applyPlatformBadge(platformBadge, item.platform)

            playButton.setOnClickListener {
                if (missing) onRedownload(item) else onPlay(item)
            }
            shareButton.setOnClickListener { onShare(item) }
            shareButton.isEnabled = !missing
            shareButton.alpha = if (missing) 0.4f else 1f
            deleteButton.setOnClickListener { onDelete(item) }
            root.setOnClickListener {
                if (missing) onRedownload(item) else onPlay(item)
            }
            root.setOnLongClickListener {
                showBottomSheet(item, root.context)
                true
            }
        }

        val adapterPosition = holder.bindingAdapterPosition
        if (adapterPosition != RecyclerView.NO_POSITION && adapterPosition > lastAnimatedPosition) {
            lastAnimatedPosition = adapterPosition
            val delay = (adapterPosition * 60L).coerceAtMost(600L)
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
                    intArrayOf(Color.parseColor("#E1306C"), Color.parseColor("#F77737")),
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
            dialog.dismiss()
            if (!item.fileExists) onRedownload(item) else onPlay(item)
        }
        view.findViewById<View>(R.id.optionShare).setOnClickListener {
            dialog.dismiss()
            onShare(item)
        }
        view.findViewById<View>(R.id.optionDelete).setOnClickListener {
            dialog.dismiss()
            onDelete(item)
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
        ) {
            "Today ${timeFormat.format(Date(timestamp))}"
        } else {
            SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
