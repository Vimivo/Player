@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@file:Suppress(
    "ArgumentListWrapping",
    "CyclomaticComplexMethod",
    "EmptyElseBlock",
    "ImportOrdering",
    "Indentation",
    "LongMethod",
    "LongParameterList",
    "MaximumLineLength",
    "MaxLineLength",
    "MultiLineIfElse",
    "TooManyFunctions",
    "Wrapping",
)

package dev.vimivo.platform.android.library.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.content.Intent
import android.graphics.Typeface
import android.util.TypedValue
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.ScreenLockLandscape
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import dev.vimivo.core.model.MediaItem
import dev.vimivo.platform.android.playback.PlaybackUiState
import dev.vimivo.platform.android.playback.PlaybackViewModel
import dev.vimivo.platform.android.ui.components.BottomSheet
import dev.vimivo.platform.android.ui.components.SettingsRow
import dev.vimivo.platform.android.ui.theme.LocalReducedMotion
import dev.vimivo.ui.components.VimivoGlassSurface
import dev.vimivo.ui.theme.VimivoGlassVariant
import dev.vimivo.settings.AppSettings
import dev.vimivo.settings.SubtitleAppearance
import dev.vimivo.settings.VideoRotationBehavior
import dev.vimivo.settings.SubtitleEdgeStyle
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot

private enum class VideoAspect(val label: String) {
    FIT("Fit"), FILL("Fill"), ORIGINAL("Original"), RATIO_16_9("16:9"), RATIO_4_3("4:3"), CROP("Crop")
}

