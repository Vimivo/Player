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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import dev.vimivo.playback.ABRepeatRange
import dev.vimivo.playback.ChapterInfo
import dev.vimivo.playback.PlaybackClock
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
                .onPointerEvent(PointerEventType.Move) { hoverX = it.changes.firstOrNull()?.position?.x }
                .onPointerEvent(PointerEventType.Exit) { hoverX = null }
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
                        onDragEnd = { scrubMillis?.let { seek(it, true) }; scrubMillis = null },
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
                    stateDescription = "${playerClock(playback.positionMillis)} of ${duration?.let(::playerClock) ?: "unknown"}"
                    if (duration != null) {
                        progressBarRangeInfo = ProgressBarRangeInfo(playback.positionMillis.toFloat(), 0f..duration.toFloat())
                        setProgress { target -> seek(target.toLong().coerceIn(0, duration), true); true }
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
                drawRect(Color.Black.copy(alpha = 0.55f), Offset(x(chapter.startPositionMillis), top), Size(1f, trackHeight))
            }
            if (alwaysShowThumb || hovered || scrubMillis != null) {
                drawCircle(Color.Black.copy(alpha = 0.35f), THUMB_RADIUS.toPx() + 1f, Offset(played, size.height / 2 + 1f))
                drawCircle(PlayerPalette.trackPlayed, THUMB_RADIUS.toPx(), Offset(played, size.height / 2))
            }
        }
        val tipX = hoverX
        val tipMillis = tipX?.let(::millisAt)
        if (tipX != null && tipMillis != null) {
            TimelineTooltip(tipX, tipMillis, chapterAt(playback.chapters, tipMillis), preview)
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

/** Time, chapter and (when available) a frame preview, floating above the cursor. */
@Composable
private fun TimelineTooltip(
    x: Float,
    millis: Long,
    chapter: ChapterInfo?,
    preview: (suspend (Long) -> ImageBitmap?)?,
) {
    // Previews are requested for 1 % steps of the hover position, so moving the mouse does not flood the decoder.
    val bucket = millis / PREVIEW_BUCKET_MILLIS
    val image by produceState<ImageBitmap?>(null, bucket, preview) {
        if (preview == null) return@produceState
        delay(PREVIEW_DEBOUNCE_MILLIS)
        value = preview(bucket * PREVIEW_BUCKET_MILLIS)
    }
    val density = LocalDensity.current
    val anchorX = with(density) { x.toInt() }
    Popup(popupPositionProvider = AboveCursor(anchorX, with(density) { 8.dp.roundToPx() })) {
        Column(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(PlayerPalette.glass)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            image?.let { Image(it, contentDescription = null, modifier = Modifier.size(PREVIEW_WIDTH, PREVIEW_HEIGHT).clip(RoundedCornerShape(4.dp))) }
            Text(playerClock(millis), color = PlayerPalette.trackPlayed, style = TOOLTIP_TEXT, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            chapter?.let { Text(it.title, color = PlayerPalette.contentMuted, style = TOOLTIP_TEXT, maxLines = 1, softWrap = false) }
        }
    }
}

/** Centres the popup horizontally on the cursor, just above the timeline. */
private class AboveCursor(private val anchorX: Int, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left + anchorX - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        return IntOffset(x, anchorBounds.top - popupContentSize.height - gap)
    }
}

/** Player time: "1:02:03" or "02:03" (two-digit minutes, VLC-style). */
internal fun playerClock(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1_000
    val hours = seconds / 3_600
    return if (hours > 0) "%d:%02d:%02d".format(hours, seconds / 60 % 60, seconds % 60) else "%02d:%02d".format(seconds / 60, seconds % 60)
}

private val HIT_HEIGHT = 20.dp
private val THUMB_RADIUS = 6.dp
private val PREVIEW_WIDTH = 160.dp
private val PREVIEW_HEIGHT = 90.dp
private val TOOLTIP_TEXT = TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum")
private const val WHEEL_STEP_MILLIS = 5_000L
private const val SCRUB_THROTTLE_NANOS = 120_000_000L
private const val PREVIEW_BUCKET_MILLIS = 2_000L
private const val PREVIEW_DEBOUNCE_MILLIS = 120L
