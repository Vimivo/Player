@file:Suppress("LongParameterList") // Composables take their state, callbacks and styling explicitly.

package dev.vimivo.desktop.player.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 18 dp icon in a 32 × 32 dp target with an 8 % hover background (radius 6). */
@Composable
internal fun PlayerIconButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = 32.dp,
    iconSize: Dp = 18.dp,
    onClick: () -> Unit,
) {
    PlayerIconFace(
        icon,
        label,
        modifier.pointerInput(onClick) { detectTapGestures { onClick() } },
        selected,
        size,
        iconSize,
    )
}

/** The look of [PlayerIconButton] without its click handling, for buttons with their own gestures. */
@Composable
internal fun PlayerIconFace(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = 32.dp,
    iconSize: Dp = 18.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) PlayerPalette.hover else Color.Transparent)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) PlayerPalette.trackPlayed else PlayerPalette.content,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * A thin slider (3 dp track, 5 dp on hover, 12 dp thumb) for volume and picture settings. Double-click resets to
 * [resetValue] when given; arrow keys step by [keyStep]. Above [boostFrom] the filled part turns amber.
 */
// Keys, tap, double-tap reset, drag, wheel and semantics form one input contract; detekt counts every branch.
@Suppress("CyclomaticComplexMethod")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun VimivoSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    resetValue: Float? = null,
    boostFrom: Float? = null,
    keyStep: Float = (valueRange.endInclusive - valueRange.start) / KEY_STEPS,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val track by animateDpAsState(if (hovered || dragging) 5.dp else 3.dp, spring(stiffness = Spring.StiffnessMedium))
    val latest by rememberUpdatedState(onValueChange)
    val span = valueRange.endInclusive - valueRange.start
    fun valueAt(x: Float, width: Float) = (valueRange.start + (x / width).coerceIn(0f, 1f) * span)
    fun step(delta: Float) = latest((value + delta).coerceIn(valueRange))

    Canvas(
        modifier
            .height(HIT_HEIGHT)
            .hoverable(interaction)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight, Key.DirectionUp -> true.also { step(keyStep) }
                    Key.DirectionLeft, Key.DirectionDown -> true.also { step(-keyStep) }
                    else -> false
                }
            }
            .pointerInput(valueRange, resetValue) {
                detectTapGestures(
                    onDoubleTap = { resetValue?.let(latest) },
                    onTap = { offset -> latest(valueAt(offset.x, size.width.toFloat())) },
                )
            }
            .pointerInput(valueRange) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ -> latest(valueAt(change.position.x, size.width.toFloat())) }
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (delta != 0f) step(if (delta < 0) keyStep else -keyStep)
            }
            .semantics {
                contentDescription = label
                stateDescription = valueText
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                setProgress { target ->
                    latest(target.coerceIn(valueRange))
                    true
                }
            },
    ) {
        val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
        val trackHeight = track.toPx()
        val top = (size.height - trackHeight) / 2
        val radius = CornerRadius(trackHeight / 2)
        drawRoundRect(PlayerPalette.trackRemaining, Offset(0f, top), Size(size.width, trackHeight), radius)
        val boostX = boostFrom?.let { ((it - valueRange.start) / span) * size.width }
        val filled = fraction * size.width
        if (boostX != null && filled > boostX) {
            drawRoundRect(PlayerPalette.boost, Offset(0f, top), Size(filled, trackHeight), radius)
            drawRoundRect(PlayerPalette.trackPlayed, Offset(0f, top), Size(boostX, trackHeight), radius)
        } else {
            drawRoundRect(PlayerPalette.trackPlayed, Offset(0f, top), Size(filled, trackHeight), radius)
        }
        boostX?.let { x ->
            drawRect(
                PlayerPalette.contentMuted,
                Offset(x - 0.5f, top - 2f),
                Size(1f, trackHeight + 4f)
            )
        }
        if (hovered || dragging) {
            drawCircle(Color.Black.copy(alpha = 0.35f), THUMB_RADIUS.toPx() + 1f, Offset(filled, size.height / 2 + 1f))
            drawCircle(PlayerPalette.trackPlayed, THUMB_RADIUS.toPx(), Offset(filled, size.height / 2))
        }
    }
}

/** Volume: speaker icon (click mutes) and a 0–max slider with a stop mark at 100 % and amber boost above it. */
@Composable
internal fun PlayerVolumeControl(
    volume: Float,
    maxVolume: Float,
    muteIcon: ImageVector,
    volumeIcon: ImageVector,
    label: String,
    onVolume: (Float) -> Unit,
    onToggleMute: () -> Unit,
    sliderWidth: Dp = 96.dp,
) {
    PlayerIconButton(if (volume == 0f) muteIcon else volumeIcon, label, onClick = onToggleMute)
    VimivoSlider(
        value = volume,
        onValueChange = onVolume,
        valueRange = 0f..maxVolume,
        label = label,
        valueText = "${(volume * PERCENT).toInt()}%",
        resetValue = 1f,
        boostFrom = if (maxVolume > 1f) 1f else null,
        keyStep = VOLUME_KEY_STEP,
        modifier = Modifier.size(width = sliderWidth, height = HIT_HEIGHT),
    )
}

private val HIT_HEIGHT = 20.dp
private val THUMB_RADIUS = 6.dp
private const val KEY_STEPS = 20f
private const val VOLUME_KEY_STEP = 0.05f
private const val PERCENT = 100
