@file:Suppress(
    "ArgumentListWrapping",
    "CyclomaticComplexMethod",
    "LongMethod",
    "MagicNumber",
    "MaximumLineLength",
    "MaxLineLength",
)

package dev.vimivo.ui.gallery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vimivo.ui.components.VimivoAccessibleSlider
import dev.vimivo.ui.components.VimivoBadge
import dev.vimivo.ui.components.VimivoBadgeVariant
import dev.vimivo.ui.components.VimivoButton
import dev.vimivo.ui.components.VimivoButtonVariant
import dev.vimivo.ui.components.VimivoEmptyState
import dev.vimivo.ui.components.VimivoEqualizerAnimation
import dev.vimivo.ui.components.VimivoErrorBanner
import dev.vimivo.ui.components.VimivoIconButton
import dev.vimivo.ui.components.VimivoIconButtonSize
import dev.vimivo.ui.components.VimivoMediaCard
import dev.vimivo.ui.components.VimivoMediaRow
import dev.vimivo.ui.theme.VimivoColors
import dev.vimivo.ui.theme.VimivoRadius
import dev.vimivo.ui.theme.VimivoSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComponentGalleryScreen(
    currentTheme: String = "Dark",
    onSelectTheme: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val colors = MaterialTheme.colorScheme

    var isPlaying by remember { mutableStateOf(true) }
    var seekPosition by remember { mutableFloatStateOf(64f) }
    var volume by remember { mutableFloatStateOf(0.85f) }
    var isFavorite by remember { mutableStateOf(true) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = colors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = VimivoSpacing.xl, vertical = VimivoSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(VimivoSpacing.xl),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Vimivo Design System",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "Calm, minimal, precision audio-video primitives",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }

                // Theme switcher
                Row(horizontalArrangement = Arrangement.spacedBy(VimivoSpacing.xs)) {
                    listOf("Dark", "AMOLED", "Light").forEach { themeName ->
                        FilterChip(
                            selected = currentTheme == themeName,
                            onClick = { onSelectTheme(themeName) },
                            label = { Text(themeName) },
                        )
                    }
                }
            }

            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))

            // Section 1: Color Tokens
            GallerySection(title = "1. Color Tokens & WCAG Contrast") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(VimivoSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(VimivoSpacing.md),
                ) {
                    ColorSwatch(name = "Background", color = colors.background, contrast = "18.1:1 (AAA)")
                    ColorSwatch(name = "Surface", color = colors.surface, contrast = "15.9:1 (AAA)")
                    ColorSwatch(name = "Elevated", color = colors.surfaceContainerHigh, contrast = "12.2:1 (AAA)")
                    ColorSwatch(name = "Neon Cyan", color = VimivoColors.NeonCyan, contrast = "13.5:1 (AAA)")
                    ColorSwatch(name = "Violet Light", color = VimivoColors.ElectricVioletLight, contrast = "7.2:1 (AAA)")
                    ColorSwatch(name = "Success", color = VimivoColors.Success, contrast = "4.8:1 (AA)")
                    ColorSwatch(name = "Warning", color = VimivoColors.Warning, contrast = "5.1:1 (AA)")
                    ColorSwatch(name = "Error", color = VimivoColors.Error, contrast = "4.9:1 (AA)")
                }
            }

            // Section 2: Action Buttons
            GallerySection(title = "2. Action Buttons") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(VimivoSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(VimivoSpacing.md),
                ) {
                    VimivoButton(
                        text = "Primary Play",
                        icon = Icons.Default.PlayArrow,
                        onClick = {},
                        variant = VimivoButtonVariant.PRIMARY,
                    )
                    VimivoButton(
                        text = "Secondary Glass",
                        icon = Icons.Outlined.FolderOpen,
                        onClick = {},
                        variant = VimivoButtonVariant.SECONDARY,
                    )
                    VimivoButton(
                        text = "Ghost Action",
                        onClick = {},
                        variant = VimivoButtonVariant.GHOST,
                    )
                    VimivoButton(
                        text = "Danger Remove",
                        onClick = {},
                        variant = VimivoButtonVariant.DANGER,
                    )
                    VimivoButton(
                        text = "Disabled",
                        onClick = {},
                        enabled = false,
                        variant = VimivoButtonVariant.PRIMARY,
                    )
                }
            }

            // Section 3: Icon Buttons & Equalizer
            GallerySection(title = "3. Icon Buttons & Animated Equalizer") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VimivoSpacing.md),
                ) {
                    VimivoIconButton(
                        icon = Icons.Outlined.Shuffle,
                        contentDescription = "Shuffle",
                        onClick = {},
                        size = VimivoIconButtonSize.SMALL,
                    )
                    VimivoIconButton(
                        icon = Icons.Outlined.FastRewind,
                        contentDescription = "Rewind",
                        onClick = {},
                        size = VimivoIconButtonSize.MEDIUM,
                    )
                    VimivoIconButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        onClick = { isPlaying = !isPlaying },
                        size = VimivoIconButtonSize.LARGE,
                        tint = colors.onPrimary,
                        backgroundColor = colors.primary,
                    )
                    VimivoIconButton(
                        icon = Icons.Outlined.FastForward,
                        contentDescription = "Forward",
                        onClick = {},
                        size = VimivoIconButtonSize.MEDIUM,
                    )
                    VimivoIconButton(
                        icon = Icons.Outlined.Repeat,
                        contentDescription = "Repeat",
                        onClick = {},
                        size = VimivoIconButtonSize.SMALL,
                    )
                    VimivoIconButton(
                        icon = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        onClick = {},
                        tint = VimivoColors.NeonCyan,
                        size = VimivoIconButtonSize.MEDIUM,
                    )

                    Spacer(Modifier.width(VimivoSpacing.lg))

                    // Equalizer preview
                    Surface(
                        shape = RoundedCornerShape(VimivoRadius.pill),
                        color = colors.surfaceContainerHigh,
                        border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.3f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            VimivoEqualizerAnimation(isPlaying = isPlaying)
                            Text(
                                text = if (isPlaying) "Playing" else "Paused",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurface,
                            )
                        }
                    }
                }
            }

            // Section 4: Technical Metadata Badges
            GallerySection(title = "4. Technical Spec Badges (Zero Marketing)") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(VimivoSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(VimivoSpacing.sm),
                ) {
                    VimivoBadge(text = "LOSSLESS", variant = VimivoBadgeVariant.CYAN)
                    VimivoBadge(text = "FLAC 192kHz / 24-bit", variant = VimivoBadgeVariant.VIOLET)
                    VimivoBadge(text = "4K HDR · 60 FPS", variant = VimivoBadgeVariant.DEFAULT)
                    VimivoBadge(text = "AV1 / OPUS", variant = VimivoBadgeVariant.DEFAULT)
                    VimivoBadge(text = "BIT-PERFECT", variant = VimivoBadgeVariant.SUCCESS)
                    VimivoBadge(text = "GAPLESS", variant = VimivoBadgeVariant.WARNING)
                }
            }

            // Section 5: Precision Sliders
            GallerySection(title = "5. Precision Seekbar & Volume Control") {
                Column(verticalArrangement = Arrangement.spacedBy(VimivoSpacing.md)) {
                    Text(
                        text = "Position: 01:04 / 04:32",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                    VimivoAccessibleSlider(
                        value = seekPosition,
                        onValueChange = { seekPosition = it },
                        valueRange = 0f..272f,
                        label = "Seek timeline",
                        formattedValue = "${seekPosition.toInt()} seconds",
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(VimivoSpacing.md),
                        modifier = Modifier.width(320.dp),
                    ) {
                        VimivoIconButton(
                            icon = Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = "Volume",
                            onClick = {},
                            size = VimivoIconButtonSize.SMALL,
                        )
                        VimivoAccessibleSlider(
                            value = volume,
                            onValueChange = { volume = it },
                            valueRange = 0f..1f,
                            label = "Volume",
                            formattedValue = "${(volume * 100).toInt()}%",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Section 6: Media Row & Card
            GallerySection(title = "6. Media Row & Media Card") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(VimivoSpacing.xl),
                ) {
                    // Left: Track rows
                    Column(
                        modifier = Modifier.weight(1.5f),
                        verticalArrangement = Arrangement.spacedBy(VimivoSpacing.xs),
                    ) {
                        VimivoMediaRow(
                            index = 1,
                            title = "Solaris (Original Mix)",
                            subtitle = "Calyx · Atmospheric Explorations",
                            durationText = "05:42",
                            technicalBadge = "FLAC 96/24",
                            isPlaying = true,
                            isFavorite = isFavorite,
                            onToggleFavorite = { isFavorite = !isFavorite },
                            onClick = {},
                        )
                        VimivoMediaRow(
                            index = 2,
                            title = "Lunar Horizon",
                            subtitle = "Calyx · Atmospheric Explorations",
                            durationText = "04:18",
                            technicalBadge = "FLAC 44/16",
                            isPlaying = false,
                            isFavorite = false,
                            onToggleFavorite = {},
                            onClick = {},
                        )
                    }

                    // Right: Album Card preview
                    VimivoMediaCard(
                        title = "Atmospheric Explorations",
                        subtitle = "Calyx · 2026 · 12 Tracks",
                        badge = "HI-RES",
                        onClick = {},
                        modifier = Modifier.width(180.dp),
                        artworkContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(VimivoColors.ElectricViolet.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "♪",
                                    fontSize = 44.sp,
                                    color = VimivoColors.NeonCyan,
                                )
                            }
                        },
                    )
                }
            }

            // Section 7: Feedback & Error States
            GallerySection(title = "7. Error, Loading & Empty States") {
                Column(verticalArrangement = Arrangement.spacedBy(VimivoSpacing.md)) {
                    VimivoErrorBanner(
                        title = "Playback Stream Offline",
                        message = "The direct stream endpoint is unreachable. " +
                            "Verify network connectivity or select local media.",
                        recoveryActionLabel = "Retry",
                        onRecoveryAction = {},
                    )
                    VimivoEmptyState(
                        title = "Your Library is Calm and Clean",
                        detail = "Open a local folder or drag and drop audio and video tracks to start playing.",
                        actionLabel = "Open Media",
                        onAction = {},
                    )
                }
            }

            Spacer(Modifier.height(VimivoSpacing.xxl))
        }
    }
}

@Composable
private fun GallerySection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VimivoSpacing.md)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun ColorSwatch(
    name: String,
    color: Color,
    contrast: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(VimivoRadius.md),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.width(150.dp),
    ) {
        Column(modifier = Modifier.padding(VimivoSpacing.sm)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(VimivoRadius.xs))
                    .background(color)
                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(VimivoRadius.xs)),
            )
            Spacer(Modifier.height(VimivoSpacing.xs))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = contrast,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
