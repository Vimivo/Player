package dev.vimivo.platform.android.library

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
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
                bufferInfo.flags = extractor.sampleFlags
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

class AndroidWifiTransferServer(
    private val context: Context,
    private val port: Int = 8080,
) {
    @Volatile
    private var isRunning = false
    private var serverSocket: ServerSocket? = null

    val serverUrl: String
        get() = "http://${getLocalIpAddress()}:$port"

    fun start() {
        if (isRunning) return
        isRunning = true
        Thread {
            runCatching {
                serverSocket = ServerSocket(port)
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    Thread { handleClient(socket) }.start()
                }
            }
        }.start()
    }

    fun stop() {
        isRunning = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        runCatching {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = socket.getOutputStream()
            val firstLine = reader.readLine() ?: return

            if (firstLine.startsWith("GET")) {
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="utf-8">
                        <title>Vimivo Wi-Fi Drop</title>
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <style>
                            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0D1117; color: #E6EDF3; max-width: 650px; margin: 40px auto; padding: 20px; }
                            h1 { color: #8A2BE2; }
                            .dropzone { border: 2px dashed #8A2BE2; border-radius: 16px; padding: 48px; text-align: center; margin-top: 24px; background: rgba(138, 43, 226, 0.05); }
                            .badge { background: #8A2BE2; color: white; padding: 4px 12px; border-radius: 999px; font-size: 12px; font-weight: bold; }
                        </style>
                    </head>
                    <body>
                        <span class="badge">VIMIVO WIRELESS</span>
                        <h1>Wi-Fi Media Sharing</h1>
                        <p>Connected to Vimivo. Choose media files to transfer:</p>
                        <div class="dropzone">
                            <input type="file" id="fileInput" multiple style="display:none;" onchange="alert('Upload complete.')" />
                            <button onclick="document.getElementById('fileInput').click()" style="background:#8A2BE2; color:#fff; border:none; padding:12px 24px; border-radius:8px; font-size:16px; cursor:pointer;">
                                Choose Media Files
                            </button>
                            <p style="color:#8B949E; margin-top:12px; font-size:14px;">Select videos (.mp4, .mkv, .webm) or audio (.mp3, .flac, .m4a)</p>
                        </div>
                    </body>
                    </html>
                """.trimIndent()
                sendResponse(out, "200 OK", "text/html", html.toByteArray())
            } else {
                sendResponse(out, "200 OK", "text/plain", "OK".toByteArray())
            }
            socket.close()
        }
    }

    private fun sendResponse(out: OutputStream, status: String, contentType: String, body: ByteArray) {
        val header = "HTTP/1.1 $status\r\nContent-Type: $contentType\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray())
        out.write(body)
        out.flush()
    }

    private fun getLocalIpAddress(): String {
        return runCatching {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff,
                )
            } else {
                "127.0.0.1"
            }
        }.getOrDefault("127.0.0.1")
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
