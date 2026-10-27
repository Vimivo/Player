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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.vimivo.desktop.player.PlayerIntent
import dev.vimivo.desktop.player.PlayerState
import dev.vimivo.playback.PlaybackStatus
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

internal class DesktopWebRemoteServer(
    private val port: Int = 8085,
    private val stateProvider: () -> PlayerState,
    private val onIntent: (PlayerIntent) -> Unit,
) {
    @Volatile
    private var server: HttpServer? = null

    val serverUrl: String
        get() = "http://${getLocalIpAddress()}:$port"

    fun start() {
        if (server != null) return
        runCatching {
            server = HttpServer.create(InetSocketAddress(port), 0).apply {
                createContext("/") { exchange ->
                    when (exchange.requestURI.path) {
                        "/", "/index.html" -> handleIndex(exchange)
                        "/api/status" -> handleStatus(exchange)
                        "/api/action" -> handleAction(exchange)
                        else -> {
                            exchange.sendResponseHeaders(404, -1)
                            exchange.close()
                        }
                    }
                }
                executor = null
                start()
            }
        }
    }

    fun stop() {
        runCatching {
            server?.stop(0)
            server = null
        }
    }

    private fun handleIndex(exchange: HttpExchange) {
        val html = REMOTE_HTML
        val bytes = html.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun handleStatus(exchange: HttpExchange) {
        val state = stateProvider()
        val media = state.playback.media
        val playing = state.playback.status == PlaybackStatus.Playing
        val pos = state.playback.positionMillis
        val dur = state.playback.durationMillis ?: 0L
        val vol = (state.playback.volume * 100).toInt()

        val json = """
            {
                "title": "${(media?.title ?: "Vimivo").replace("\"", "\\\"")}",
                "artist": "${(media?.artist ?: "No media playing").replace("\"", "\\\"")}",
                "isPlaying": $playing,
                "positionMillis": $pos,
                "durationMillis": $dur,
                "volume": $vol,
                "fullscreen": ${state.fullscreen}
            }
        """.trimIndent()

        val bytes = json.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun handleAction(exchange: HttpExchange) {
        val query = exchange.requestURI.query.orEmpty()
        val params = query.split('&').associate {
            val parts = it.split('=')
            parts[0] to parts.getOrElse(1) { "" }
        }

        when (params["cmd"]) {
            "play_pause" -> onIntent(PlayerIntent.TogglePlayback)
            "next" -> onIntent(PlayerIntent.Next)
            "previous" -> onIntent(PlayerIntent.Previous)
            "fullscreen" -> onIntent(PlayerIntent.SetFullscreen(!stateProvider().fullscreen))
            "seek" -> {
                val ms = params["val"]?.toLongOrNull() ?: 0L
                onIntent(PlayerIntent.Seek(ms))
            }
            "volume" -> {
                val v = (params["val"]?.toFloatOrNull() ?: 100f) / 100f
                onIntent(PlayerIntent.SetVolume(v))
            }
        }

        val res = """{"ok": true}"""
        val bytes = res.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
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
                    if (!addr.isLoopbackAddress && addr is InetAddress && addr.hostAddress.contains('.')) {
                        return addr.hostAddress
                    }
                }
            }
            "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }

    companion object {
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
                function action(cmd, val) {
                  fetch('/api/action?cmd=' + cmd + (val !== undefined ? '&val=' + val : ''), { method: 'POST' });
                }
                function setVol(val) { action('volume', val); }
                document.getElementById('seek-bar').addEventListener('change', function() {
                  action('seek', this.value);
                });
                async function sync() {
                  try {
                    const res = await fetch('/api/status');
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
    val server = remember { DesktopWebRemoteServer(8085, stateProvider, onIntent) }
    var serverUrl by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

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
                            serverUrl,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val sel = StringSelection(serverUrl)
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
                    "No apps or accounts required. Play/pause, skip, seek, and adjust volume in real time.",
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
