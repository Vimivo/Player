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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.collectAsState
import dev.vimivo.playback.SeekController
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
private val TIMELINE_HIT_HEIGHT = 48.dp
private val TIMELINE_THUMB_RADIUS = 6.dp

/**
 * Clean thin continuous seek bar (spec §3.1) for both audio and video: 3dp track growing to 5dp on drag,
 * played = white/accent, buffered = white 35%, remaining = white 18%, round thumb, chapter ticks.
 * Coordinates input and snap-back prevention through [SeekController].
 */
@Composable
internal fun VimivoTimeline(
    positionMillis: Long,
    durationMillis: Long,
    bufferedPositionMillis: Long = 0L,
    chapters: List<dev.vimivo.playback.ChapterInfo> = emptyList(),
    seekController: SeekController,
    modifier: Modifier = Modifier,
    alwaysShowThumb: Boolean = false,
    playedColor: Color = MaterialTheme.colorScheme.primary,
) {
    val duration = durationMillis.takeIf { it > 0 }
    val seekState by seekController.state.collectAsState()
    val isScrubbing = seekState.scrubMillis != null
    val isHolding = seekState.overrideMillis != null
    val displayedMillis = seekState.overrideMillis ?: positionMillis

    val trackHeight by androidx.compose.animation.core.animateDpAsState(
        if (isScrubbing) 5.dp else 3.dp,
        androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
    )

    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val density = androidx.compose.ui.platform.LocalDensity.current
    val thumbRadiusPx = with(density) { TIMELINE_THUMB_RADIUS.toPx() }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val trackWidthPx = (widthPx - 2 * thumbRadiusPx).coerceAtLeast(1f)
    fun millisAt(x: Float): Long? = duration?.let { dev.vimivo.playback.TimelineGeometry.millisAt(x, thumbRadiusPx, trackWidthPx, it, rtl) }
    val enabled = duration != null && seekController.canSeek

    val remainingColor = Color.White.copy(alpha = 0.18f)
    val bufferedColor = Color.White.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TIMELINE_HIT_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(enabled, duration, thumbRadiusPx, trackWidthPx, rtl) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        millisAt(offset.x)?.let {
                            seekController.seekTo(it, android.os.SystemClock.elapsedRealtime())
                        }
                    }
                }
                .pointerInput(enabled, duration, thumbRadiusPx, trackWidthPx, rtl) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            millisAt(offset.x)?.let {
                                seekController.dragStart(it, android.os.SystemClock.elapsedRealtime())
                            }
                        },
                        onDragEnd = {
                            seekController.dragEnd(android.os.SystemClock.elapsedRealtime())
                        },
                        onDragCancel = {
                            seekController.dragCancel(android.os.SystemClock.elapsedRealtime())
                        },
                    ) { change, _ ->
                        change.consume()
                        millisAt(change.position.x)?.let {
                            seekController.dragTo(it, android.os.SystemClock.elapsedRealtime())
                        }
                    }
                }
                .semantics {
                    contentDescription = "Playback seekbar"
                    stateDescription = "${formatClock(displayedMillis)} of ${formatClock(durationMillis)}"
                    if (duration != null) {
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            displayedMillis.coerceIn(0, duration).toFloat(),
                            0f..duration.toFloat(),
                        )
                        setProgress { target ->
                            seekController.seekTo(target.toLong(), android.os.SystemClock.elapsedRealtime())
                            true
                        }
                    }
                },
        ) {
            val trackH = trackHeight.toPx()
            val top = (size.height - trackH) / 2
            val radius = androidx.compose.ui.geometry.CornerRadius(trackH / 2)
            val start = thumbRadiusPx
            val width = trackWidthPx

            drawRoundRect(remainingColor, androidx.compose.ui.geometry.Offset(start, top), androidx.compose.ui.geometry.Size(width, trackH), radius)

            val total = duration ?: return@Canvas
            fun x(millis: Long) = dev.vimivo.playback.TimelineGeometry.xAt(millis.toDouble(), start, width, total, rtl)

            fun span(color: Color, endPx: Float) {
                val left = if (rtl) endPx else start
                val right = if (rtl) start + width else endPx
                if (right > left) drawRoundRect(color, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(right - left, trackH), radius)
            }

            span(bufferedColor, x(bufferedPositionMillis))

            val playedPx = dev.vimivo.playback.TimelineGeometry.xAt(displayedMillis.toDouble(), start, width, total, rtl)
            span(playedColor, playedPx)

            chapters.drop(1).forEach { chapter ->
                val chapterX = x(chapter.startPositionMillis)
                drawRect(
                    Color.Black.copy(alpha = 0.55f),
                    androidx.compose.ui.geometry.Offset(chapterX, top),
                    androidx.compose.ui.geometry.Size(1.dp.toPx(), trackH),
                )
            }

            if (alwaysShowThumb || isScrubbing || isHolding) {
                drawCircle(
                    Color.Black.copy(alpha = 0.35f),
                    thumbRadiusPx + 1f,
                    androidx.compose.ui.geometry.Offset(playedPx, size.height / 2 + 1f),
                )
                drawCircle(
                    playedColor,
                    thumbRadiusPx,
                    androidx.compose.ui.geometry.Offset(playedPx, size.height / 2),
                )
            }
        }
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
