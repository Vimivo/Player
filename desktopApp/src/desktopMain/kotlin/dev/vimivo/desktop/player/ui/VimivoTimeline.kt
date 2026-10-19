@file:Suppress("LongParameterList") // Composables take their state, callbacks and styling explicitly.

package dev.vimivo.desktop.player.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vimivo.playback.ABRepeatRange
import dev.vimivo.playback.ChapterInfo
import dev.vimivo.playback.PlaybackClock
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.awt.MouseInfo

/**
 * The player timeline (spec §3.1): one continuous thin bar (played / buffered / remaining), 3 dp growing to 5 dp on
 * hover, a 12 dp thumb on hover and drag (always when [alwaysShowThumb]), chapter ticks and the A–B range.
 *
 * Smoothness: the playhead comes from a [PlaybackClock] read only in the draw phase; a frame loop runs only while
 * playing and invalidates drawing, so the bar moves every frame without recomposing the player.
 *
 * Input: click seeks exactly; dragging scrubs with keyframe seeks (throttled) and seeks exactly on release; the wheel
 * seeks ±5 s; hovering shows the time, the chapter and, when [preview] returns one, a frame preview.
 */
// Click, scrub, wheel, hover, semantics and drawing form one control; detekt counts every branch.
@Suppress("CyclomaticComplexMethod")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun VimivoTimeline(
    playback: PlaybackState,
    label: String,
    onSeek: (positionMillis: Long, exact: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    alwaysShowThumb: Boolean = false,
    preview: (suspend (positionMillis: Long) -> ImageBitmap?)? = null,
) {
    val duration = playback.durationMillis?.takeIf { it > 0 }
    val clock = remember { PlaybackClock() }
    val running = playback.status == PlaybackStatus.Playing
    LaunchedEffect(playback.positionMillis, running, playback.speed, duration, playback.sessionId) {
        clock.report(playback.positionMillis, System.nanoTime(), running, playback.speed, duration)
    }
    var frameNanos by remember { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(running) {
        while (running && isActive) withFrameNanos { frameNanos = System.nanoTime() }
    }

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var hoverX by remember { mutableStateOf<Float?>(null) }
    var hoverAnchor by remember { mutableStateOf<TooltipAnchor?>(null) }
    var scrubMillis by remember { mutableStateOf<Long?>(null) }
    var widthPx by remember { mutableStateOf(1f) }
    val track by animateDpAsState(
        if (hovered || scrubMillis != null) 5.dp else 3.dp,
        spring(stiffness = Spring.StiffnessMedium),
    )
    val seek by rememberUpdatedState(onSeek)
    fun millisAt(x: Float): Long? = duration?.let { (x / widthPx).coerceIn(0f, 1f).times(it).toLong() }

    Box(modifier.height(HIT_HEIGHT)) {
        Canvas(
            Modifier
                .matchParentSize()
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .hoverable(interaction)
                .onPointerEvent(PointerEventType.Move) { event ->
                    val position = event.changes.firstOrNull()?.position
                    hoverX = position?.x
                    hoverAnchor = position?.let { TooltipAnchor.fromPointer(it.y) }
                }
                .onPointerEvent(PointerEventType.Exit) {
                    hoverX = null
                    hoverAnchor = null
                }
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (delta != 0f && duration != null) {
                        val now = clock.positionAt(System.nanoTime()).toLong()
                        val step = if (delta < 0) WHEEL_STEP_MILLIS else -WHEEL_STEP_MILLIS
                        seek((now + step).coerceIn(0, duration), false)
                    }
                }
                .pointerInput(duration) {
                    detectTapGestures { offset -> millisAt(offset.x)?.let { seek(it, true) } }
                }
                .pointerInput(duration) {
                    var lastSeekNanos = 0L
                    detectDragGestures(
                        onDragStart = { offset -> scrubMillis = millisAt(offset.x) },
                        onDragEnd = {
                            scrubMillis?.let { seek(it, true) }
                            scrubMillis = null
                        },
                        onDragCancel = { scrubMillis = null },
                    ) { change, _ ->
                        hoverX = change.position.x
                        scrubMillis = millisAt(change.position.x)
                        val now = System.nanoTime()
                        if (now - lastSeekNanos > SCRUB_THROTTLE_NANOS) {
                            lastSeekNanos = now
                            scrubMillis?.let { seek(it, false) }
                        }
                    }
                }
                .semantics {
                    contentDescription = label
                    val total = duration?.let(::playerClock) ?: "unknown"
                    stateDescription = "${playerClock(playback.positionMillis)} of $total"
                    if (duration != null) {
                        val range = 0f..duration.toFloat()
                        progressBarRangeInfo = ProgressBarRangeInfo(playback.positionMillis.toFloat(), range)
                        setProgress { target ->
                            seek(target.toLong().coerceIn(0, duration), true)
                            true
                        }
                    }
                },
        ) {
            frameNanos // Read here so each frame only redraws.
            val trackHeight = track.toPx()
            val top = (size.height - trackHeight) / 2
            val radius = CornerRadius(trackHeight / 2)
            drawRoundRect(PlayerPalette.trackRemaining, Offset(0f, top), Size(size.width, trackHeight), radius)
            val total = duration?.toFloat() ?: return@Canvas
            fun x(millis: Long) = (millis / total).coerceIn(0f, 1f) * size.width
            val buffered = x(playback.bufferedPositionMillis)
            drawRoundRect(PlayerPalette.trackBuffered, Offset(0f, top), Size(buffered, trackHeight), radius)
            val ab = playback.abRepeat
            if (ab.pointA != null) drawAbRange(ab, ::x, top, trackHeight)
            val position = scrubMillis?.toFloat() ?: clock.positionAt(System.nanoTime()).toFloat()
            val played = (position / total).coerceIn(0f, 1f) * size.width
            drawRoundRect(PlayerPalette.trackPlayed, Offset(0f, top), Size(played, trackHeight), radius)
            playback.chapters.drop(1).forEach { chapter ->
                drawRect(
                    Color.Black.copy(alpha = 0.55f),
                    Offset(x(chapter.startPositionMillis), top),
                    Size(1f, trackHeight)
                )
            }
            if (alwaysShowThumb || hovered || scrubMillis != null) {
                drawCircle(
                    Color.Black.copy(alpha = 0.35f),
                    THUMB_RADIUS.toPx() + 1f,
                    Offset(played, size.height / 2 + 1f)
                )
                drawCircle(PlayerPalette.trackPlayed, THUMB_RADIUS.toPx(), Offset(played, size.height / 2))
            }
        }
        val tipX = hoverX
        val tipMillis = tipX?.let(::millisAt)
        val anchor = hoverAnchor
        if (tipX != null && tipMillis != null && anchor != null) {
            TimelineTooltip(anchor, tipMillis, chapterAt(playback.chapters, tipMillis), preview)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAbRange(
    range: ABRepeatRange,
    x: (Long) -> Float,
    top: Float,
    height: Float,
) {
    val start = x(range.pointA ?: return)
    val end = range.pointB?.let(x) ?: (start + 2f)
    drawRect(PlayerPalette.abRange, Offset(start, top - 2f), Size((end - start).coerceAtLeast(2f), height + 4f))
}

private fun chapterAt(chapters: List<ChapterInfo>, millis: Long): ChapterInfo? =
    chapters.lastOrNull { it.startPositionMillis <= millis }

/**
 * Where the pointer is on screen (AWT points) and how far below the timeline's top edge it is, taken at the hover
 * event. Anchoring to the real pointer avoids mapping Compose coordinates to the screen.
 */
internal data class TooltipAnchor(val screenX: Int, val screenY: Int, val pointerYInTimelinePx: Float) {
    /** Top-left of a tooltip of [size]: centred on the pointer, [gap] above the timeline's top edge. */
    fun tooltipPosition(size: DpSize, density: Float, gap: Dp): DpOffset {
        val timelineTop = screenY - pointerYInTimelinePx / density
        return DpOffset((screenX - size.width.value / 2).dp, (timelineTop - size.height.value - gap.value).dp)
    }

    companion object {
        fun fromPointer(yInTimelinePx: Float): TooltipAnchor? = MouseInfo.getPointerInfo()?.location?.let {
            TooltipAnchor(it.x, it.y, yInTimelinePx)
        }
    }
}

/**
 * Time, chapter and (when available) a frame preview above the cursor. It is its own small window: the video is a
 * native layer above Compose, so a popup inside the player window would be hidden behind it.
 */
@Composable
private fun TimelineTooltip(
    anchor: TooltipAnchor,
    millis: Long,
    chapter: ChapterInfo?,
    preview: (suspend (Long) -> ImageBitmap?)?,
) {
    val window = LocalHostWindow.current ?: return
    // Previews are requested per 2 s of hover position, so moving the mouse does not flood the decoder.
    val bucket = millis / PREVIEW_BUCKET_MILLIS
    val image by produceState<ImageBitmap?>(null, bucket, preview) {
        if (preview == null) return@produceState
        delay(PREVIEW_DEBOUNCE_MILLIS)
        value = preview(bucket * PREVIEW_BUCKET_MILLIS)
    }
    val size = if (preview != null) PREVIEW_TIP_SIZE else TEXT_TIP_SIZE
    val position = anchor.tooltipPosition(size, LocalDensity.current.density, TIP_GAP)
    OverlayWindow(owner = window, position = position, size = size, title = "Timeline preview") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(PlayerPalette.glass)
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                image?.let {
                    Image(
                        it,
                        contentDescription = null,
                        modifier = Modifier.size(PREVIEW_WIDTH, PREVIEW_HEIGHT).clip(RoundedCornerShape(4.dp))
                    )
                }
                Text(
                    playerClock(millis),
                    color = PlayerPalette.trackPlayed,
                    style = TOOLTIP_TEXT,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
                chapter?.let {
                    Text(
                        it.title,
                        color = PlayerPalette.contentMuted,
                        style = TOOLTIP_TEXT,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = PREVIEW_WIDTH)
                    )
                }
            }
        }
    }
}

/** Player time: "1:02:03" or "02:03" (two-digit minutes, VLC-style). */
internal fun playerClock(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1_000
    val hours = seconds / 3_600
    return if (hours > 0) {
        "%d:%02d:%02d".format(
            hours,
            seconds / 60 % 60,
            seconds % 60
        )
    } else {
        "%02d:%02d".format(seconds / 60, seconds % 60)
    }
}

private val HIT_HEIGHT = 20.dp
private val TIP_GAP = 6.dp
private val TEXT_TIP_SIZE = DpSize(176.dp, 52.dp)
private val PREVIEW_TIP_SIZE = DpSize(176.dp, 148.dp)
private val THUMB_RADIUS = 6.dp
private val PREVIEW_WIDTH = 160.dp
private val PREVIEW_HEIGHT = 90.dp
private val TOOLTIP_TEXT = TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum")
private const val WHEEL_STEP_MILLIS = 5_000L
private const val SCRUB_THROTTLE_NANOS = 120_000_000L
private const val PREVIEW_BUCKET_MILLIS = 2_000L
private const val PREVIEW_DEBOUNCE_MILLIS = 120L
