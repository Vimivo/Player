package dev.vimivo.desktop.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vimivo.core.model.MediaItem
import dev.vimivo.desktop.DesktopArtwork
import dev.vimivo.desktop.resources.Res
import dev.vimivo.desktop.resources.player_back_to_library
import dev.vimivo.desktop.resources.player_back_to_library_description
import dev.vimivo.desktop.resources.player_loading
import dev.vimivo.desktop.resources.player_now_playing
import dev.vimivo.desktop.resources.player_resume_dismiss
import dev.vimivo.desktop.resources.player_resume_from
import dev.vimivo.desktop.resources.player_start_over
import dev.vimivo.ui.theme.LocalReducedMotion
import org.jetbrains.compose.resources.stringResource

/** Shown instead of an empty black surface until the opened file's first frame plays. */
@Composable
internal fun PlayerLoadingCard(title: String, modifier: Modifier = Modifier) {
    val reducedMotion = LocalReducedMotion.current
    val pulse by rememberInfiniteTransition(label = "loading pulse").animateFloat(
        initialValue = LOADING_ALPHA_MIN,
        targetValue = if (reducedMotion) LOADING_ALPHA_MIN else 1f,
        animationSpec = infiniteRepeatable(tween(LOADING_PULSE_MILLIS), RepeatMode.Reverse),
        label = "loading alpha",
    )
    Box(modifier.background(MaterialTheme.colorScheme.scrim), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = GLASS_ALPHA),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                text = stringResource(Res.string.player_loading, title),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp).alpha(pulse),
            )
        }
    }
}

/** Non-blocking: playback already continues from the saved position; "Start over" rewinds. */
@Composable
internal fun ResumeToast(
    prompt: ResumePrompt,
    onStartOver: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = GLASS_ALPHA),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.player_resume_from, formatClock(prompt.positionMillis)),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            )
            TextButton(onClick = onStartOver) { Text(stringResource(Res.string.player_start_over)) }
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.player_resume_dismiss)) }
        }
    }
}

/** Header above the video stage: back to the library plus the playing title. */
@Composable
internal fun PlayerStageHeader(title: String?, onBackToLibrary: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBackToLibrary) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(Res.string.player_back_to_library_description),
            )
            Text(stringResource(Res.string.player_back_to_library), modifier = Modifier.padding(start = 6.dp))
        }
        Text(
            title.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** What an opened audio file shows instead of a video surface: artwork, title, and the way back. */
@Composable
internal fun AudioNowPlayingCard(media: MediaItem, loading: Boolean, onBackToLibrary: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopArtwork(uri = media.artworkUri, title = media.title, size = 160.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(Res.string.player_now_playing),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    media.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                listOfNotNull(media.artist, media.album).joinToString(" · ").takeIf(String::isNotBlank)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (loading) {
                    Text(
                        stringResource(Res.string.player_loading, media.title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            TextButton(onClick = onBackToLibrary) { Text(stringResource(Res.string.player_back_to_library)) }
        }
    }
}

/** H:MM:SS or M:SS with tabular-friendly zero padding. */
internal fun formatClock(millis: Long): String {
    val totalSeconds = (millis / MILLIS_PER_SECOND).coerceAtLeast(0)
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private const val GLASS_ALPHA = 0.86f
private const val LOADING_ALPHA_MIN = 0.55f
private const val LOADING_PULSE_MILLIS = 900
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
