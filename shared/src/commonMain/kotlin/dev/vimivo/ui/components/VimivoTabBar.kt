@file:Suppress("MatchingDeclarationName")

package dev.vimivo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.vimivo.ui.theme.LocalReducedMotion
import dev.vimivo.ui.theme.LocalReducedTransparency
import dev.vimivo.ui.theme.VimivoElevation
import dev.vimivo.ui.theme.VimivoIconSize
import dev.vimivo.ui.theme.VimivoShell
import dev.vimivo.ui.theme.VimivoSpacing

/** One destination in [VimivoTabBar]; [selectedIcon] is the filled variant shown while selected. */
@Immutable
data class VimivoTabItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * Floating glass capsule shared by the tab bar and the mini player so they read as one stack.
 *
 * With a [hazeState] the content behind it is blurred. Where blur is unavailable, or no state is supplied, a
 * translucent tint is drawn instead, and reduced transparency makes the surface opaque.
 */
@Composable
fun VimivoFloatingChrome(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val reducedTransparency = LocalReducedTransparency.current
    val shape = remember { RoundedCornerShape(VimivoShell.floatingChromeRadius) }
    val fallback = colors.surfaceContainerHigh.copy(alpha = FALLBACK_ALPHA)
    val style = remember(colors) {
        HazeStyle(
            backgroundColor = colors.surface,
            tint = HazeTint(colors.surfaceContainer.copy(alpha = BLUR_TINT_ALPHA)),
            blurRadius = 24.dp,
            noiseFactor = 0f,
            fallbackTint = HazeTint(fallback),
        )
    }
    val surface = when {
        reducedTransparency -> Modifier.background(colors.surfaceContainerHigh)
        hazeState != null -> Modifier.hazeEffect(hazeState, style)
        else -> Modifier.background(fallback)
    }
    val borderAlpha = if (reducedTransparency) OPAQUE_BORDER_ALPHA else GLASS_BORDER_ALPHA
    CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
        Box(
            modifier
                .shadow(VimivoElevation.level3, shape)
                .clip(shape)
                .then(surface)
                .border(1.dp, colors.outline.copy(alpha = borderAlpha), shape),
        ) {
            content()
        }
    }
}

/**
 * Floating capsule navigation with a spring-animated pill behind the selected icon and label.
 *
 * The bar consumes the navigation bar insets below itself; place it at the bottom of the screen and give
 * scrolling content enough bottom padding to pass behind it.
 */
@Composable
fun VimivoTabBar(
    items: List<VimivoTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val reducedMotion = LocalReducedMotion.current
    VimivoFloatingChrome(
        hazeState = hazeState,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(
                start = VimivoShell.floatingChromeMargin,
                end = VimivoShell.floatingChromeMargin,
                bottom = VimivoShell.floatingChromeGap,
            )
            .fillMaxWidth()
            .height(VimivoShell.tabBarHeight),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(VimivoSpacing.xs)) {
            val tabWidth = maxWidth / items.size.coerceAtLeast(1)
            val pillOffset by animateDpAsState(
                targetValue = tabWidth * selectedIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0)),
                animationSpec = if (reducedMotion) {
                    snap()
                } else {
                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                },
                label = "tab pill offset",
            )
            Box(
                Modifier
                    .offset { IntOffset(pillOffset.roundToPx(), 0) }
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colors.secondaryContainer),
            )
            Row(Modifier.fillMaxSize().selectableGroup()) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                        animationSpec = if (reducedMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow),
                        label = "tab content color",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = selected,
                                interactionSource = null,
                                indication = null,
                                role = Role.Tab,
                                onClick = {
                                    if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onSelect(index)
                                },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(VimivoIconSize.medium),
                        )
                        Text(
                            text = item.label,
                            color = contentColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private const val BLUR_TINT_ALPHA = 0.62f
private const val FALLBACK_ALPHA = 0.94f
private const val GLASS_BORDER_ALPHA = 0.24f
private const val OPAQUE_BORDER_ALPHA = 0.72f
