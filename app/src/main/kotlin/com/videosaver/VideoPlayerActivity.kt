package com.videosaver

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.MediaController
import androidx.appcompat.app.AppCompatActivity
import com.videosaver.databinding.ActivityVideoPlayerBinding
import com.videosaver.storage.StorageHelper
import java.io.File

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoPlayerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val localPath = intent.getStringExtra(EXTRA_LOCAL_PATH)
        if (localPath.isNullOrBlank()) {
            finish()
            return
        }

        val uri = resolveUri(localPath)
        if (uri == null) {
            binding.errorText.visibility = View.VISIBLE
            binding.errorText.text = getString(R.string.file_not_found)
            return
        }

        binding.videoView.setMediaController(MediaController(this).also { it.setAnchorView(binding.videoView) })
        binding.videoView.setVideoURI(uri)
        binding.videoView.setOnPreparedListener { mp ->
            binding.progressBar.visibility = View.GONE
            mp.start()
        }
        binding.videoView.setOnErrorListener { _, _, _ ->
            binding.progressBar.visibility = View.GONE
            binding.errorText.visibility = View.VISIBLE
            binding.errorText.text = getString(R.string.playback_failed)
            true
        }
    }

    private fun resolveUri(localPath: String): Uri? {
        if (localPath.startsWith("content://")) return Uri.parse(localPath)
        val file = File(localPath)
        if (!file.exists()) return null
        return StorageHelper.openUri(this, localPath)
    }

    override fun onPause() {
        super.onPause()
        binding.videoView.pause()
    }

    override fun onDestroy() {
        binding.videoView.stopPlayback()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_LOCAL_PATH = "local_path"

        fun intent(context: Context, localPath: String): Intent =
            Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_LOCAL_PATH, localPath)
            }

        fun canPlay(context: Context, localPath: String): Boolean {
            if (localPath.startsWith("content://")) return true
            return File(localPath).exists()
        }
    }
}
