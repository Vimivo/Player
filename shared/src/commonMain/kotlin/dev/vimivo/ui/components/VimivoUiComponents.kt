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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.vimivo.ui.theme.LocalReducedTransparency
import dev.vimivo.ui.theme.VimivoGlassVariant
import dev.vimivo.ui.theme.VimivoRadius
import dev.vimivo.ui.theme.VimivoSpacing

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
    val solid = reducedTransparency || variant == VimivoGlassVariant.SOLID_FALLBACK
    val containerColor = when {
        solid -> colors.surfaceContainerHigh
        else -> when (variant) {
            VimivoGlassVariant.FLOATING_PLAYER -> colors.surfaceContainerHigh.copy(alpha = 0.94f)
            VimivoGlassVariant.SHEET -> colors.surfaceContainer.copy(alpha = 0.96f)
            VimivoGlassVariant.CONTROL -> colors.surfaceContainerHighest.copy(alpha = 0.88f)
            else -> colors.surfaceContainer.copy(alpha = 0.90f)
        }
    }
    val radius = glassRadius(variant)
    val elevation = glassElevation(variant)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius),
        color = containerColor,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outline.copy(alpha = if (solid) 0.72f else 0.32f)),
        tonalElevation = elevation,
        shadowElevation = if (solid) 0.dp else elevation,
        content = content,
    )
}

private fun glassRadius(variant: VimivoGlassVariant) = when (variant) {
    VimivoGlassVariant.FLOATING_PLAYER, VimivoGlassVariant.SHEET -> VimivoRadius.xl
    VimivoGlassVariant.CONTROL -> VimivoRadius.pill
    VimivoGlassVariant.POPOVER, VimivoGlassVariant.NAVIGATION -> VimivoRadius.lg
    VimivoGlassVariant.SOLID_FALLBACK -> VimivoRadius.md
}

private fun glassElevation(variant: VimivoGlassVariant) = when (variant) {
    VimivoGlassVariant.FLOATING_PLAYER, VimivoGlassVariant.SHEET -> 8.dp
    VimivoGlassVariant.POPOVER -> 6.dp
    VimivoGlassVariant.NAVIGATION -> 3.dp
    else -> 1.dp
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
                    modifier = Modifier.padding(top = VimivoSpacing.sm).semantics {
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
                    modifier = Modifier.semantics {
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
            modifier = Modifier.semantics {
                role = Role.Button
                contentDescription = "Move $itemTitle up in order"
            },
        ) {
            Text("↑")
        }
        OutlinedButton(
            onClick = onMoveDown,
            enabled = canMoveDown,
            modifier = Modifier.semantics {
                role = Role.Button
                contentDescription = "Move $itemTitle down in order"
            },
        ) {
            Text("↓")
        }
        OutlinedButton(
            onClick = onRemove,
            modifier = Modifier.semantics {
                role = Role.Button
                contentDescription = "Remove $itemTitle from list"
            },
        ) {
            Text("Remove")
        }
    }
}
