package dev.vimivo.desktop.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.vimivo.desktop.net.LanAccess
import dev.vimivo.desktop.player.PlayerIntent
import dev.vimivo.desktop.player.PlayerState
import dev.vimivo.playback.PlaybackStatus
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * Phone-browser remote. Reachable only with the per-session token embedded in [serverUrl],
 * bound to one private LAN address, and shut down when the dialog closes.
 */
internal class DesktopWebRemoteServer(
    private val preferredPort: Int = 8085,
    private val stateProvider: () -> PlayerState,
    private val onIntent: (PlayerIntent) -> Unit,
    private val bindAddress: InetAddress? = LanAccess.privateIpv4(),
) {
    @Volatile
    private var server: HttpServer? = null
    private val token = LanAccess.newToken()

    /** Null when the server is not running (for example no LAN address, or the bind failed). */
    @Volatile
    var serverUrl: String? = null
        private set

    /** Human-readable reason the server could not start, shown in the dialog. */
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
            startError = "Could not open a local port for the remote."
            return
        }
        created.createContext("/") { exchange -> exchange.use { route(it) } }
        created.executor = Executors.newFixedThreadPool(MAX_THREADS) { r -> Thread(r, "vimivo-web-remote").apply { isDaemon = true } }
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
        val path = exchange.requestURI.path
        val query = LanAccess.parseQuery(exchange.requestURI.rawQuery)
        val supplied = exchange.requestHeaders.getFirst(TOKEN_HEADER) ?: query["t"]
        if (!LanAccess.tokenMatches(token, supplied)) {
            respond(exchange, 403, "text/plain; charset=utf-8", "Forbidden")
            return
        }
        when {
            path == "/" || path == "/index.html" -> respond(exchange, 200, "text/html; charset=utf-8", REMOTE_HTML)
            path == "/api/status" && exchange.requestMethod == "GET" -> respond(exchange, 200, "application/json", statusJson())
            path == "/api/action" && exchange.requestMethod == "POST" -> handleAction(exchange, query)
            else -> respond(exchange, 404, "text/plain; charset=utf-8", "Not found")
        }
    }

    private fun statusJson(): String {
        val state = stateProvider()
        val media = state.playback.media
        val playing = state.playback.status == PlaybackStatus.Playing
        val volume = (state.playback.volume * 100).toInt().coerceIn(0, 100)
        return """{"title":"${LanAccess.jsonEscape(media?.title ?: "Vimivo")}",""" +
            """"artist":"${LanAccess.jsonEscape(media?.artist ?: "No media playing")}",""" +
            """"isPlaying":$playing,"positionMillis":${state.playback.positionMillis},""" +
            """"durationMillis":${state.playback.durationMillis ?: 0L},"volume":$volume,""" +
            """"fullscreen":${state.fullscreen}}"""
    }

    private fun handleAction(exchange: HttpExchange, query: Map<String, String>) {
        val value = query["val"]
        val handled = when (query["cmd"]) {
            "play_pause" -> { onIntent(PlayerIntent.TogglePlayback); true }
            "next" -> { onIntent(PlayerIntent.Next); true }
            "previous" -> { onIntent(PlayerIntent.Previous); true }
            "fullscreen" -> { onIntent(PlayerIntent.SetFullscreen(!stateProvider().fullscreen)); true }
            "seek" -> value?.toLongOrNull()?.takeIf { it >= 0 }?.let { onIntent(PlayerIntent.Seek(it)); true } ?: false
            "volume" -> value?.toFloatOrNull()?.takeIf { it.isFinite() }
                ?.let { onIntent(PlayerIntent.SetVolume((it / 100f).coerceIn(0f, 1f))); true } ?: false
            else -> false
        }
        if (handled) respond(exchange, 200, "application/json", """{"ok":true}""")
        else respond(exchange, 400, "application/json", """{"ok":false}""")
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

        private val REMOTE_HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
              <title>Vimivo Web Remote</title>
              <style>
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
                body { background: #0f0f11; color: #fff; min-height: 100vh; display: flex; flex-direction: column; justify-content: center; align-items: center; padding: 20px; }
                .card { width: 100%; max-width: 400px; background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.1); border-radius: 24px; padding: 24px; backdrop-filter: blur(20px); text-align: center; }
                .title { font-size: 20px; font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-bottom: 6px; }
                .artist { font-size: 14px; color: #a1a1aa; margin-bottom: 24px; }
                .timeline { width: 100%; margin-bottom: 8px; }
                .slider { width: 100%; accent-color: #6366f1; height: 6px; border-radius: 3px; outline: none; }
                .times { display: flex; justify-content: space-between; font-size: 12px; color: #71717a; margin-bottom: 24px; }
                .controls { display: flex; justify-content: center; align-items: center; gap: 20px; margin-bottom: 24px; }
                button { background: rgba(255,255,255,0.1); border: none; color: #fff; border-radius: 50%; width: 52px; height: 52px; font-size: 20px; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: background 0.2s; }
                button:active { background: rgba(255,255,255,0.25); transform: scale(0.96); }
                button.play { width: 68px; height: 68px; font-size: 28px; background: #6366f1; }
                .vol-row { display: flex; align-items: center; gap: 12px; margin-top: 10px; font-size: 14px; color: #a1a1aa; }
                .vol-row input { flex: 1; }
              </style>
            </head>
            <body>
              <div class="card">
                <div class="title" id="track-title">Connecting…</div>
                <div class="artist" id="track-artist">Vimivo Desktop</div>
                <div class="timeline">
                  <input type="range" class="slider" id="seek-bar" min="0" max="100" value="0">
                </div>
                <div class="times">
                  <span id="cur-time">0:00</span>
                  <span id="dur-time">0:00</span>
                </div>
                <div class="controls">
                  <button onclick="action('previous')">⏮</button>
                  <button class="play" id="play-btn" onclick="action('play_pause')">▶</button>
                  <button onclick="action('next')">⏭</button>
                </div>
                <div class="vol-row">
                  <span>🔈</span>
                  <input type="range" class="slider" id="vol-bar" min="0" max="100" value="100" oninput="setVol(this.value)">
                  <span>🔊</span>
                </div>
              </div>
              <script>
                function formatTime(ms) {
                  const s = Math.floor(ms / 1000);
                  const m = Math.floor(s / 60);
                  const rem = s % 60;
                  return m + ":" + (rem < 10 ? "0" : "") + rem;
                }
                const token = new URLSearchParams(location.search).get('t') || '';
                const headers = { 'X-Vimivo-Token': token };
                function action(cmd, val) {
                  fetch('/api/action?cmd=' + cmd + (val !== undefined ? '&val=' + val : ''), { method: 'POST', headers });
                }
                function setVol(val) { action('volume', val); }
                document.getElementById('seek-bar').addEventListener('change', function() {
                  action('seek', this.value);
                });
                async function sync() {
                  try {
                    const res = await fetch('/api/status', { headers });
                    const d = await res.json();
                    document.getElementById('track-title').innerText = d.title;
                    document.getElementById('track-artist').innerText = d.artist;
                    document.getElementById('play-btn').innerText = d.isPlaying ? '⏸' : '▶';
                    document.getElementById('cur-time').innerText = formatTime(d.positionMillis);
                    document.getElementById('dur-time').innerText = formatTime(d.durationMillis);
                    const seek = document.getElementById('seek-bar');
                    seek.max = d.durationMillis;
                    seek.value = d.positionMillis;
                    document.getElementById('vol-bar').value = d.volume;
                  } catch (e) {}
                }
                setInterval(sync, 500);
                sync();
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}

@Composable
internal fun DesktopWebRemoteDialog(
    stateProvider: () -> PlayerState,
    onIntent: (PlayerIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    val latestState by rememberUpdatedState(stateProvider)
    val latestIntent by rememberUpdatedState(onIntent)
    val server = remember { DesktopWebRemoteServer(stateProvider = { latestState() }, onIntent = { latestIntent(it) }) }
    var serverUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

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
                Icon(Icons.Outlined.PhoneIphone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Web Remote Control", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Control Vimivo playback from your smartphone or tablet browser on the same Wi-Fi network.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CellTower, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("Open on your mobile device:", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            serverUrl ?: error.orEmpty(),
                            style = if (serverUrl != null) {
                                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = if (serverUrl != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            enabled = serverUrl != null,
                            onClick = {
                                val sel = StringSelection(serverUrl.orEmpty())
                                Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                                copied = true
                            },
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (copied) "Copied!" else "Copy Address")
                        }
                    }
                }

                Text(
                    "The link contains a one-time key. Anyone who has it can control playback until you close this window, so only share it with people you trust. Nothing leaves your local network.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}
