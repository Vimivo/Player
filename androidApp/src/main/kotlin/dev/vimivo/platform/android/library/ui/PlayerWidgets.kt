@file:Suppress(
    "ArgumentListWrapping",
    "CyclomaticComplexMethod",
    "ImportOrdering",
    "Indentation",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "MaximumLineLength",
    "MaxLineLength",
    "MultiLineIfElse",
    "ParameterListWrapping",
    "TooManyFunctions",
    "Wrapping",
)

package dev.vimivo.platform.android.library.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlin.math.abs

/** Formats a position as m:ss or h:mm:ss. */
internal fun formatClock(millis: Long): String {
    val total = (millis.coerceAtLeast(0) / 1_000).toInt()
    val hours = total / 3_600
    val minutes = total % 3_600 / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/**
 * The track's seek bar. With [peaks] (the real loudness of the track, computed in the background) it draws bars; until
 * they exist, or when the visualizer is off, it is a plain thin line with a thumb. Tap or drag to seek; accessibility
 * services see a slider either way.
 */
@Composable
internal fun WaveformSeekBar(
    positionMillis: Long,
    durationMillis: Long,
    peaks: FloatArray?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val played = MaterialTheme.colorScheme.primary
    val remaining = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val fraction = if (dragFraction >= 0f) dragFraction else {
        if (durationMillis > 0) (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f) else 0f
    }
    val enabled = durationMillis > 0
    Canvas(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .pointerInput(enabled, durationMillis) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset -> onSeek((offset.x / size.width * durationMillis).toLong().coerceIn(0, durationMillis)) }
            }
            .pointerInput(enabled, durationMillis) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        onSeek((dragFraction * durationMillis).toLong())
                        dragFraction = -1f
                    },
                    onDragCancel = { dragFraction = -1f },
                ) { change, _ ->
                    change.consume()
                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
            .semantics {
                contentDescription = "Playback seekbar"
                stateDescription = "${formatClock(positionMillis)} of ${formatClock(durationMillis)}"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    positionMillis.coerceIn(0, durationMillis.coerceAtLeast(1)).toFloat(),
                    0f..durationMillis.coerceAtLeast(1).toFloat(),
                )
                setProgress { target ->
                    onSeek(target.toLong().coerceIn(0, durationMillis))
                    true
                }
            },
    ) {
        if (peaks == null || peaks.isEmpty()) drawPlainLine(fraction, played, remaining) else drawPeaks(peaks, fraction, played, remaining)
    }
}

private fun DrawScope.drawPlainLine(fraction: Float, played: Color, remaining: Color) {
    val thickness = 4.dp.toPx()
    val y = size.height / 2
    val corner = CornerRadius(thickness / 2)
    drawRoundRect(remaining, Offset(0f, y - thickness / 2), Size(size.width, thickness), corner)
    drawRoundRect(played, Offset(0f, y - thickness / 2), Size(size.width * fraction, thickness), corner)
    drawCircle(played, 7.dp.toPx(), Offset(size.width * fraction, y))
}

private fun DrawScope.drawPeaks(peaks: FloatArray, fraction: Float, played: Color, remaining: Color) {
    val slot = size.width / peaks.size
    val barWidth = slot * 0.56f
    val minimum = 3.dp.toPx()
    peaks.forEachIndexed { index, peak ->
        val barHeight = maxOf(minimum, size.height * peak)
        val x = index * slot + (slot - barWidth) / 2
        drawRoundRect(
            color = if ((index + 0.5f) / peaks.size <= fraction) played else remaining,
            topLeft = Offset(x, (size.height - barHeight) / 2),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2),
        )
    }
}

/** The elapsed and remaining time under a seek bar. */
@Composable
internal fun TimeLabels(positionMillis: Long, durationMillis: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatClock(positionMillis), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatClock(durationMillis), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Shuffle, previous, play/pause in a ring, next and repeat. */
@Composable
internal fun TransportRow(
    isPlaying: Boolean,
    shuffle: Boolean,
    repeatMode: Int,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurface
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onShuffle, Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Shuffle, if (shuffle) "Disable shuffle" else "Enable shuffle", tint = if (shuffle) primary else idle.copy(alpha = 0.8f))
        }
        IconButton(onPrevious, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous track", Modifier.size(34.dp)) }
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(primary.copy(alpha = 0.16f)).border(2.dp, primary, CircleShape)
                .clickable(onClickLabel = if (isPlaying) "Pause" else "Play", onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (isPlaying) "Pause" else "Play",
                Modifier.size(40.dp),
            )
        }
        IconButton(onNext, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, "Next track", Modifier.size(34.dp)) }
        IconButton(onRepeat, Modifier.size(48.dp)) {
            val active = repeatMode != Player.REPEAT_MODE_OFF
            Icon(
                if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Repeat mode ${when (repeatMode) { Player.REPEAT_MODE_ONE -> "one"; Player.REPEAT_MODE_ALL -> "all"; else -> "off" }}",
                tint = if (active) primary else idle.copy(alpha = 0.8f),
            )
        }
    }
}

/** A vertical gain slider for one equalizer band; drag anywhere on the track. */
@Composable
internal fun VerticalGainSlider(
    gainDb: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val active = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val zero = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    fun valueAt(y: Float, height: Float) = range.endInclusive - (y / height).coerceIn(0f, 1f) * (range.endInclusive - range.start)
    Canvas(
        modifier
            .semantics {
                contentDescription = "$label gain"
                stateDescription = "%.1f dB".format(gainDb)
                progressBarRangeInfo = ProgressBarRangeInfo(gainDb, range)
                setProgress { target ->
                    if (enabled) onChange(target.coerceIn(range.start, range.endInclusive))
                    enabled
                }
            }
            .pointerInput(enabled, range) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { onChange(valueAt(it.y, size.height.toFloat())) },
                ) { change, _ ->
                    change.consume()
                    onChange(valueAt(change.position.y, size.height.toFloat()))
                }
            }
            .pointerInput(enabled, range) {
                if (!enabled) return@pointerInput
                detectTapGestures { onChange(valueAt(it.y, size.height.toFloat())) }
            },
    ) {
        val centreX = size.width / 2
        val span = range.endInclusive - range.start
        val thumbY = size.height * (range.endInclusive - gainDb) / span
        val zeroY = size.height * range.endInclusive / span
        drawRoundRect(track, Offset(centreX - 2.dp.toPx(), 0f), Size(4.dp.toPx(), size.height), CornerRadius(2.dp.toPx()))
        drawLine(zero, Offset(centreX - 9.dp.toPx(), zeroY), Offset(centreX + 9.dp.toPx(), zeroY), 1.dp.toPx())
        val top = minOf(thumbY, zeroY)
        drawRoundRect(
            if (enabled) active else active.copy(alpha = 0.4f),
            Offset(centreX - 2.dp.toPx(), top), Size(4.dp.toPx(), abs(thumbY - zeroY)), CornerRadius(2.dp.toPx()),
        )
        drawCircle(if (enabled) active else active.copy(alpha = 0.4f), 9.dp.toPx(), Offset(centreX, thumbY))
        drawCircle(Color.White.copy(alpha = if (enabled) 0.9f else 0.5f), 3.5.dp.toPx(), Offset(centreX, thumbY))
    }
}

/** A labelled, tappable action under the player: icon over text, with an optional count badge. */
@Composable
internal fun PlayerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    active: Boolean = false,
) {
    Column(
        modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Icon(icon, null, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            badge?.let {
                Text(
                    it,
                    Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.primary, CircleShape).padding(horizontal = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
