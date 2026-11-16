package com.chargetone.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 提示音文件存储。
 *
 * 用户通过 SAF 选择音频后，文件会被**复制**进应用私有目录（filesDir/tones/），
 * 这样即使原文件被删除、移动，或重启后 URI 授权失效，提示音仍然可用。
 */
class ToneStore(private val context: Context) {

    private val tonesDir: File
        get() = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    fun resolve(token: String?): File? {
        if (token.isNullOrBlank()) return null
        val file = File(tonesDir, token)
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * 复制选中的音频到私有目录，返回用于保存到设置里的文件名。
     * 失败返回 null。
     */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri)
        val extension = pickExtension(displayName, context.contentResolver.getType(uri))
        val token = buildToken(displayName, extension)
        val target = File(tonesDir, token)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: run {
                Log.w(TAG, "无法打开输入流: $uri")
                return@withContext null
            }
            if (target.length() <= 0L) {
                target.delete()
                return@withContext null
            }
            token
        } catch (e: IOException) {
            Log.w(TAG, "复制音频失败: $uri", e)
            target.delete()
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "没有读取该音频的权限: $uri", e)
            target.delete()
            null
        }
    }

    fun delete(token: String?) {
        if (token.isNullOrBlank()) return
        File(tonesDir, token).delete()
    }

    /** 用户可读的文件名，形如「我的提示音_a1b2c3.mp3」 */
    fun displayName(token: String?): String? {
        if (token.isNullOrBlank()) return null
        val base = token.substringBeforeLast('.', token)
        val dot = token.lastIndexOf('.')
        val ext = if (dot >= 0) token.substring(dot) else ""
        return base.substringBeforeLast('_', base) + ext
    }

    private fun buildToken(displayName: String?, extension: String): String {
        val safeBase = (displayName ?: DEFAULT_NAME)
            .substringBeforeLast('.', displayName ?: DEFAULT_NAME)
            .replace(UNSAFE_CHARS, "_")
            .trim()
            .take(32)
            .ifBlank { DEFAULT_NAME }
        val suffix = UUID.randomUUID().toString().take(6)
        return "${safeBase}_$suffix.$extension"
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
    }.getOrNull()

    private fun pickExtension(displayName: String?, mimeType: String?): String {
        val fromName = displayName?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it in SUPPORTED_EXTENSIONS }
        if (fromName != null) return fromName
        val fromMime = mimeType?.let { MIME_EXTENSIONS[it] }
        return fromMime ?: DEFAULT_EXTENSION
    }

    private companion object {
        const val TAG = "ChargeTone/ToneStore"
        const val DIR_NAME = "tones"
        const val DEFAULT_NAME = "charge_tone"
        const val DEFAULT_EXTENSION = "audio"

        val UNSAFE_CHARS = Regex("[\\\\/:*?\"<>|\\s]+")
        val SUPPORTED_EXTENSIONS = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "opus", "amr", "mid")
        val MIME_EXTENSIONS = mapOf(
            "audio/mpeg" to "mp3",
            "audio/mp3" to "mp3",
            "audio/wav" to "wav",
            "audio/x-wav" to "wav",
            "audio/ogg" to "ogg",
            "audio/mp4" to "m4a",
            "audio/aac" to "aac",
            "audio/flac" to "flac",
            "audio/x-flac" to "flac",
            "audio/opus" to "opus",
            "audio/amr" to "amr",
            "audio/midi" to "mid",
        )
    }
}
