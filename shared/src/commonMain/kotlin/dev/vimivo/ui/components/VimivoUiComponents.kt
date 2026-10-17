@file:Suppress(
    "LongParameterList",
    "LongMethod",
    "CyclomaticComplexMethod",
    "MagicNumber",
    "TooManyFunctions",
)

package dev.vimivo.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vimivo.ui.theme.LocalReducedMotion
import dev.vimivo.ui.theme.LocalReducedTransparency
import dev.vimivo.ui.theme.VimivoColors
import dev.vimivo.ui.theme.VimivoGlassVariant
import dev.vimivo.ui.theme.VimivoRadius
import dev.vimivo.ui.theme.VimivoSpacing
import dev.vimivo.ui.theme.resolveGlassTokens

/**
 * Lightweight Aurora Glass material. It deliberately avoids live backdrop blur so scrolling and
 * native video rendering keep a predictable GPU budget. Reduced-transparency and high-contrast
 * modes resolve to an opaque surface.
 */
@Composable
fun VimivoGlassSurface(
    variant: VimivoGlassVariant,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reducedTransparency = LocalReducedTransparency.current
    val colors = MaterialTheme.colorScheme
    val tokens = remember(variant, reducedTransparency) { resolveGlassTokens(variant, reducedTransparency) }
    val baseColor = remember(variant, colors) {
        when (variant) {
            VimivoGlassVariant.FLOATING_PLAYER -> colors.surfaceContainerHigh
            VimivoGlassVariant.CONTROL -> colors.surfaceContainerHighest
            else -> colors.surfaceContainer
        }
    }
    val shape = remember(tokens.radius) { RoundedCornerShape(tokens.radius) }
    val border = remember(colors.outline, tokens.borderAlpha) {
        BorderStroke(1.dp, colors.outline.copy(alpha = tokens.borderAlpha))
    }
    val surfaceColor = remember(tokens.opaque, tokens.containerAlpha, baseColor, colors.surfaceContainerHigh) {
        if (tokens.opaque) colors.surfaceContainerHigh else baseColor.copy(alpha = tokens.containerAlpha)
    }
    Surface(
        modifier = modifier,
        shape = shape,
        color = surfaceColor,
        contentColor = colors.onSurface,
        border = border,
        tonalElevation = tokens.elevation,
        shadowElevation = tokens.elevation,
        content = content,
    )
}

@Composable
fun VimivoAccessibleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
    formattedValue: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
) {
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        modifier = modifier.semantics {
            contentDescription = label
            stateDescription = formattedValue
            progressBarRangeInfo = ProgressBarRangeInfo(
                current = value.coerceIn(valueRange.start, valueRange.endInclusive),
                range = valueRange,
                steps = steps,
            )
        },
    )
}

@Composable
fun VimivoEmptyState(
    title: String,
    detail: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(VimivoSpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VimivoSpacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.padding(top = VimivoSpacing.sm).clearAndSetSemantics {
                        role = Role.Button
                        contentDescription = actionLabel
                    },
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun VimivoLoadingState(
    message: String = "Loading...",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(VimivoSpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VimivoSpacing.md),
            modifier = Modifier.semantics {
                contentDescription = message
            },
        ) {
            CircularProgressIndicator()
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun VimivoErrorBanner(
    title: String,
    message: String,
    recoveryActionLabel: String? = null,
    onRecoveryAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = modifier.fillMaxWidth().padding(VimivoSpacing.sm).semantics {
            contentDescription = "Alert: $title. $message"
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(VimivoSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VimivoSpacing.md),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(VimivoSpacing.xxs)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (recoveryActionLabel != null && onRecoveryAction != null) {
                Button(
                    onClick = onRecoveryAction,
                    modifier = Modifier.clearAndSetSemantics {
                        role = Role.Button
                        contentDescription = recoveryActionLabel
                    },
                ) {
                    Text(recoveryActionLabel)
                }
            }
        }
    }
}

@Composable
fun VimivoQueueReorderControls(
    itemTitle: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(VimivoSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onMoveUp,
            enabled = canMoveUp,
            modifier = Modifier.clearAndSetSemantics {
                role = Role.Button
                contentDescription = "Move $itemTitle up in order"
            },
        ) {
            Text("↑")
        }
        OutlinedButton(
            onClick = onMoveDown,
            enabled = canMoveDown,
            modifier = Modifier.clearAndSetSemantics {
                role = Role.Button
                contentDescription = "Move $itemTitle down in order"
            },
        ) {
            Text("↓")
        }
        OutlinedButton(
            onClick = onRemove,
            modifier = Modifier.clearAndSetSemantics {
                role = Role.Button
                contentDescription = "Remove $itemTitle from list"
            },
        ) {
            Text("Remove")
        }
    }
}

@Composable
fun VimivoSettingsSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(VimivoSpacing.xxs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun VimivoSettingsRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = VimivoSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = VimivoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(VimivoSpacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        control()
    }
}

@Composable
fun VimivoConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    cancelLabel: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = confirmLabel
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = cancelLabel
                },
            ) {
                Text(cancelLabel)
            }
        },
    )
}