@Composable
fun AdvancedVideoPlayer(
    player: PlaybackUiState,
    controller: Player?,
    viewModel: PlaybackViewModel,
    settings: AppSettings,
    onGestureSensitivity: (Float) -> Unit,
    onPreferredSubtitleLanguage: (String) -> Unit,
    onPreferredAudioLanguage: (String) -> Unit,
    onSubtitlesEnabled: (Boolean) -> Unit,
    onSubtitleDelay: (Long) -> Unit,
    onAudioDelay: (Long) -> Unit,
    onSubtitleAppearance: (SubtitleAppearance) -> Unit,
    onPlayerState: (Boolean, Boolean) -> Unit,
    onEnterPictureInPicture: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val inPictureInPicture = activity?.isInPictureInPictureMode == true
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val supportsPictureInPicture = remember(context) {
        context.packageManager.hasSystemFeature("android.software.picture_in_picture")
    }
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onSubtitlesEnabled(true)
            viewModel.loadExternalSubtitle(it)
        }
    }
    val orientation = LocalConfiguration.current.orientation
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var orientationLocked by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
    var aspect by remember(settings.videoAspectPreference) {
        mutableStateOf(runCatching { VideoAspect.valueOf(settings.videoAspectPreference.name) }.getOrDefault(VideoAspect.FIT))
    }
    var zoom by remember { mutableFloatStateOf(1f) }
    var seekPreview by remember { mutableLongStateOf(player.positionMillis) }
    var dragging by remember { mutableStateOf(false) }
    var autoAudioAppliedFor by remember { mutableStateOf<String?>(null) }
    var autoSubtitleAppliedFor by remember { mutableStateOf<String?>(null) }
    var autoExternalAppliedFor by remember { mutableStateOf<String?>(null) }
    val media = player.current?.toDomain()

    LaunchedEffect(media?.id?.value) {
        if (media != null) viewModel.setSpeed(settings.defaultVideoSpeed)
    }

    LaunchedEffect(media?.id?.value, player.audioTracks, settings.preferredAudioLanguage) {
        val mediaId = media?.id?.value ?: return@LaunchedEffect
        if (autoAudioAppliedFor != mediaId && settings.preferredAudioLanguage.isNotBlank()) {
            player.audioTracks.firstOrNull {
                it.language.equals(settings.preferredAudioLanguage, ignoreCase = true)
            }?.let {
                viewModel.selectAudioTrack(it.id)
                autoAudioAppliedFor = mediaId
            }
        }
    }
    LaunchedEffect(media?.id?.value, player.subtitleTracks, settings.preferredSubtitleLanguage) {
        val mediaId = media?.id?.value ?: return@LaunchedEffect
        if (settings.subtitlesEnabled && autoSubtitleAppliedFor != mediaId && settings.preferredSubtitleLanguage.isNotBlank()) {
            player.subtitleTracks.firstOrNull {
                it.language.equals(settings.preferredSubtitleLanguage, ignoreCase = true)
            }?.let {
                viewModel.selectSubtitleTrack(it.id)
                autoSubtitleAppliedFor = mediaId
            }
        }
    }
    LaunchedEffect(media?.id?.value, player.subtitleTracks, settings.subtitlesEnabled) {
        if (!settings.subtitlesEnabled && player.subtitleTracks.any { it.isSelected }) {
            viewModel.selectSubtitleTrack(null)
        }
    }
    LaunchedEffect(media?.id?.value, player.externalSubtitleCandidates, settings.subtitlesEnabled) {
        val mediaId = media?.id?.value ?: return@LaunchedEffect
        if (settings.subtitlesEnabled && autoExternalAppliedFor != mediaId) {
            val match = player.externalSubtitleCandidates.firstOrNull {
                settings.preferredSubtitleLanguage.isNotBlank() &&
                    it.language.equals(settings.preferredSubtitleLanguage, ignoreCase = true)
            } ?: player.externalSubtitleCandidates.firstOrNull { it.language == null }
            match?.let {
                viewModel.loadExternalSubtitle(it)
                autoExternalAppliedFor = mediaId
            }
        }
    }
    LaunchedEffect(media?.id?.value, player.engine, settings.subtitleDelayMillis) {
        if (player.capabilities?.subtitleDelay == true) {
            viewModel.setSubtitleDelay(settings.subtitleDelayMillis)
        }
    }
    LaunchedEffect(media?.id?.value, player.engine, settings.audioDelayMillis) {
        if (player.capabilities?.audioDelay == true) {
            viewModel.setAudioDelay(settings.audioDelayMillis)
        }
    }

    SideEffect { onPlayerState(true, player.isPlaying) }
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = when (settings.videoRotationBehavior) {
            VideoRotationBehavior.FOLLOW_SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            VideoRotationBehavior.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            VideoRotationBehavior.LOCK_CURRENT -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }
        activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView).hide(WindowInsetsCompat.Type.systemBars()) }
        onDispose {
            onPlayerState(false, false)
            activity?.requestedOrientation = originalOrientation
            activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
    LaunchedEffect(controlsVisible, player.isPlaying, locked) {
        if (controlsVisible && player.isPlaying && !locked) {
            delay(3_500)
            controlsVisible = false
        }
    }
    BackHandler {
        if (locked) locked = false else onDismiss()
    }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Box(
            Modifier.fillMaxSize()
                .padding(horizontal = 20.dp)
                .pointerInput(locked, settings.videoGestureSensitivity, player.durationMillis) {
                    if (!locked) detectDragGestures(
                        onDragStart = { dragging = true; seekPreview = player.positionMillis },
                        onDragEnd = { if (dragging) viewModel.seekTo(seekPreview); dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, drag ->
                        change.consume()
                        val horizontal = abs(drag.x) > abs(drag.y)
                        if (horizontal && player.durationMillis > 0) {
                            val delta = (drag.x / size.width * player.durationMillis * settings.videoGestureSensitivity).toLong()
                            seekPreview = (seekPreview + delta).coerceIn(0, player.durationMillis)
                        } else if (!horizontal && change.position.x >= size.width / 2) {
                            val delta = -drag.y / size.height * settings.videoGestureSensitivity
                            audioManager.adjustVolumeFraction(delta)
                        }
                    }
                }
                .pointerInput(locked) {
                    if (!locked) detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            val interval = settings.doubleTapSeekSeconds * 1_000L
                            val seek = if (offset.x < size.width / 2) -interval else interval
                            viewModel.seekTo((player.positionMillis + seek).coerceIn(0, player.durationMillis))
                            controlsVisible = true
                        },
                    )
                }
                .pointerInput(locked) {
                    if (!locked) awaitEachGesture {
                        do {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.filter { it.pressed }
                            if (pointers.size >= 2) {
                                val first = pointers[0]
                                val second = pointers[1]
                                val previousDistance = hypot(
                                    first.previousPosition.x - second.previousPosition.x,
                                    first.previousPosition.y - second.previousPosition.y,
                                )
                                val currentDistance = hypot(
                                    first.position.x - second.position.x,
                                    first.position.y - second.position.y,
                                )
                                if (previousDistance > 0f) {
                                    zoom = (zoom * currentDistance / previousDistance).coerceIn(1f, 3f)
                                }
                                pointers.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            val videoModifier = when (aspect) {
                VideoAspect.RATIO_16_9 -> Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(16f / 9f)
                VideoAspect.RATIO_4_3 -> Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(4f / 3f)
                else -> Modifier.fillMaxSize()
            }
            if (controller != null) AndroidView(
                factory = { PlayerView(it).apply { useController = false; this.player = controller } },
                update = {
                    it.player = controller
                    it.resizeMode = aspect.resizeMode
                    it.subtitleView?.applySubtitleAppearance(settings.subtitleAppearance)
                },
                modifier = videoModifier.graphicsLayer(scaleX = zoom, scaleY = zoom),
            )
            player.errorMessage?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.align(Alignment.Center)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f))
                        .padding(16.dp),
                )
            }
            if (dragging) Text(
                "${formatVideoTime(seekPreview)} / ${formatVideoTime(player.durationMillis)}",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.65f)).padding(16.dp),
            )
            if (!inPictureInPicture && locked) IconButton({ locked = false }, Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Outlined.LockOpen, "Unlock controls", tint = Color.White)
            } else if (!inPictureInPicture) {
                val controls: @Composable () -> Unit = {
                    VideoControls(
                        player = player,
                        media = media,
                        orientationLocked = orientationLocked,
                        supportsPictureInPicture = supportsPictureInPicture,
                        onClose = onDismiss,
                        onToggle = viewModel::togglePlayback,
                        onStop = viewModel::stop,
                        onPrevious = viewModel::previous,
                        onNext = viewModel::next,
                        onSeek = viewModel::seekTo,
                        onLock = { locked = true; controlsVisible = false },
                        onOrientationLock = {
                    orientationLocked = !orientationLocked
                    activity?.requestedOrientation = if (orientationLocked) {
                        if (orientation == Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else when (settings.videoRotationBehavior) {
                        VideoRotationBehavior.FOLLOW_SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        VideoRotationBehavior.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                        VideoRotationBehavior.LOCK_CURRENT -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
                    }
                        },
                        onPip = onEnterPictureInPicture,
                        onMenu = { menuVisible = true },
                    )
                }
                if (LocalReducedMotion.current) {
                    if (controlsVisible) controls()
                } else AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut()) { controls() }
            }
        }
    }
    if (menuVisible) VideoSettingsSheet(
        player,
        media,
        aspect,
        settings,
        onAspect = { aspect = it; zoom = 1f },
        onSensitivity = onGestureSensitivity,
        onPreferredSubtitleLanguage = onPreferredSubtitleLanguage,
        onPreferredAudioLanguage = onPreferredAudioLanguage,
        onSubtitlesEnabled = onSubtitlesEnabled,
        onSubtitleDelay = onSubtitleDelay,
        onAudioDelay = onAudioDelay,
        onSubtitleAppearance = onSubtitleAppearance,
        onOpenSubtitle = {
            subtitlePicker.launch(
                arrayOf("application/x-subrip", "text/vtt", "text/x-ssa", "text/plain", "application/octet-stream"),
            )
        },
        viewModel = viewModel,
        onDismiss = { menuVisible = false },
    )
}

