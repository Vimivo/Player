package dev.vimivo.desktop.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vimivo.desktop.platform.DesktopPlatform

@Composable
internal fun DesktopCaptureDeviceDialog(
    onOpenCapture: (uri: String, title: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val platform = remember { DesktopPlatform.detect() }

    var customUri by remember {
        mutableStateOf(
            when (platform) {
                DesktopPlatform.MACOS -> "avfoundation://0"
                DesktopPlatform.WINDOWS -> "dshow://video=0"
                DesktopPlatform.LINUX -> "v4l2:///dev/video0"
                DesktopPlatform.UNKNOWN -> "avfoundation://0"
            }
        )
    }

    val defaultScreenUri = when (platform) {
        DesktopPlatform.MACOS -> "avfoundation://default"
        DesktopPlatform.WINDOWS -> "gdigrab://desktop"
        DesktopPlatform.LINUX -> "lavfi://[x11grab]"
        DesktopPlatform.UNKNOWN -> "avfoundation://default"
    }

    val defaultWebcamUri = when (platform) {
        DesktopPlatform.MACOS -> "avfoundation://0"
        DesktopPlatform.WINDOWS -> "dshow://video=0"
        DesktopPlatform.LINUX -> "v4l2:///dev/video0"
        DesktopPlatform.UNKNOWN -> "avfoundation://0"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Open Capture Device", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Screen Capture") },
                        icon = { Icon(Icons.Outlined.DesktopWindows, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Webcam / Camera") },
                        icon = { Icon(Icons.Outlined.Videocam, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }

                if (selectedTab == 0) {
                    Text(
                        "Capture and preview your desktop display in real time using native operating system capture pipeline ($defaultScreenUri).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "Open a connected webcam, USB video device, or HDMI capture card directly in Vimivo ($defaultWebcamUri).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = customUri,
                        onValueChange = { customUri = it },
                        label = { Text("Capture Device String / URI") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTab == 0) {
                        onOpenCapture(defaultScreenUri, "Screen Capture (Display)")
                    } else {
                        onOpenCapture(customUri.ifBlank { defaultWebcamUri }, "Camera Capture")
                    }
                    onDismiss()
                },
            ) {
                Text("Open Capture")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