enum class VimivoButtonVariant {
    PRIMARY,
    SECONDARY,
    GHOST,
    DANGER,
}

@Composable
fun VimivoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: VimivoButtonVariant = VimivoButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(VimivoRadius.pill)

    when (variant) {
        VimivoButtonVariant.PRIMARY -> {
            Button(
                onClick = onClick,
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    disabledContainerColor = colors.surfaceContainerHighest,
                    disabledContentColor = colors.onSurfaceVariant.copy(alpha = 0.4f),
                ),
                modifier = modifier.height(40.dp).semantics {
                    role = Role.Button
                    contentDescription = text
                },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VimivoSpacing.xs),
                ) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
        VimivoButtonVariant.SECONDARY -> {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                shape = shape,
                border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = colors.surfaceContainerHigh.copy(alpha = 0.6f),
                    contentColor = colors.onSurface,
                    disabledContentColor = colors.onSurfaceVariant.copy(alpha = 0.4f),
                ),
                modifier = modifier.height(40.dp).semantics {
                    role = Role.Button
                    contentDescription = text
                },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VimivoSpacing.xs),
                ) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
        VimivoButtonVariant.GHOST -> {
            Button(
                onClick = onClick,
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = colors.onSurface,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = colors.onSurfaceVariant.copy(alpha = 0.4f),
                ),
                modifier = modifier.height(40.dp).semantics {
                    role = Role.Button
                    contentDescription = text
                },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VimivoSpacing.xs),
                ) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        VimivoButtonVariant.DANGER -> {
            Button(
                onClick = onClick,
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.error,
                    contentColor = colors.onError,
                ),
                modifier = modifier.height(40.dp).semantics {
                    role = Role.Button
                    contentDescription = text
                },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VimivoSpacing.xs),
                ) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

enum class VimivoIconButtonSize(val buttonSize: Dp, val iconSize: Dp) {
    SMALL(32.dp, 16.dp),
    MEDIUM(40.dp, 20.dp),
    LARGE(48.dp, 24.dp),
}

@Composable
fun VimivoIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: VimivoIconButtonSize = VimivoIconButtonSize.MEDIUM,
    enabled: Boolean = true,
    tint: Color? = null,
    backgroundColor: Color = Color.Transparent,
) {
    val colors = MaterialTheme.colorScheme
    val resolvedTint = tint ?: colors.onSurface

    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(size.buttonSize)
            .clip(CircleShape)
            .background(backgroundColor),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = resolvedTint,
            disabledContentColor = colors.onSurfaceVariant.copy(alpha = 0.38f),
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(size.iconSize),
        )
    }
}

enum class VimivoBadgeVariant {
    DEFAULT,
    CYAN,
    VIOLET,
    SUCCESS,
    WARNING,
}