@Composable
private fun VideoControls(
    player: PlaybackUiState,
    media: MediaItem?,
    orientationLocked: Boolean,
    supportsPictureInPicture: Boolean,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onLock: () -> Unit,
    onOrientationLock: () -> Unit,
    onPip: () -> Unit,
    onMenu: () -> Unit,
) {
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        VimivoGlassSurface(VimivoGlassVariant.NAVIGATION, Modifier.fillMaxWidth().padding(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(Icons.Outlined.Close, "Close video", tint = Color.White) }
            Text(media?.title.orEmpty(), color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
            if (supportsPictureInPicture) IconButton(onPip) { Icon(Icons.Outlined.PictureInPicture, "Picture in picture", tint = Color.White) }
            IconButton(onOrientationLock) {
                Icon(Icons.Outlined.ScreenLockLandscape, if (orientationLocked) "Unlock orientation" else "Lock orientation", tint = Color.White)
            }
            IconButton(onMenu) { Icon(Icons.Outlined.MoreVert, "Video settings", tint = Color.White) }
        }
        }
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onPrevious) { Icon(Icons.Outlined.SkipPrevious, "Previous", tint = Color.White) }
            IconButton(onToggle) { Icon(if (player.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (player.isPlaying) "Pause" else "Play", tint = Color.White) }
            IconButton(onStop) { Icon(Icons.Outlined.Stop, "Stop playback", tint = Color.White) }
            IconButton(onNext) { Icon(Icons.Outlined.SkipNext, "Next", tint = Color.White) }
        }
        VimivoGlassSurface(VimivoGlassVariant.CONTROL, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            LinearProgressIndicator(
                progress = { if (player.durationMillis > 0) player.bufferedPositionMillis.toFloat() / player.durationMillis else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Color.White.copy(alpha = 0.45f),
                trackColor = Color.White.copy(alpha = 0.16f),
            )
            Slider(
                value = player.positionMillis.toFloat().coerceAtMost(player.durationMillis.toFloat()),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..player.durationMillis.coerceAtLeast(1).toFloat(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${formatVideoTime(player.positionMillis)} / ${formatVideoTime(player.durationMillis)}", color = Color.White)
                Spacer(Modifier.weight(1f))
                IconButton(onLock) { Icon(Icons.Outlined.Lock, "Lock screen controls", tint = Color.White) }
            }
        }
        }
    }
}

