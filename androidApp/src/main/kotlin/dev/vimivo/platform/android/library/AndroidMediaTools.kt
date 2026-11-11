@file:Suppress(
    "ArgumentListWrapping",
    "CyclomaticComplexMethod",
    "ImplicitDefaultLocale",
    "MaximumLineLength",
    "MaxLineLength",
    "NoUnusedImports",
    "TooGenericExceptionCaught",
)

package dev.vimivo.platform.android.library

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

object AndroidMediaConverter {
    /**
     * Extracts the primary audio track from a video without re-encoding (demuxing & remuxing),
     * ensuring fast lossless extraction to an .m4a / audio container.
     */
    suspend fun extractAudio(
        context: Context,
        videoUri: Uri,
        outputFileName: String = "extracted_${System.currentTimeMillis()}.m4a",
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = MediaExtractor()
            if (videoUri.scheme == "content" || videoUri.scheme == "file") {
                extractor.setDataSource(context, videoUri, null)
            } else {
                extractor.setDataSource(videoUri.toString())
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            require(audioTrackIndex != -1 && audioFormat != null) { "No audio track found in video." }

            extractor.selectTrack(audioTrackIndex)

            val outputDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "extracted_audio").apply {
                if (!exists()) mkdirs()
            }
            val sanitizedName = outputFileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val finalName = if (sanitizedName.endsWith(".m4a", ignoreCase = true)) sanitizedName else "$sanitizedName.m4a"
            val outputFile = File(outputDir, finalName)
            if (outputFile.exists()) outputFile.delete()

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val maxBufferSize = audioFormat.takeIf { it.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE) }
                ?.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) ?: (1024 * 1024)
            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    bufferInfo.size = 0
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                val sampleFlags = extractor.sampleFlags
                var bufferFlags = 0
                if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    bufferFlags = bufferFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                }
                if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                    bufferFlags = bufferFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                }
                bufferInfo.flags = bufferFlags
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()

            outputFile
        }
    }
}

/** Wi-Fi share for the phone: files sent from a browser on the same network land in the device's media library. */
class AndroidWifiTransferServer(context: Context, onReceived: (String) -> Unit) {
    private val server = LanUploadServer(
        sink = MediaStoreUploadSink(context.applicationContext, onReceived),
        logWarning = { message, error -> android.util.Log.w("VimivoWifiTransfer", message, error) },
    )

    /** Address (with the session key) to open in the other device's browser; null while not running. */
    val url: String? get() = server.url
    val startError: String? get() = server.startError

    fun start() = server.start()
    fun stop() = server.stop()
}

/**
 * Saves received media through MediaStore (Movies/Vimivo, Music/Vimivo) so it needs no storage permission and
 * appears in the library on its own. Below Android 10 it falls back to the app's own media folders.
 */
internal class MediaStoreUploadSink(
    private val context: Context,
    private val onReceived: (String) -> Unit,
) : UploadSink {
    override fun save(name: String, mimeType: String, length: Long, body: java.io.InputStream): String? {
        val isVideo = mimeType.startsWith("video/")
        val saved = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                saveToMediaStore(name, mimeType, length, isVideo, body)
            } else {
                saveToAppFolder(name, length, isVideo, body)
            }
        } catch (e: java.io.IOException) {
            android.util.Log.w("VimivoWifiTransfer", "Could not save received file", e)
            null
        }
        if (saved != null) onReceived(saved)
        return saved
    }

    @android.annotation.SuppressLint("InlinedApi")
    private fun saveToMediaStore(name: String, mimeType: String, length: Long, isVideo: Boolean, body: java.io.InputStream): String? {
        val resolver = context.contentResolver
        val collection = if (isVideo) {
            android.provider.MediaStore.Video.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val folder = if (isVideo) android.os.Environment.DIRECTORY_MOVIES else android.os.Environment.DIRECTORY_MUSIC
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "$folder/Vimivo")
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        val complete = try {
            val out = resolver.openOutputStream(uri) ?: throw java.io.IOException("No output stream")
            out.use { body.copyTo(it) == length }
        } catch (e: java.io.IOException) {
            resolver.delete(uri, null, null)
            throw e
        }
        if (!complete) {
            resolver.delete(uri, null, null)
            return null
        }
        resolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return name
    }

    private fun saveToAppFolder(name: String, length: Long, isVideo: Boolean, body: java.io.InputStream): String? {
        val type = if (isVideo) android.os.Environment.DIRECTORY_MOVIES else android.os.Environment.DIRECTORY_MUSIC
        val dir = File(context.getExternalFilesDir(type) ?: context.filesDir, "Vimivo").apply { mkdirs() }
        var target = File(dir, name)
        var n = 1
        while (target.exists()) target = File(dir, "${name.substringBeforeLast('.')} (${n++}).${name.substringAfterLast('.', "")}")
        val complete = target.outputStream().use { body.copyTo(it) == length }
        if (!complete) {
            target.delete()
            return null
        }
        return target.name
    }
}

object AndroidRingtoneHelper {
    /**
     * Sets the specified audio URI as the default phone ringtone.
     * On Android 6.0+, prompts for WRITE_SETTINGS permission if not already granted.
     */
    fun setAsRingtone(context: Context, uri: Uri): Result<Boolean> {
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (!android.provider.Settings.System.canWrite(context)) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:" + context.packageName)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return Result.success(false)
                }
            }
            android.media.RingtoneManager.setActualDefaultRingtoneUri(
                context,
                android.media.RingtoneManager.TYPE_RINGTONE,
                uri,
            )
            true
        }
    }
}
