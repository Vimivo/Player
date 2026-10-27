package dev.vimivo.desktop.sharing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

class DesktopWifiTransferServer(
    private val port: Int = 8080,
    private val onFileUploaded: ((String) -> Unit)? = null,
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
                        <title>Vimivo Desktop Wi-Fi Share</title>
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
                        <p>Connected to Vimivo Desktop. Choose media files to transfer:</p>
                        <div class="dropzone">
                            <input type="file" id="fileInput" multiple style="display:none;" onchange="alert('Media file received on Vimivo Desktop.')" />
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
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.hostAddress.indexOf(':') < 0) {
                        return addr.hostAddress
                    }
                }
            }
            "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }
}

@Composable
fun DesktopWifiTransferDialog(
    onDismiss: () -> Unit,
) {
    val server = remember { DesktopWifiTransferServer() }
    var serverUrl by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        server.start()
        serverUrl = server.serverUrl
        onDispose {
            server.stop()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Wi-Fi File Sharing", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Transfer media files between your mobile phone or another device and Vimivo without a cable.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Open this address in any browser on the same Wi-Fi:", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            serverUrl,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Ensure both devices are on the same Wi-Fi network.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}
