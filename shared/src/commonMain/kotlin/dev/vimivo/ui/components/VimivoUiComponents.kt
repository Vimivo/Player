@file:Suppress("LongParameterList")

package dev.vimivo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.vimivo.ui.theme.LocalReducedTransparency
import dev.vimivo.ui.theme.VimivoGlassVariant
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