@Composable
fun VimivoBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: VimivoBadgeVariant = VimivoBadgeVariant.DEFAULT,
) {
    val colors = MaterialTheme.colorScheme
    val (bgColor, textColor, borderColor) = when (variant) {
        VimivoBadgeVariant.DEFAULT -> Triple(
            colors.surfaceContainerHigh.copy(alpha = 0.8f),
            colors.onSurfaceVariant,
            colors.outline.copy(alpha = 0.4f),
        )
        VimivoBadgeVariant.CYAN -> Triple(
            VimivoColors.NeonCyan.copy(alpha = 0.12f),
            VimivoColors.NeonCyan,
            VimivoColors.NeonCyan.copy(alpha = 0.35f),
        )
        VimivoBadgeVariant.VIOLET -> Triple(
            VimivoColors.ElectricVioletLight.copy(alpha = 0.12f),
            VimivoColors.ElectricVioletLight,
            VimivoColors.ElectricVioletLight.copy(alpha = 0.35f),
        )
        VimivoBadgeVariant.SUCCESS -> Triple(
            VimivoColors.Success.copy(alpha = 0.12f),
            VimivoColors.Success,
            VimivoColors.Success.copy(alpha = 0.35f),
        )
        VimivoBadgeVariant.WARNING -> Triple(
            VimivoColors.Warning.copy(alpha = 0.12f),
            VimivoColors.Warning,
            VimivoColors.Warning.copy(alpha = 0.35f),
        )
    }

    Surface(
        shape = RoundedCornerShape(VimivoRadius.xs),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            ),
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Animated 3-bar jumping equalizer to indicate playback activity.
 * Respects [LocalReducedMotion] by rendering static bars when reduced motion is preferred.
 */
@Composable
fun VimivoEqualizerAnimation(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val reducedMotion = LocalReducedMotion.current
    val infiniteTransition = rememberInfiniteTransition(label = "EqualizerTransition")

    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Bar1",
    )
    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Bar2",
    )
    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Bar3",
    )

    val actualH1 = if (isPlaying && !reducedMotion) bar1Height else 0.4f
    val actualH2 = if (isPlaying && !reducedMotion) bar2Height else 0.8f
    val actualH3 = if (isPlaying && !reducedMotion) bar3Height else 0.5f

    Row(
        modifier = modifier.size(width = 16.dp, height = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp * actualH1)
                .clip(RoundedCornerShape(1.5.dp))
                .background(color),
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp * actualH2)
                .clip(RoundedCornerShape(1.5.dp))
                .background(color),
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp * actualH3)
                .clip(RoundedCornerShape(1.5.dp))
                .background(color),
        )
    }
}

@Composable
fun VimivoMediaCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    artworkContent: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(VimivoRadius.md),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
        tonalElevation = 1.dp,
        modifier = modifier.semantics {
            role = Role.Button
            contentDescription = "$title, $subtitle"
        },
    ) {
        Column(modifier = Modifier.padding(VimivoSpacing.sm)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(VimivoRadius.sm))
                    .background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (artworkContent != null) {
                    artworkContent()
                }
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(VimivoSpacing.xs),
                    ) {
                        VimivoBadge(text = badge, variant = VimivoBadgeVariant.DEFAULT)
                    }
                }
            }
            Spacer(Modifier.height(VimivoSpacing.sm))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun VimivoMediaRow(
    title: String,
    subtitle: String,
    durationText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
    isPlaying: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    technicalBadge: String? = null,
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(VimivoRadius.sm),
        color = if (isPlaying) colors.primary.copy(alpha = 0.08f) else Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics {
                role = Role.Button
                contentDescription = "$title by $subtitle, duration $durationText"
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = VimivoSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VimivoSpacing.md),
        ) {
            // Index or Equalizer
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isPlaying) {
                    VimivoEqualizerAnimation(isPlaying = true)
                } else if (index != null) {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            // Title and Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = if (isPlaying) colors.primary else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Optional technical badge (e.g. "FLAC", "192kHz")
            if (technicalBadge != null) {
                VimivoBadge(text = technicalBadge, variant = VimivoBadgeVariant.DEFAULT)
            }

            // Duration
            Text(
                text = durationText,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )

            // Favorite button
            if (onToggleFavorite != null) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(28.dp),
                ) {
                    Text(
                        text = if (isFavorite) "★" else "☆",
                        color = if (isFavorite) colors.primary else colors.onSurfaceVariant,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}