@Composable
private fun VideoSettingsSheet(
    player: PlaybackUiState,
    media: MediaItem?,
    aspect: VideoAspect,
    settings: AppSettings,
    onAspect: (VideoAspect) -> Unit,
    onSensitivity: (Float) -> Unit,
    onPreferredSubtitleLanguage: (String) -> Unit,
    onPreferredAudioLanguage: (String) -> Unit,
    onSubtitlesEnabled: (Boolean) -> Unit,
    onSubtitleDelay: (Long) -> Unit,
    onAudioDelay: (Long) -> Unit,
    onSubtitleAppearance: (SubtitleAppearance) -> Unit,
    onOpenSubtitle: () -> Unit,
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
) {
    BottomSheet(onDismiss) {
        Text("Video settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(20.dp))
        MenuHeading(Icons.Outlined.Speed, "Playback speed")
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { speed ->
                AssistChip({ viewModel.setSpeed(speed) }, { Text("$speed×") })
            }
        }
        MenuHeading(Icons.AutoMirrored.Outlined.VolumeUp, "Audio tracks")
        if (player.audioTracks.isEmpty()) Text("No selectable audio tracks reported", Modifier.padding(horizontal = 20.dp))
        player.audioTracks.forEach { track ->
            SettingsRow(Icons.AutoMirrored.Outlined.VolumeUp, track.label, track.language ?: "Language not reported", track.isSelected) {
                viewModel.selectAudioTrack(track.id)
                track.language?.let(onPreferredAudioLanguage)
            }
        }
        if (player.capabilities?.audioDelay == true) {
            DelayControl("Audio synchronization", settings.audioDelayMillis) { value ->
                onAudioDelay(value)
                viewModel.setAudioDelay(value)
            }
        } else Text("Audio delay is unavailable with this engine", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        MenuHeading(Icons.Outlined.Subtitles, "Subtitles")
        SettingsRow(Icons.Outlined.Subtitles, "Off", checked = !settings.subtitlesEnabled) {
            onSubtitlesEnabled(false)
            viewModel.selectSubtitleTrack(null)
        }
        player.subtitleTracks.forEach { track ->
            SettingsRow(Icons.Outlined.Subtitles, track.label, track.language ?: "Language not reported", track.isSelected) {
                viewModel.selectSubtitleTrack(track.id)
                onSubtitlesEnabled(true)
                track.language?.let(onPreferredSubtitleLanguage)
            }
        }
        if (player.capabilities?.externalSubtitles == true) {
            SettingsRow(Icons.Outlined.Subtitles, "Open subtitle file", "SRT, ASS, SSA, or WebVTT", onClick = onOpenSubtitle)
            player.externalSubtitleCandidates.forEach { subtitle ->
                SettingsRow(
                    Icons.Outlined.Subtitles,
                    subtitle.label,
                    subtitle.language?.let { "Detected · $it" } ?: "Detected in an authorized folder",
                    onClick = {
                        onSubtitlesEnabled(true)
                        viewModel.loadExternalSubtitle(subtitle)
                    },
                )
            }
        } else Text("External subtitles are unavailable with this engine", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (player.capabilities?.subtitleDelay == true) {
            DelayControl("Subtitle synchronization", settings.subtitleDelayMillis) { value ->
                onSubtitleDelay(value)
                viewModel.setSubtitleDelay(value)
            }
        } else Text("Subtitle delay is unavailable with this engine", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        player.trackErrorMessage?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
        }
        if (player.capabilities?.subtitleAppearance == true) {
            SubtitleAppearanceControls(settings.subtitleAppearance, onSubtitleAppearance)
        } else {
            Text(
                "Subtitle appearance is controlled by the active native renderer. ASS/SSA styling support varies by engine.",
                Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MenuHeading(Icons.Outlined.AspectRatio, "Aspect ratio")
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            VideoAspect.entries.forEach { value ->
                AssistChip({ onAspect(value) }, { Text(if (value == aspect) "✓ ${value.label}" else value.label) })
            }
        }
        MenuHeading(Icons.Outlined.FitScreen, "Gesture sensitivity")
        Slider(settings.videoGestureSensitivity, onSensitivity, valueRange = 0.5f..2f, modifier = Modifier.padding(horizontal = 20.dp))
        MenuHeading(Icons.Outlined.Repeat, "A–B repeat")
        val abStart = player.abRepeatStartMillis
        val abEnd = player.abRepeatEndMillis
        when {
            abStart == null -> SettingsRow(Icons.Outlined.Repeat, "Set A at current position", onClick = viewModel::setAbRepeatStart)
            abEnd == null -> {
                SettingsRow(Icons.Outlined.Repeat, "A: ${formatVideoTime(abStart)}", "Choose B at least one second later") {}
                SettingsRow(Icons.Outlined.Repeat, "Set B at current position", onClick = viewModel::setAbRepeatEnd)
                SettingsRow(Icons.Outlined.Close, "Clear A–B repeat", onClick = viewModel::clearAbRepeat)
            }
            else -> {
                SettingsRow(Icons.Outlined.Repeat, "Repeating ${formatVideoTime(abStart)} – ${formatVideoTime(abEnd)}", onClick = viewModel::clearAbRepeat)
                Text("Timing follows playback position; it is not frame-accurate.", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        MenuHeading(Icons.Outlined.Info, "Decoder and video information")
        InfoLine("Engine", player.engine?.name)
        InfoLine("Decoder", player.engine?.let { if (player.capabilities?.hardwareDecoding == true) "$it · hardware when available" else it.name })
        InfoLine("Codec", player.videoTracks.firstOrNull { it.isSelected }?.codec ?: player.videoTracks.firstOrNull()?.codec)
        InfoLine("Resolution", media?.let { item -> item.width?.let { width -> item.height?.let { height -> "$width × $height" } } })
        InfoLine("Frame rate", media?.frameRate?.let { "%.2f fps".format(it) })
        InfoLine("Duration", media?.durationMillis?.let(::formatVideoTime))
        InfoLine("Bitrate", media?.bitrate?.let { "${it / 1_000} kbps" })
        InfoLine("Audio codec", player.audioTracks.firstOrNull { it.isSelected }?.codec)
        InfoLine("Subtitle tracks", player.subtitleTracks.takeIf { it.isNotEmpty() }?.size?.toString())
        InfoLine("File size", media?.sizeBytes?.let(::formatVideoBytes))
    }
}

@Composable
private fun MenuHeading(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null)
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DelayControl(label: String, value: Long, onChange: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("$label: ${if (value > 0) "+" else ""}$value ms", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip({ onChange((value - 500).coerceAtLeast(-10_000)) }, { Text("−0.5 s") })
            AssistChip({ onChange(0) }, { Text("Reset") })
            AssistChip({ onChange((value + 500).coerceAtMost(10_000)) }, { Text("+0.5 s") })
        }
    }
}

@Composable
private fun SubtitleAppearanceControls(
    appearance: SubtitleAppearance,
    onChange: (SubtitleAppearance) -> Unit,
) {
    MenuHeading(Icons.Outlined.Subtitles, "Subtitle appearance")
    Text("Size · ${appearance.textSizeSp.toInt()} sp", Modifier.padding(horizontal = 20.dp))
    Slider(
        appearance.textSizeSp,
        { onChange(appearance.copy(textSizeSp = it)) },
        valueRange = 12f..40f,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    Text("Position", Modifier.padding(horizontal = 20.dp))
    Slider(
        appearance.positionFraction,
        { onChange(appearance.copy(positionFraction = it)) },
        valueRange = 0.02f..0.35f,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip({ onChange(appearance.copy(textColorArgb = 0xFFFFFFFF)) }, { Text("White") })
        AssistChip({ onChange(appearance.copy(textColorArgb = 0xFFFFFF00)) }, { Text("Yellow") })
        AssistChip({ onChange(appearance.copy(backgroundColorArgb = 0x00000000)) }, { Text("No background") })
        AssistChip({ onChange(appearance.copy(backgroundColorArgb = 0x99000000)) }, { Text("Dark background") })
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SubtitleEdgeStyle.entries.forEach { style ->
            AssistChip(
                { onChange(appearance.copy(edgeStyle = style)) },
                { Text(if (appearance.edgeStyle == style) "✓ ${style.label}" else style.label) },
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value != null) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value)
    }
}

private val VideoAspect.resizeMode: Int get() = when (this) {
    VideoAspect.FIT, VideoAspect.RATIO_16_9, VideoAspect.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    VideoAspect.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    VideoAspect.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
    VideoAspect.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun AudioManager.adjustVolumeFraction(delta: Float) {
    val maximum = getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val current = getStreamVolume(AudioManager.STREAM_MUSIC)
    setStreamVolume(AudioManager.STREAM_MUSIC, (current + delta * maximum).toInt().coerceIn(0, maximum), 0)
}

private fun androidx.media3.ui.SubtitleView.applySubtitleAppearance(appearance: SubtitleAppearance) {
    setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, appearance.textSizeSp)
    setBottomPaddingFraction(appearance.positionFraction)
    setApplyEmbeddedStyles(true)
    setApplyEmbeddedFontSizes(true)
    setStyle(
        CaptionStyleCompat(
            appearance.textColorArgb.toInt(),
            appearance.backgroundColorArgb.toInt(),
            Color.Transparent.toArgb(),
            when (appearance.edgeStyle) {
                SubtitleEdgeStyle.NONE -> CaptionStyleCompat.EDGE_TYPE_NONE
                SubtitleEdgeStyle.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                SubtitleEdgeStyle.DROP_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            },
            appearance.edgeColorArgb.toInt(),
            Typeface.DEFAULT,
        ),
    )
}

private val SubtitleEdgeStyle.label: String get() = when (this) {
    SubtitleEdgeStyle.NONE -> "No outline"
    SubtitleEdgeStyle.OUTLINE -> "Outline"
    SubtitleEdgeStyle.DROP_SHADOW -> "Shadow"
}

private fun formatVideoTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return if (seconds >= 3_600) "%d:%02d:%02d".format(seconds / 3_600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun formatVideoBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "${bytes / 1_024} KB"
}
