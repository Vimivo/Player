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
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.vimivo.desktop.net.LanAccess
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors

/**
 * Receives media files from a phone browser over the local network.
 *
 * Reachable only with the per-session token in [serverUrl]; bound to one private LAN
 * address; accepts only known media extensions, caps the size and writes into [destination].
 */
internal class DesktopWifiTransferServer(
    private val destination: Path,
    private val onFileReceived: (Path) -> Unit,
    private val preferredPort: Int = 8080,
    private val bindAddress: InetAddress? = LanAccess.privateIpv4(),
    private val maxBytes: Long = MAX_UPLOAD_BYTES,
) {
    @Volatile
    private var server: HttpServer? = null
    private val token = LanAccess.newToken()
    private val logger = System.getLogger("vimivo.wifi-transfer")

    @Volatile
    var serverUrl: String? = null
        private set

    @Volatile
    var startError: String? = null
        private set

    fun start() {
        if (server != null) return
        val address = bindAddress ?: run {
            startError = "No local network connection found. Connect to Wi-Fi or Ethernet and reopen this window."
            return
        }
        val created = bind(address, preferredPort) ?: bind(address, 0)
        if (created == null) {
            startError = "Could not open a local port for sharing."
            return
        }
        created.createContext("/") { exchange -> exchange.use { route(it) } }
        created.executor = Executors.newFixedThreadPool(MAX_THREADS) { r -> Thread(r, "vimivo-wifi-transfer").apply { isDaemon = true } }
        created.start()
        server = created
        serverUrl = "http://${address.hostAddress}:${created.address.port}/?t=$token"
    }

    fun stop() {
        val s = server ?: return
        server = null
        serverUrl = null
        s.stop(0)
        (s.executor as? java.util.concurrent.ExecutorService)?.shutdownNow()
    }

    private fun bind(address: InetAddress, port: Int): HttpServer? =
        try {
            HttpServer.create(InetSocketAddress(address, port), 0)
        } catch (_: java.net.BindException) {
            null
        }

    private fun route(exchange: HttpExchange) {
        val query = LanAccess.parseQuery(exchange.requestURI.rawQuery)
        val supplied = exchange.requestHeaders.getFirst(TOKEN_HEADER) ?: query["t"]
        if (!LanAccess.tokenMatches(token, supplied)) {
            respond(exchange, 403, "text/plain; charset=utf-8", "Forbidden")
            return
        }
        when {
            exchange.requestURI.path == "/" && exchange.requestMethod == "GET" ->
                respond(exchange, 200, "text/html; charset=utf-8", UPLOAD_HTML)
            exchange.requestURI.path == "/upload" && exchange.requestMethod == "POST" -> handleUpload(exchange, query)
            else -> respond(exchange, 404, "text/plain; charset=utf-8", "Not found")
        }
    }

    private fun handleUpload(exchange: HttpExchange, query: Map<String, String>) {
        val name = sanitizeFileName(query["name"].orEmpty())
        if (name == null) {
            respond(exchange, 415, "text/plain; charset=utf-8", "Unsupported file type")
            return
        }
        val length = exchange.requestHeaders.getFirst("Content-Length")?.toLongOrNull()
        if (length == null || length < 0) {
            respond(exchange, 411, "text/plain; charset=utf-8", "Length required")
            return
        }
        if (length > maxBytes) {
            respond(exchange, 413, "text/plain; charset=utf-8", "File too large")
            return
        }
        val saved = try {
            Files.createDirectories(destination)
            receive(exchange.requestBody, length, name)
        } catch (e: IOException) {
            logger.log(System.Logger.Level.WARNING, "Wi-Fi upload could not be saved", e)
            respond(exchange, 500, "text/plain; charset=utf-8", "Could not save file")
            return
        }
        if (saved == null) {
            respond(exchange, 400, "text/plain; charset=utf-8", "Incomplete upload")
            return
        }
        onFileReceived(saved)
        respond(exchange, 200, "application/json", """{"ok":true,"name":"${LanAccess.jsonEscape(saved.fileName.toString())}"}""")
    }

    /** Streams exactly [length] bytes to a temp file, then atomically moves it to a unique name. */
    private fun receive(body: InputStream, length: Long, name: String): Path? {
        val partial = Files.createTempFile(destination, ".vimivo-", ".part")
        try {
            val complete = Files.newOutputStream(partial).use { out -> copyExactly(body, out, length) }
            if (!complete) return null
            val target = uniqueTarget(destination, name)
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE)
            return target
        } finally {
            Files.deleteIfExists(partial)
        }
    }

    /** Returns false if the client closed the connection before sending [length] bytes. */
    private fun copyExactly(input: InputStream, out: OutputStream, length: Long): Boolean {
        val buffer = ByteArray(COPY_BUFFER)
        var remaining = length
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) return false
            out.write(buffer, 0, read)
            remaining -= read
        }
        return true
    }

    private fun respond(exchange: HttpExchange, status: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.responseHeaders.set("Referrer-Policy", "no-referrer")
        exchange.responseHeaders.set("X-Content-Type-Options", "nosniff")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    companion object {
        private const val TOKEN_HEADER = "X-Vimivo-Token"
        private const val MAX_THREADS = 4
        private const val COPY_BUFFER = 64 * 1024
        const val MAX_UPLOAD_BYTES: Long = 16L * 1024 * 1024 * 1024

        private val ALLOWED_EXTENSIONS = setOf(
            "mp4", "m4v", "mkv", "webm", "mov", "avi", "ts", "mpg", "mpeg", "3gp", "flv", "wmv",
            "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "aiff", "alac",
            "srt", "ass", "ssa", "vtt", "sub", "lrc",
        )

        /** Returns a safe base file name, or null if the type is not an accepted media/subtitle file. */
        fun sanitizeFileName(raw: String): String? {
            val base = raw.substringAfterLast('/').substringAfterLast('\\')
                .filter { it >= ' ' && it != '\u007f' }
                .trim()
                .trimStart('.')
                .take(180)
            if (base.isEmpty()) return null
            val ext = base.substringAfterLast('.', "").lowercase()
            if (ext !in ALLOWED_EXTENSIONS) return null
            return base
        }

        /** Picks "name.ext", then "name (1).ext", ... that does not exist yet in [dir]. */
        fun uniqueTarget(dir: Path, name: String): Path {
            val stem = name.substringBeforeLast('.', name)
            val ext = name.substringAfterLast('.', "")
            var candidate = dir.resolve(name)
            var n = 1
            while (Files.exists(candidate)) {
                candidate = dir.resolve("$stem ($n).$ext")
                n++
            }
            return candidate
        }

        private val UPLOAD_HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Vimivo Wi-Fi Share</title>
              <style>
                * { box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
                body { background: #0f0f11; color: #f4f4f5; max-width: 560px; margin: 0 auto; padding: 24px 16px; }
                h1 { font-size: 22px; margin: 0 0 6px; }
                p { color: #a1a1aa; font-size: 14px; line-height: 1.5; }
                button { background: #6366f1; color: #fff; border: 0; border-radius: 12px; padding: 14px 22px; font-size: 16px; font-weight: 600; margin-top: 12px; }
                ul { list-style: none; padding: 0; margin-top: 20px; }
                li { background: rgba(255,255,255,0.06); border-radius: 12px; padding: 12px 14px; margin-bottom: 8px; font-size: 14px; word-break: break-all; }
                .ok { color: #4ade80; } .err { color: #f87171; }
              </style>
            </head>
            <body>
              <h1>Send to Vimivo</h1>
              <p>Pick videos, music or subtitles. They are copied straight to the computer over your local network.</p>
              <input type="file" id="files" multiple accept="video/*,audio/*,.mkv,.flac,.srt,.ass,.vtt,.opus" hidden>
              <button onclick="document.getElementById('files').click()">Choose files</button>
              <ul id="log"></ul>
              <script>
                const token = new URLSearchParams(location.search).get('t') || '';
                document.getElementById('files').addEventListener('change', async (e) => {
                  for (const file of e.target.files) {
                    const li = document.createElement('li');
                    li.textContent = file.name + ' — sending…';
                    document.getElementById('log').prepend(li);
                    try {
                      const res = await fetch('/upload?name=' + encodeURIComponent(file.name), {
                        method: 'POST', headers: { 'X-Vimivo-Token': token }, body: file });
                      li.textContent = file.name + (res.ok ? ' — received' : ' — failed (' + (await res.text()) + ')');
                      li.className = res.ok ? 'ok' : 'err';
                    } catch (err) { li.textContent = file.name + ' — failed'; li.className = 'err'; }
                  }
                  e.target.value = '';
                });
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}

@Composable
fun DesktopWifiTransferDialog(
    onDismiss: () -> Unit,
) {
    val destination = remember { Path.of(System.getProperty("user.home"), "Downloads", "Vimivo") }
    var received by remember { mutableStateOf<List<String>>(emptyList()) }
    val server = remember {
        DesktopWifiTransferServer(
            destination = destination,
            onFileReceived = { path -> received = listOf(path.fileName.toString()) + received },
        )
    }
    var serverUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        server.start()
        serverUrl = server.serverUrl
        error = server.startError
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
                        Text("Open this address in a browser on the same Wi-Fi:", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            serverUrl ?: error.orEmpty(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (serverUrl != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        serverUrl?.let {
                            Spacer(Modifier.height(8.dp))
                            dev.vimivo.desktop.ui.QrCode(it)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Files are saved to $destination. The link has a one-time key and stops working when you close this window.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (received.isNotEmpty()) {
                    Text("Received", style = MaterialTheme.typography.labelLarge)
                    received.take(5).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
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
