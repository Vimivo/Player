package dev.vimivo.desktop.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Every text in the panel: one line, no per-character wrapping, ellipsis when too long (spec §2.4). */
@Composable
internal fun PanelText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PlayerPalette.content,
    style: TextStyle = PANEL_TEXT,
    textAlign: TextAlign? = null,
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}

/** 11 sp uppercase, letter-spaced, 50 % white; 16 dp above. */
@Composable
internal fun PanelSection(title: String) {
    Spacer(Modifier.height(16.dp))
    PanelText(
        title.uppercase(),
        color = PlayerPalette.contentMuted,
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/**
 * Label (fixed 120 dp, left) and control (fills the rest). When the panel is too narrow for both, the label stacks
 * above the control.
 */
@Composable
internal fun PanelRow(label: String, content: @Composable RowScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (maxWidth >= STACK_BELOW) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PanelText(label, modifier = Modifier.width(LABEL_WIDTH), color = PlayerPalette.contentMuted)
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = content)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PanelText(label, color = PlayerPalette.contentMuted)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = content)
            }
        }
    }
}

/** `[ − ]  +150 ms  [ + ]  ⟲` — never a vertical pill. */
@Composable
internal fun RowScope.DelayStepper(
    label: String,
    millis: Long,
    stepMillis: Long,
    onStep: (deltaMillis: Long) -> Unit,
    onReset: () -> Unit,
) {
    PlayerIconButton(Icons.Outlined.Remove, "$label −$stepMillis ms") { onStep(-stepMillis) }
    PanelText(
        "%+d ms".format(millis),
        modifier = Modifier.widthIn(min = 76.dp).weight(1f, fill = false),
        style = PANEL_TEXT.copy(fontFeatureSettings = "tnum"),
        textAlign = TextAlign.Center,
    )
    PlayerIconButton(Icons.Outlined.Add, "$label +$stepMillis ms") { onStep(stepMillis) }
    if (millis != 0L) PlayerIconButton(Icons.Outlined.RestartAlt, "Reset $label", onClick = onReset)
}

/** A compact segmented control for a few short choices. */
@Composable
internal fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** Relative widths; labels of different lengths (tabs) get room in proportion. */
    weight: (T) -> Float = { 1f },
) {
    Row(
        modifier
            .clip(SEGMENT_SHAPE)
            .border(1.dp, PlayerPalette.glassBorder, SEGMENT_SHAPE)
            .background(PlayerPalette.hover),
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                Modifier
                    .weight(weight(option))
                    .height(28.dp)
                    .background(if (active) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelect(option) }
                    .semantics {
                        role = Role.Tab
                        this.selected = active
                    }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                PanelText(label(option), color = if (active) PlayerPalette.trackPlayed else PlayerPalette.content)
            }
        }
    }
}

/** A dropdown for longer lists (aspect presets, tracks). */
@Composable
internal fun <T> PanelDropdown(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    placeholder: String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(SEGMENT_SHAPE)
                .border(1.dp, PlayerPalette.glassBorder, SEGMENT_SHAPE)
                .background(PlayerPalette.hover)
                .clickable(enabled = options.isNotEmpty()) { open = true }
                .semantics { contentDescription = selected?.let(label) ?: placeholder }
                .padding(start = 10.dp, end = 4.dp)
                .height(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelText(selected?.let(label) ?: placeholder, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ArrowDropDown, null, tint = PlayerPalette.content, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { PanelText(label(option)) }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}

/** A small text button in the panel's style. */
@Composable
internal fun PanelButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .clip(SEGMENT_SHAPE)
            .border(1.dp, PlayerPalette.glassBorder, SEGMENT_SHAPE)
            .background(PlayerPalette.hover)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { role = Role.Button }
            .height(28.dp)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        PanelText(text, color = if (enabled) PlayerPalette.content else PlayerPalette.contentMuted)
    }
}

internal val PANEL_TEXT = TextStyle(fontSize = 13.sp)
private val LABEL_WIDTH = 120.dp
private val STACK_BELOW = 260.dp
private val SEGMENT_SHAPE = RoundedCornerShape(6.dp)
