package com.videosaver.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.videosaver.prefs.AppPrefs
import java.io.File
import java.io.InputStream

object StorageHelper {

    private const val MIME_MP4 = "video/mp4"

    sealed class OutputTarget {
        abstract val displayName: String

        data class FilePath(val file: File) : OutputTarget() {
            override val displayName: String get() = file.name
        }

        data class DocumentUri(val uri: Uri) : OutputTarget() {
            override val displayName: String get() = uri.lastPathSegment ?: "video.mp4"
        }
    }

    data class ResolveResult(
        val target: OutputTarget,
        val usedFallbackFolder: Boolean = false,
    )

    fun resolveOutputTarget(context: Context, filename: String): ResolveResult {
        val treeUri = AppPrefs.getDownloadTreeUri(context)
        if (treeUri != null) {
            val tree = DocumentFile.fromTreeUri(context, treeUri)
            val doc = tree?.createFile(MIME_MP4, filename)
            if (doc != null) {
                return ResolveResult(OutputTarget.DocumentUri(doc.uri), usedFallbackFolder = false)
            }
        }
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloads.mkdirs()
        return ResolveResult(
            OutputTarget.FilePath(File(downloads, filename)),
            usedFallbackFolder = treeUri != null,
        )
    }

    fun deleteTarget(target: OutputTarget) {
        when (target) {
            is OutputTarget.FilePath -> target.file.takeIf { it.exists() }?.delete()
            is OutputTarget.DocumentUri -> Unit // caller uses delete(context, uri)
        }
    }

    fun deleteTarget(context: Context, target: OutputTarget) {
        when (target) {
            is OutputTarget.FilePath -> target.file.takeIf { it.exists() }?.delete()
            is OutputTarget.DocumentUri ->
                DocumentFile.fromSingleUri(context, target.uri)?.delete()
        }
    }

    fun exists(context: Context, localPath: String): Boolean = when {
        localPath.startsWith("content://") ->
            DocumentFile.fromSingleUri(context, Uri.parse(localPath))?.exists() == true
        else -> File(localPath).exists()
    }

    fun fileSize(context: Context, localPath: String): Long = when {
        localPath.startsWith("content://") -> {
            val uri = Uri.parse(localPath)
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else 0L
                } else 0L
            } ?: 0L
        }
        else -> File(localPath).takeIf { it.exists() }?.length() ?: 0L
    }

    fun openUri(context: Context, localPath: String): Uri? {
        if (localPath.startsWith("content://")) return Uri.parse(localPath)
        val file = File(localPath)
        if (!file.exists()) return null
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun delete(context: Context, localPath: String): Boolean = when {
        localPath.startsWith("content://") ->
            DocumentFile.fromSingleUri(context, Uri.parse(localPath))?.delete() == true
        else -> File(localPath).takeIf { it.exists() }?.delete() == true
    }

    fun folderDisplayName(context: Context): String {
        val treeUri = AppPrefs.getDownloadTreeUri(context) ?: return context.getString(com.videosaver.R.string.default_downloads)
        val tree = DocumentFile.fromTreeUri(context, treeUri)
        return tree?.name ?: treeUri.lastPathSegment ?: context.getString(com.videosaver.R.string.custom_folder)
    }
}
