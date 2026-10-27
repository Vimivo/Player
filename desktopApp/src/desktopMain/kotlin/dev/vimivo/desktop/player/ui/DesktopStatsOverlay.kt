package dev.vimivo.desktop.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vimivo.desktop.playback.MpvPlaybackStats
import dev.vimivo.desktop.playback.MpvPlayerEngine
import kotlinx.coroutines.delay

@Composable
internal fun DesktopStatsOverlay(
    engine: MpvPlayerEngine,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var stats by remember { mutableStateOf(engine.playbackStats()) }

    LaunchedEffect(Unit) {
        while (true) {
            stats = engine.playbackStats()
            delay(500L)
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.85f),
        shadowElevation = 8.dp,
        modifier = modifier
            .padding(16.dp)
            .width(360.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Stats for Nerds",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Close Stats",
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            StatRow("Resolution", "${stats.width ?: 0} × ${stats.height ?: 0}")
            val fpsText = "${"%.2f".format(stats.fps ?: 0.0)} fps (Render: ${"%.1f".format(stats.outputFps ?: 0.0)} fps)"
            StatRow("Framerate", fpsText)
            StatRow("Video Codec", stats.videoCodec?.uppercase() ?: "Unknown")
            StatRow("Hardware Decoder", stats.hwdec?.takeIf { it != "no" } ?: "Software Decoding")
            StatRow("Video Bitrate", "${stats.videoBitrateKbps ?: 0} kbps")
            val audioSpec = "${stats.audioCodec?.uppercase() ?: "Unknown"} " +
                "(${stats.audioChannels ?: 2} ch, ${stats.audioSampleRate ?: 48000} Hz)"
            StatRow("Audio Codec", audioSpec)
            StatRow("Audio Bitrate", "${stats.audioBitrateKbps ?: 0} kbps")
            StatRow("Dropped Frames", "${stats.droppedFrames} frames")
            StatRow("Forward Buffer", "${"%.1f".format(stats.cacheDurationSeconds)} s ahead")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            fontSize = 11.sp,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
