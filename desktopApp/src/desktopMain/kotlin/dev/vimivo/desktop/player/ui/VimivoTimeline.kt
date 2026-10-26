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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vimivo.desktop.player.monotonicMillis
import dev.vimivo.playback.ABRepeatEnd
import dev.vimivo.playback.ABRepeatRange
import dev.vimivo.playback.ChapterInfo
import dev.vimivo.playback.PlaybackClock
import dev.vimivo.playback.PlaybackControlPolicy
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.SeekController
import dev.vimivo.playback.TimelineGeometry
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.awt.MouseInfo
import kotlin.math.abs

/** The ViewModel's single [SeekController]; every timeline seeks through it. */
internal val LocalSeekController = staticCompositionLocalOf<SeekController> {
    error("No SeekController provided: wrap the player in CompositionLocalProvider(LocalSeekController ...)")
}

/**
 * The player timeline (spec §3.1), identical for video and audio: one continuous thin bar (played / buffered /
 * remaining), 3 dp growing to 5 dp on hover, a 12 dp thumb on hover and drag (always when [alwaysShowThumb]), chapter
 * ticks and the A–B range. No waveform.
 *
 * Smoothness: the playhead comes from a [PlaybackClock] read only in the draw phase; a frame loop runs only while
 * playing and invalidates drawing, so the bar moves every frame without recomposing the player.
 *
 * Input goes through the shared [SeekController]: a click is one exact seek; dragging previews with throttled keyframe
 * seeks and seeks exactly on release; the wheel seeks ±5 s. While a seek is in flight the thumb stays on the target
 * until the engine confirms it, then interpolation restarts from there (no snap-back). x is mapped against the drawn
 * track, which is inset by the thumb radius inside the 24 dp hit area, and mirrored in right-to-left layouts.
 */
// Click, scrub, wheel, hover, semantics and drawing form one control; detekt counts every branch.
@Suppress("CyclomaticComplexMethod")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun VimivoTimeline(
    playback: PlaybackState,
    label: String,
    modifier: Modifier = Modifier,
    alwaysShowThumb: Boolean = false,
    preview: (suspend (positionMillis: Long) -> ImageBitmap?)? = null,
    onAbChange: (ABRepeatRange) -> Unit = {},
    seek: SeekController = LocalSeekController.current,
) {
    val duration = playback.durationMillis?.takeIf { it > 0 }
    val seekState by seek.state.collectAsState()
    val clock = remember { PlaybackClock() }
    val running = playback.status == PlaybackStatus.Playing && !playback.isSeeking
    val held = seekState.overrideMillis != null
    LaunchedEffect(playback.positionMillis, running, playback.speed, duration, playback.sessionId, held) {
        // While a seek is held the engine's reports are stale; when the hold ends, restart exactly at the engine's
        // confirmed position instead of blending from the old one.
        if (!held) clock.report(playback.positionMillis, System.nanoTime(), running, playback.speed, duration, snap = true)
    }
    var frameNanos by remember { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(running) {
        while (running && isActive) withFrameNanos { frameNanos = System.nanoTime() }
    }

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var hoverX by remember { mutableStateOf<Float?>(null) }
    var hoverAnchor by remember { mutableStateOf<TooltipAnchor?>(null) }
    var widthPx by remember { mutableStateOf(1f) }
    var abDrag by remember { mutableStateOf<AbDrag?>(null) }
    val abChange by rememberUpdatedState(onAbChange)
    val region = abDrag?.range ?: playback.abRepeat
    val scrubbing = seekState.scrubMillis != null
    val track by animateDpAsState(
        if (hovered || scrubbing) 5.dp else 3.dp,
        spring(stiffness = Spring.StiffnessMedium),
    )
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val inset = with(LocalDensity.current) { THUMB_RADIUS.toPx() }
    val trackWidth = (widthPx - 2 * inset).coerceAtLeast(1f)
    fun millisAt(x: Float): Long? = duration?.let { TimelineGeometry.millisAt(x, inset, trackWidth, it, rtl) }
    val enabled = duration != null && playback.isSeekable

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
                    if (delta != 0f && enabled) {
                        val shown = clock.positionAt(System.nanoTime()).toLong()
                        seek.seekBy(if (delta < 0) WHEEL_STEP_MILLIS else -WHEEL_STEP_MILLIS, shown, monotonicMillis())
                    }
                }
                .pointerInput(duration, enabled, inset, trackWidth, rtl) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset -> millisAt(offset.x)?.let { seek.seekTo(it, monotonicMillis()) } }
                }
                .pointerInput(duration, enabled, playback.abRepeat, inset, trackWidth, rtl) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            val end = AbRegionGeometry.endAt(
                                offset.x - inset,
                                playback.abRepeat,
                                trackWidth,
                                duration,
                                AB_HIT_RADIUS.toPx(),
                            )
                            if (end != null) {
                                abDrag = AbDrag(end, playback.abRepeat)
                            } else {
                                millisAt(offset.x)?.let { seek.dragStart(it, monotonicMillis()) }
                            }
                        },
                        onDragEnd = {
                            abDrag?.let { abChange(it.range) }
                            abDrag = null
                            seek.dragEnd(monotonicMillis())
                        },
                        onDragCancel = {
                            abDrag = null
                            seek.dragCancel(monotonicMillis())
                        },
                    ) { change, _ ->
                        hoverX = change.position.x
                        val at = millisAt(change.position.x) ?: return@detectDragGestures
                        val dragging = abDrag
                        if (dragging != null) {
                            abDrag = dragging.copy(
                                range = PlaybackControlPolicy.moveABPoint(dragging.range, dragging.end, at, duration),
                            )
                        } else {
                            seek.dragTo(at, monotonicMillis())
                        }
                    }
                }
                .timelineSemantics(label, playback, duration, region, seek),
        ) {
            frameNanos // Read here so each frame only redraws.
            drawTimeline(
                TimelineFrame(
                    playback = playback,
                    duration = duration,
                    region = region,
                    trackHeight = track.toPx(),
                    trackStart = inset,
                    trackWidth = trackWidth,
                    rightToLeft = rtl,
                    positionMillis = seekState.overrideMillis?.toDouble() ?: clock.positionAt(System.nanoTime()),
                    showThumb = enabled && (alwaysShowThumb || hovered || scrubbing),
                ),
            )
        }
        val tipX = hoverX
        val tipMillis = tipX?.let(::millisAt)
        val anchor = hoverAnchor
        if (tipX != null && tipMillis != null && anchor != null) {
            TimelineTooltip(anchor, tipMillis, chapterAt(playback.chapters, tipMillis), preview)
        }
    }
}

