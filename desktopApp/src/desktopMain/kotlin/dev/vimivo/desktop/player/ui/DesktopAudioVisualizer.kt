package dev.vimivo.desktop.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.vimivo.playback.PlaybackStatus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class VisualizerStyle {
    SPECTRUM,
    WAVEFORM,
}

@Composable
fun DesktopAudioVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    initialStyle: VisualizerStyle = VisualizerStyle.SPECTRUM,
) {
    var style by remember { mutableStateOf(initialStyle) }

    val transition = rememberInfiniteTransition()
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (style) {
            VisualizerStyle.SPECTRUM -> SpectrumVisualizer(isPlaying = isPlaying, phase = phase)
            VisualizerStyle.WAVEFORM -> WaveformVisualizer(isPlaying = isPlaying, phase = phase)
        }

        // Mode switcher chip in top-right
        Surface(
            color = Color.Black.copy(alpha = 0.5f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = { style = VisualizerStyle.SPECTRUM },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Outlined.GraphicEq,
                        contentDescription = "Spectrum visualizer",
                        tint = if (style == VisualizerStyle.SPECTRUM) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { style = VisualizerStyle.WAVEFORM },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ShowChart,
                        contentDescription = "Waveform visualizer",
                        tint = if (style == VisualizerStyle.WAVEFORM) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpectrumVisualizer(
    isPlaying: Boolean,
    phase: Float,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary

    val numBars = 36

    Canvas(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp)) {
        val width = size.width
        val height = size.height
        val barWidth = (width / numBars) * 0.7f
        val gap = (width / numBars) * 0.3f

        for (i in 0 until numBars) {
            val normalizedIndex = i.toFloat() / numBars
            // Create harmonic energy distribution
            val wave1 = sin(phase * 1.5f + i * 0.35f)
            val wave2 = sin(phase * 2.2f + i * 0.6f)
            val harmonicFactor = (sin(normalizedIndex * PI.toFloat()) * 0.7f + 0.3f)
            val rawMagnitude = if (isPlaying) {
                abs(wave1 * 0.6f + wave2 * 0.4f) * harmonicFactor
            } else 0.05f

            val barHeight = (rawMagnitude * height * 0.75f).coerceIn(4f, height * 0.85f)
            val x = i * (barWidth + gap) + gap / 2f
            val y = height - barHeight

            val brush = Brush.verticalGradient(
                colors = listOf(secondaryColor, primaryColor),
                startY = y,
                endY = height,
            )

            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4f, 4f),
            )

            // Floating peak marker
            val peakY = (y - 6f).coerceAtLeast(0f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(x, peakY),
                size = Size(barWidth, 3f),
                cornerRadius = CornerRadius(1.5f, 1.5f),
            )
        }
    }
}

@Composable
private fun WaveformVisualizer(
    isPlaying: Boolean,
    phase: Float,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier.fillMaxSize().padding(24.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        val points = 80
        val step = width / points

        val path1 = Path()
        val path2 = Path()

        for (i in 0..points) {
            val x = i * step
            val norm = (i.toFloat() / points)
            val envelope = sin(norm * PI.toFloat())

            val amp1 = if (isPlaying) (height * 0.3f * envelope) else 4f
            val amp2 = if (isPlaying) (height * 0.2f * envelope) else 2f

            val y1 = centerY + sin(phase * 2f + i * 0.2f) * amp1
            val y2 = centerY + sin(phase * 3f + i * 0.15f + 1f) * amp2

            if (i == 0) {
                path1.moveTo(x, y1)
                path2.moveTo(x, y2)
            } else {
                path1.lineTo(x, y1)
                path2.lineTo(x, y2)
            }
        }

        drawPath(
            path = path1,
            color = primaryColor,
            style = Stroke(width = 3.5f),
        )
        drawPath(
            path = path2,
            color = tertiaryColor.copy(alpha = 0.7f),
            style = Stroke(width = 2f),
        )
    }
}
