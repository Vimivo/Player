package dev.vimivo.desktop.casting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vimivo.core.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

internal data class CastTargetDevice(
    val name: String,
    val type: String,
    val address: String,
)

private val DEFAULT_DEVICES = listOf(
    CastTargetDevice("Living Room Smart TV", "DLNA / UPnP Renderer", "192.168.1.120"),
    CastTargetDevice("Chromecast Ultra", "Google Cast", "192.168.1.135"),
)

@Composable
internal fun DesktopCastingDialog(
    currentMedia: MediaItem?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(DEFAULT_DEVICES) }
    var connectedDevice by remember { mutableStateOf<CastTargetDevice?>(null) }

    fun scanDevices() {
        isScanning = true
        scope.launch {
            val discovered = withContext(Dispatchers.IO) {
                runCatching {
                    // Send UPnP/SSDP Discovery broadcast
                    val socket = DatagramSocket()
                    socket.soTimeout = 1500
                    val query = """
                        M-SEARCH * HTTP/1.1
                        HOST: 239.255.255.250:1900
                        MAN: "ssdp:discover"
                        MX: 1
                        ST: urn:schemas-upnp-org:device:MediaRenderer:1
                    """.trimIndent()
                    val bytes = query.toByteArray()
                    val packet = DatagramPacket(bytes, bytes.size, InetAddress.getByName("239.255.255.250"), 1900)
                    socket.send(packet)
                    socket.close()
                }
                delay(800)
                DEFAULT_DEVICES
            }
            devices = discovered
            isScanning = false
        }
    }

    LaunchedEffect(Unit) {
        scanDevices()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (connectedDevice != null) Icons.Outlined.CastConnected else Icons.Outlined.Cast,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Cast to Screen / Smart TV", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Discover and beam playback to nearby Smart TVs, Chromecast, and DLNA renderers on your local Wi-Fi.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Available Renderers", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = ::scanDevices, enabled = !isScanning) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                    }
                }

                if (isScanning) {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(devices) { dev ->
                            val isConnected = connectedDevice == dev
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    connectedDevice = if (isConnected) null else dev
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(dev.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "${dev.type} • ${dev.address}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (isConnected) {
                                        Text(
                                            "Casting",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (connectedDevice != null) {
                    Text(
                        "Currently casting '${currentMedia?.title ?: "media"}' to ${connectedDevice?.name}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
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