private fun Modifier.timelineSemantics(
    label: String,
    playback: PlaybackState,
    duration: Long?,
    region: ABRepeatRange,
    seek: SeekController,
): Modifier = semantics {
    contentDescription = label
    val total = duration?.let(::playerClock) ?: "unknown"
    stateDescription = "${playerClock(playback.positionMillis)} of $total" + AbRegionGeometry.describe(region)
    if (duration != null && playback.isSeekable) {
        val range = 0f..duration.toFloat()
        progressBarRangeInfo = ProgressBarRangeInfo(playback.positionMillis.toFloat(), range)
        setProgress { target ->
            seek.seekTo(target.toLong(), monotonicMillis())
            true
        }
    }
}

/** What one frame of the timeline draws; read in the draw phase so playback never recomposes the player. */
private class TimelineFrame(
    val playback: PlaybackState,
    val duration: Long?,
    val region: ABRepeatRange,
    val trackHeight: Float,
    val trackStart: Float,
    val trackWidth: Float,
    val rightToLeft: Boolean,
    val positionMillis: Double,
    val showThumb: Boolean,
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTimeline(frame: TimelineFrame) {
    val trackHeight = frame.trackHeight
    val top = (size.height - trackHeight) / 2
    val radius = CornerRadius(trackHeight / 2)
    val start = frame.trackStart
    val width = frame.trackWidth
    drawRoundRect(PlayerPalette.trackRemaining, Offset(start, top), Size(width, trackHeight), radius)
    val total = frame.duration ?: return
    fun x(millis: Long) = TimelineGeometry.xAt(millis.toDouble(), start, width, total, frame.rightToLeft)
    // A span from the media start to [millis]: left-anchored, or right-anchored when right-to-left.
    fun span(colour: Color, end: Float) {
        val left = if (frame.rightToLeft) end else start
        val right = if (frame.rightToLeft) start + width else end
        if (right > left) drawRoundRect(colour, Offset(left, top), Size(right - left, trackHeight), radius)
    }
    span(PlayerPalette.trackBuffered, x(frame.playback.bufferedPositionMillis))
    if (frame.region.pointA != null) drawAbRange(frame.region, ::x, top, trackHeight)
    val played = TimelineGeometry.xAt(frame.positionMillis, start, width, total, frame.rightToLeft)
    span(PlayerPalette.trackPlayed, played)
    frame.playback.chapters.drop(1).forEach { chapter ->
        drawRect(
            Color.Black.copy(alpha = 0.55f),
            Offset(x(chapter.startPositionMillis), top),
            Size(1f, trackHeight)
        )
    }
    if (frame.showThumb) {
        drawCircle(
            Color.Black.copy(alpha = 0.35f),
            THUMB_RADIUS.toPx() + 1f,
            Offset(played, size.height / 2 + 1f)
        )
        drawCircle(PlayerPalette.trackPlayed, THUMB_RADIUS.toPx(), Offset(played, size.height / 2))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAbRange(
    range: ABRepeatRange,
    x: (Long) -> Float,
    top: Float,
    height: Float,
) {
    val bounds = AbRegionGeometry.bounds(range, x, MIN_REGION_PX) ?: return
    drawRect(
        PlayerPalette.abRange,
        Offset(bounds.start, top - 2f),
        Size(bounds.end - bounds.start, height + 4f),
    )
    // End caps mark A and B; they are the handles for dragging.
    val cap = Size(AB_CAP_WIDTH_PX, height + AB_CAP_OVERHANG_PX)
    drawRect(PlayerPalette.trackPlayed, Offset(bounds.start, top - AB_CAP_OVERHANG_PX / 2), cap)
    if (range.pointB != null) {
        drawRect(PlayerPalette.trackPlayed, Offset(bounds.end - AB_CAP_WIDTH_PX, top - AB_CAP_OVERHANG_PX / 2), cap)
    }
}

private data class AbDrag(val end: ABRepeatEnd, val range: ABRepeatRange)

/** Where the A–B region sits on the timeline, and which end a pointer is on; separate from drawing so it is tested. */
internal object AbRegionGeometry {
    data class Bounds(val start: Float, val end: Float)

    /** The drawn span: A alone is a thin marker, and a region is never narrower than [minWidth]. */
    fun bounds(range: ABRepeatRange, x: (Long) -> Float, minWidth: Float): Bounds? {
        val start = x(range.pointA ?: return null)
        val end = range.pointB?.let(x) ?: (start + minWidth)
        return Bounds(start, maxOf(end, start + minWidth))
    }

    /** The end of a complete region within [hitRadius] px of [pointerX], the nearer one when both are. */
    fun endAt(
        pointerX: Float,
        range: ABRepeatRange,
        widthPx: Float,
        durationMillis: Long,
        hitRadius: Float,
    ): ABRepeatEnd? {
        val a = range.pointA
        val b = range.pointB
        if (a == null || b == null || !range.enabled) return null
        if (durationMillis <= 0) return null
        fun px(millis: Long) = millis.toFloat() / durationMillis * widthPx
        val distanceA = abs(pointerX - px(a))
        val distanceB = abs(pointerX - px(b))
        return when {
            minOf(distanceA, distanceB) > hitRadius -> null
            distanceA <= distanceB -> ABRepeatEnd.A
            else -> ABRepeatEnd.B
        }
    }

    /** Screen-reader text for the region, appended to the timeline's state. */
    fun describe(range: ABRepeatRange): String {
        val a = range.pointA ?: return ""
        val b = range.pointB ?: return ", repeat start ${playerClock(a)}"
        return ", repeating ${playerClock(a)} to ${playerClock(b)}"
    }
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

private val HIT_HEIGHT = 24.dp
private val AB_HIT_RADIUS = 8.dp
private const val MIN_REGION_PX = 2f
private const val AB_CAP_WIDTH_PX = 2f
private const val AB_CAP_OVERHANG_PX = 8f
private val TIP_GAP = 6.dp
private val TEXT_TIP_SIZE = DpSize(176.dp, 52.dp)
private val PREVIEW_TIP_SIZE = DpSize(176.dp, 148.dp)
private val THUMB_RADIUS = 6.dp
private val PREVIEW_WIDTH = 160.dp
private val PREVIEW_HEIGHT = 90.dp
private val TOOLTIP_TEXT = TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum")
private const val WHEEL_STEP_MILLIS = 5_000L
private const val PREVIEW_BUCKET_MILLIS = 2_000L
private const val PREVIEW_DEBOUNCE_MILLIS = 120L
