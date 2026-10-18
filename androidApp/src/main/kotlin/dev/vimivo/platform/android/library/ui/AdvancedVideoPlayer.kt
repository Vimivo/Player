@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@file:Suppress(
    "ArgumentListWrapping",
    "CyclomaticComplexMethod",
    "EmptyElseBlock",
    "ImportOrdering",
    "Indentation",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
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
import android.provider.Settings
import android.util.TypedValue
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import dev.vimivo.settings.AppSettings
import dev.vimivo.settings.SubtitleAppearance
import dev.vimivo.settings.VideoRotationBehavior
import dev.vimivo.settings.SubtitleEdgeStyle
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import dev.vimivo.playback.PlaybackSpeeds

private enum class VideoAspect(val label: String) {
    FIT("Fit to screen"), FILL("Stretch"), ORIGINAL("Original size"), RATIO_16_9("16:9"), RATIO_4_3("4:3"), CROP("Crop")
}

/** Which part of the video options sheet is shown; quick actions open a focused panel. */
private enum class PlayerPanel { ALL, SPEED, AUDIO, SUBTITLES }

private enum class DragMode { NONE, SEEK, BRIGHTNESS, VOLUME, ZOOM }

/** Transient overlay shown while a gesture or quick action is in progress. */
private sealed interface GestureFeedback {
    data class Seek(val targetMillis: Long, val deltaMillis: Long, val durationMillis: Long) : GestureFeedback
    data class Level(val icon: ImageVector, val label: String, val fraction: Float) : GestureFeedback
    data class Message(val icon: ImageVector?, val text: String) : GestureFeedback
}

private const val HOLD_SPEED = 2f
private const val CONTROLS_TIMEOUT_MILLIS = 3_500L
private const val FEEDBACK_TIMEOUT_MILLIS = 700L
private const val DOUBLE_TAP_ACCUMULATE_MILLIS = 900L

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
    val view = LocalView.current
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
    var lockHintVisible by remember { mutableStateOf(false) }
    var orientationLocked by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    var aspect by remember(settings.videoAspectPreference) {
        mutableStateOf(runCatching { VideoAspect.valueOf(settings.videoAspectPreference.name) }.getOrDefault(VideoAspect.FIT))
    }
    var zoom by remember { mutableFloatStateOf(1f) }
    var showRemaining by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<GestureFeedback?>(null) }
    var gestureActive by remember { mutableStateOf(false) }
    var holdBoostFrom by remember { mutableStateOf<Float?>(null) }
    var doubleTapSide by remember { mutableIntStateOf(0) }
    var doubleTapTotalSeconds by remember { mutableIntStateOf(0) }
    var doubleTapAt by remember { mutableLongStateOf(0L) }
    var autoAudioAppliedFor by remember { mutableStateOf<String?>(null) }
    var autoSubtitleAppliedFor by remember { mutableStateOf<String?>(null) }
    var autoExternalAppliedFor by remember { mutableStateOf<String?>(null) }
    val media = player.current?.toDomain()
    val latestPlayer by rememberUpdatedState(player)

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

    SideEffect {
        onPlayerState(true, player.isPlaying)
        view.keepScreenOn = player.isPlaying
    }
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = settings.videoRotationBehavior.toRequestedOrientation()
        activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView).hide(WindowInsetsCompat.Type.systemBars()) }
        onDispose {
            onPlayerState(false, false)
            view.keepScreenOn = false
            activity?.requestedOrientation = originalOrientation
            activity?.setWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
    LaunchedEffect(controlsVisible, player.isPlaying, locked, panel, gestureActive) {
        val idle = !locked && panel == null && !gestureActive
        if (controlsVisible && player.isPlaying && idle) {
            delay(CONTROLS_TIMEOUT_MILLIS)
            controlsVisible = false
        }
    }
    LaunchedEffect(lockHintVisible) {
        if (lockHintVisible) {
            delay(CONTROLS_TIMEOUT_MILLIS)
            lockHintVisible = false
        }
    }
    LaunchedEffect(feedback, gestureActive) {
        if (feedback != null && !gestureActive) {
            delay(FEEDBACK_TIMEOUT_MILLIS)
            feedback = null
        }
    }
    LaunchedEffect(doubleTapAt) {
        if (doubleTapAt != 0L) {
            delay(DOUBLE_TAP_ACCUMULATE_MILLIS)
            doubleTapSide = 0
            doubleTapTotalSeconds = 0
        }
    }
    BackHandler {
        if (locked) lockHintVisible = true else onDismiss()
    }

    fun showMessage(icon: ImageVector?, text: String) {
        feedback = GestureFeedback.Message(icon, text)
    }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Box(
            Modifier.fillMaxSize()
                .pointerInput(locked) {
                    if (locked) {
                        detectTapGestures(onTap = { lockHintVisible = !lockHintVisible })
                    } else detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            val current = latestPlayer
                            val side = when {
                                offset.x < size.width * 0.4f -> -1
                                offset.x > size.width * 0.6f -> 1
                                else -> 0
                            }
                            if (side == 0) {
                                viewModel.togglePlayback()
                                showMessage(if (current.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (current.isPlaying) "Paused" else "Playing")
                            } else {
                                val interval = settings.doubleTapSeekSeconds
                                doubleTapTotalSeconds = if (doubleTapSide == side) doubleTapTotalSeconds + interval else interval
                                doubleTapSide = side
                                doubleTapAt = System.currentTimeMillis()
                                controlsVisible = false
                                val target = (current.positionMillis + side * interval * 1_000L).coerceIn(0, current.durationMillis.coerceAtLeast(0))
                                viewModel.seekTo(target)
                            }
                        },
                        onLongPress = {
                            val current = latestPlayer
                            if (current.isPlaying && holdBoostFrom == null) {
                                holdBoostFrom = current.speed
                                viewModel.setSpeed(HOLD_SPEED)
                                gestureActive = true
                                showMessage(Icons.Rounded.FastForward, "${formatSpeed(HOLD_SPEED)} speed")
                            }
                        },
                        onPress = {
                            tryAwaitRelease()
                            holdBoostFrom?.let { previous ->
                                viewModel.setSpeed(previous)
                                holdBoostFrom = null
                                gestureActive = false
                            }
                        },
                    )
                }
                .pointerInput(locked, settings.videoGestureSensitivity) {
                    if (locked) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val edgeGuard = 24.dp.toPx()
                        val fromEdge = down.position.y < edgeGuard || down.position.y > size.height - edgeGuard
                        var mode = DragMode.NONE
                        var travel = Offset.Zero
                        var seekStart = 0L
                        var seekTarget = 0L
                        var level = 0f
                        val volumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (pressed.size >= 2) {
                                if (mode != DragMode.ZOOM) {
                                    mode = DragMode.ZOOM
                                    gestureActive = true
                                    controlsVisible = false
                                }
                                zoom = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                                feedback = GestureFeedback.Message(Icons.Rounded.ZoomIn, "Zoom ${(zoom * 100).roundToInt()}%")
                                event.changes.forEach { it.consume() }
                            } else if (change != null && mode != DragMode.ZOOM && !fromEdge) {
                                val delta = change.positionChange()
                                if (mode == DragMode.NONE) {
                                    travel += delta
                                    if (travel.getDistance() > viewConfiguration.touchSlop) {
                                        val current = latestPlayer
                                        mode = when {
                                            abs(travel.x) > abs(travel.y) -> if (current.durationMillis > 0) DragMode.SEEK else DragMode.NONE
                                            down.position.x < size.width / 2 -> DragMode.BRIGHTNESS
                                            else -> DragMode.VOLUME
                                        }
                                        when (mode) {
                                            DragMode.SEEK -> {
                                                seekStart = current.positionMillis
                                                seekTarget = seekStart
                                            }
                                            DragMode.BRIGHTNESS -> level = activity?.currentWindowBrightness() ?: 0.5f
                                            DragMode.VOLUME -> level = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / volumeMax
                                            else -> Unit
                                        }
                                        gestureActive = mode != DragMode.NONE
                                        if (gestureActive) controlsVisible = false
                                    }
                                }
                                when (mode) {
                                    DragMode.SEEK -> {
                                        val duration = latestPlayer.durationMillis
                                        // A full-width swipe covers 90 seconds or a third of the video, whichever is longer.
                                        val span = maxOf(90_000L, duration / 3) * settings.videoGestureSensitivity
                                        seekTarget = (seekTarget + (delta.x / size.width * span).toLong()).coerceIn(0, duration)
                                        feedback = GestureFeedback.Seek(seekTarget, seekTarget - seekStart, duration)
                                    }
                                    DragMode.BRIGHTNESS -> {
                                        level = (level - delta.y / size.height * 1.4f * settings.videoGestureSensitivity).coerceIn(0.01f, 1f)
                                        activity?.setWindowBrightness(level)
                                        feedback = GestureFeedback.Level(
                                            if (level < 0.4f) Icons.Rounded.BrightnessLow else Icons.Rounded.BrightnessHigh,
                                            "Brightness ${(level * 100).roundToInt()}",
                                            level,
                                        )
                                    }
                                    DragMode.VOLUME -> {
                                        level = (level - delta.y / size.height * 1.4f * settings.videoGestureSensitivity).coerceIn(0f, 1f)
                                        val steps = (level * volumeMax).roundToInt()
                                        if (steps != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, steps, 0)
                                        }
                                        feedback = GestureFeedback.Level(
                                            if (steps == 0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                            "Volume $steps",
                                            level,
                                        )
                                    }
                                    else -> Unit
                                }
                                if (mode != DragMode.NONE) change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                        if (mode == DragMode.SEEK) viewModel.seekTo(seekTarget)
                        if (mode != DragMode.NONE) gestureActive = false
                    }
                },
        ) {
            val videoModifier = when (aspect) {
                VideoAspect.RATIO_16_9 -> Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(16f / 9f)
                VideoAspect.RATIO_4_3 -> Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(4f / 3f)
                else -> Modifier.fillMaxSize()
            }
            if (controller != null) AndroidView(
                factory = {
                    PlayerView(it).apply {
                        useController = false
                        // The session adapter does not relay first-frame events, so the default black
                        // shutter would never lift; the surface already sits on a black background.
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        this.player = controller
                    }
                },
                update = {
                    it.player = controller
                    it.resizeMode = aspect.resizeMode
                    it.subtitleView?.applySubtitleAppearance(settings.subtitleAppearance)
                },
                modifier = videoModifier.graphicsLayer(scaleX = zoom, scaleY = zoom),
            )
            player.errorMessage?.let { message ->
                Surface(
                    Modifier.align(Alignment.Center).padding(32.dp).widthIn(max = 420.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xE6202024),
                    contentColor = Color.White,
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Can't play this video", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.72f))
                    }
                }
            }
            if (doubleTapSide != 0) DoubleTapSeekIndicator(doubleTapSide, doubleTapTotalSeconds)
            feedback?.let { GestureFeedbackHud(it, Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 72.dp)) }

            if (inPictureInPicture) {
                // Picture-in-picture shows only the video; the system provides its own controls.
            } else if (locked) {
                AnimatedVisibility(lockHintVisible, Modifier.align(Alignment.CenterStart), enter = fadeIn(), exit = fadeOut()) {
                    Surface(
                        onClick = { locked = false; lockHintVisible = false; controlsVisible = true },
                        modifier = Modifier.safeDrawingPadding().padding(24.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White,
                    ) {
                        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LockOpen, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Tap to unlock", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            } else {
                val controls: @Composable () -> Unit = {
                    VideoControls(
                        player = player,
                        media = media,
                        aspect = aspect,
                        showRemaining = showRemaining,
                        orientationLocked = orientationLocked,
                        supportsPictureInPicture = supportsPictureInPicture,
                        hasSubtitles = player.subtitleTracks.isNotEmpty() || player.capabilities?.externalSubtitles == true,
                        onClose = onDismiss,
                        onToggle = viewModel::togglePlayback,
                        onPrevious = viewModel::previous,
                        onNext = viewModel::next,
                        onSeek = viewModel::seekTo,
                        onSkip = { seconds ->
                            viewModel.seekTo((player.positionMillis + seconds * 1_000L).coerceIn(0, player.durationMillis.coerceAtLeast(0)))
                        },
                        onToggleRemaining = { showRemaining = !showRemaining },
                        onLock = {
                            locked = true
                            controlsVisible = false
                            lockHintVisible = true
                        },
                        onOrientationLock = {
                            orientationLocked = !orientationLocked
                            activity?.requestedOrientation = if (orientationLocked) {
                                if (orientation == Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            } else settings.videoRotationBehavior.toRequestedOrientation()
                            showMessage(
                                if (orientationLocked) Icons.Rounded.ScreenLockRotation else Icons.Rounded.ScreenRotation,
                                if (orientationLocked) "Rotation locked" else "Auto-rotate",
                            )
                        },
                        onAspect = {
                            aspect = VideoAspect.entries[(aspect.ordinal + 1) % VideoAspect.entries.size]
                            zoom = 1f
                            showMessage(Icons.Rounded.AspectRatio, aspect.label)
                        },
                        onPip = onEnterPictureInPicture,
                        onPanel = { panel = it },
                    )
                }
                if (LocalReducedMotion.current) {
                    if (controlsVisible) controls()
                } else AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut()) { controls() }
            }
        }
    }
    panel?.let { selected ->
        VideoSettingsSheet(
            selected,
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
            onDismiss = { panel = null },
        )
    }
}

private val TopScrim = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent))
private val BottomScrim = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))
private const val TABULAR_FIGURES = "tnum"

@Composable
private fun VideoControls(
    player: PlaybackUiState,
    media: MediaItem?,
    aspect: VideoAspect,
    showRemaining: Boolean,
    orientationLocked: Boolean,
    supportsPictureInPicture: Boolean,
    hasSubtitles: Boolean,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Int) -> Unit,
    onToggleRemaining: () -> Unit,
    onLock: () -> Unit,
    onOrientationLock: () -> Unit,
    onAspect: () -> Unit,
    onPip: () -> Unit,
    onPanel: (PlayerPanel) -> Unit,
) {
    val hasQueue = player.queue.size > 1
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(TopScrim)) {
            Row(
                Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Close video", onClick = onClose)
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        media?.title.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    videoSubtitle(player, media)?.let {
                        Text(it, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
                if (player.audioTracks.size > 1) PlayerIconButton(Icons.Rounded.Audiotrack, "Audio track") { onPanel(PlayerPanel.AUDIO) }
                if (hasSubtitles) PlayerIconButton(Icons.Rounded.ClosedCaption, "Subtitles") { onPanel(PlayerPanel.SUBTITLES) }
                if (supportsPictureInPicture) PlayerIconButton(Icons.Rounded.PictureInPictureAlt, "Picture in picture", onClick = onPip)
                PlayerIconButton(Icons.Rounded.MoreVert, "Video options") { onPanel(PlayerPanel.ALL) }
            }
        }

        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerIconButton(Icons.Rounded.SkipPrevious, "Previous", enabled = hasQueue && player.currentIndex > 0, size = 48, onClick = onPrevious)
            PlayerIconButton(Icons.Rounded.Replay10, "Back 10 seconds", size = 48, onClick = { onSkip(-10) })
            Surface(
                onClick = onToggle,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.42f),
                contentColor = Color.White,
                modifier = Modifier.size(76.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (player.isPlaying) "Pause" else "Play",
                        Modifier.size(44.dp),
                    )
                }
            }
            PlayerIconButton(Icons.Rounded.Forward10, "Forward 10 seconds", size = 48, onClick = { onSkip(10) })
            PlayerIconButton(Icons.Rounded.SkipNext, "Next", enabled = hasQueue && player.currentIndex < player.queue.lastIndex, size = 48, onClick = onNext)
        }

        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(BottomScrim)) {
            Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 4.dp)) {
                VideoSeekBar(player, showRemaining, onSeek, onToggleRemaining)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PlayerIconButton(Icons.Rounded.Lock, "Lock screen controls", onClick = onLock)
                    PlayerIconButton(
                        if (orientationLocked) Icons.Rounded.ScreenLockRotation else Icons.Rounded.ScreenRotation,
                        if (orientationLocked) "Unlock orientation" else "Lock orientation",
                        onClick = onOrientationLock,
                    )
                    Spacer(Modifier.weight(1f))
                    PlayerPill(Icons.Rounded.Speed, formatSpeed(player.speed), "Playback speed ${formatSpeed(player.speed)}") { onPanel(PlayerPanel.SPEED) }
                    Spacer(Modifier.width(8.dp))
                    PlayerPill(Icons.Rounded.AspectRatio, aspect.shortLabel, "Aspect ratio ${aspect.label}", onAspect)
                }
            }
        }
    }
}

@Composable
private fun VideoSeekBar(
    player: PlaybackUiState,
    showRemaining: Boolean,
    onSeek: (Long) -> Unit,
    onToggleRemaining: () -> Unit,
) {
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val duration = player.durationMillis.coerceAtLeast(1)
    val position = scrubbing?.toLong() ?: if (player.durationMillis > 0) player.positionMillis.coerceAtMost(duration) else 0L
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatVideoTime(position),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
        )
        Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            LinearProgressIndicator(
                progress = { if (player.durationMillis > 0) player.bufferedPositionMillis.toFloat() / player.durationMillis else 0f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Color.White.copy(alpha = 0.35f),
                trackColor = Color.White.copy(alpha = 0.18f),
                drawStopIndicator = {},
            )
            Slider(
                value = position.toFloat(),
                onValueChange = { scrubbing = it },
                onValueChangeFinished = {
                    scrubbing?.let { onSeek(it.toLong()) }
                    scrubbing = null
                },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "Video playback seekbar"
                    stateDescription = "${formatVideoTime(position)} of ${formatVideoTime(player.durationMillis)}"
                    progressBarRangeInfo = ProgressBarRangeInfo(current = position.toFloat(), range = 0f..duration.toFloat())
                },
            )
        }
        Text(
            if (showRemaining) "−" + formatVideoTime(player.durationMillis - position) else formatVideoTime(player.durationMillis),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
            modifier = Modifier.clickable(onClickLabel = "Toggle remaining time", onClick = onToggleRemaining),
        )
    }
}

@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    size: Int = 24,
    onClick: () -> Unit,
) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.size((size + 24).dp)) {
        Icon(icon, description, tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(size.dp))
    }
}

@Composable
private fun PlayerPill(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.14f),
        contentColor = Color.White,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun GestureFeedbackHud(feedback: GestureFeedback, modifier: Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.64f),
        contentColor = Color.White,
    ) {
        when (feedback) {
            is GestureFeedback.Seek -> Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatVideoTime(feedback.targetMillis),
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    (if (feedback.deltaMillis >= 0) "+" else "−") + formatVideoTime(abs(feedback.deltaMillis)) +
                        "  ·  " + formatVideoTime(feedback.durationMillis),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
            is GestureFeedback.Level -> Column(Modifier.padding(horizontal = 22.dp, vertical = 16.dp).width(160.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(feedback.icon, null, Modifier.size(28.dp))
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { feedback.fraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.24f),
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(8.dp))
                Text(feedback.label, style = MaterialTheme.typography.labelLarge)
            }
            is GestureFeedback.Message -> Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                feedback.icon?.let {
                    Icon(it, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(feedback.text, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun DoubleTapSeekIndicator(side: Int, seconds: Int) {
    val forward = side > 0
    Box(Modifier.fillMaxSize(), contentAlignment = if (forward) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(0.36f).background(
                Color.White.copy(alpha = 0.10f),
                if (forward) RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50) else RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (forward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind, null, tint = Color.White, modifier = Modifier.size(36.dp))
                Text("${if (forward) "+" else "−"}$seconds s", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun videoSubtitle(player: PlaybackUiState, media: MediaItem?): String? {
    val parts = buildList {
        if (player.queue.size > 1) add("${player.currentIndex + 1} of ${player.queue.size}")
        media?.let { item -> item.width?.let { w -> item.height?.let { h -> add(resolutionLabel(w, h)) } } }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
}

private fun resolutionLabel(width: Int, height: Int): String = when (minOf(width, height)) {
    in 2_000..Int.MAX_VALUE -> "4K"
    in 1_400 until 2_000 -> "1440p"
    in 1_000 until 1_400 -> "1080p"
    in 700 until 1_000 -> "720p"
    else -> "${minOf(width, height)}p"
}

private val VideoAspect.shortLabel: String get() = when (this) {
    VideoAspect.FIT -> "Fit"
    VideoAspect.FILL -> "Stretch"
    VideoAspect.ORIGINAL -> "100%"
    VideoAspect.RATIO_16_9 -> "16:9"
    VideoAspect.RATIO_4_3 -> "4:3"
    VideoAspect.CROP -> "Crop"
}

private fun VideoRotationBehavior.toRequestedOrientation(): Int = when (this) {
    VideoRotationBehavior.FOLLOW_SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    VideoRotationBehavior.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
    VideoRotationBehavior.LOCK_CURRENT -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
}

private fun Activity.currentWindowBrightness(): Float {
    val override = window.attributes.screenBrightness
    if (override >= 0f) return override
    return runCatching { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }
        .getOrDefault(0.5f)
}

private fun Activity.setWindowBrightness(value: Float) {
    window.attributes = window.attributes.apply { screenBrightness = value }
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "%.1f×".format(speed) else "%s×".format(speed.toString().trimEnd('0'))

@Composable
private fun VideoSettingsSheet(
    panel: PlayerPanel,
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
    val all = panel == PlayerPanel.ALL
    BottomSheet(onDismiss) {
        Text(
            when (panel) {
                PlayerPanel.ALL -> "Video options"
                PlayerPanel.SPEED -> "Playback speed"
                PlayerPanel.AUDIO -> "Audio"
                PlayerPanel.SUBTITLES -> "Subtitles"
            },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        if (all || panel == PlayerPanel.SPEED) SpeedControls(player.speed, viewModel::setSpeed, showHeading = all)
        if (all || panel == PlayerPanel.AUDIO) AudioSection(player, settings, onPreferredAudioLanguage, onAudioDelay, viewModel)
        if (all || panel == PlayerPanel.SUBTITLES) SubtitleSection(
            player, settings, onPreferredSubtitleLanguage, onSubtitlesEnabled, onSubtitleDelay, onSubtitleAppearance, onOpenSubtitle, viewModel,
        )
        if (all) GeneralSection(player, media, aspect, settings, onAspect, onSensitivity, viewModel)
    }
}

@Composable
private fun SpeedControls(speed: Float, onSpeed: (Float) -> Unit, showHeading: Boolean) {
    if (showHeading) MenuHeading(Icons.Outlined.Speed, "Playback speed")
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(formatSpeed(speed), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.width(72.dp))
        Slider(
            value = speed,
            onValueChange = { onSpeed((it * 20).roundToInt() / 20f) },
            valueRange = 0.25f..4f,
            modifier = Modifier.weight(1f).semantics { contentDescription = "Playback speed" },
        )
    }
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(PlaybackSpeeds.PRESETS.size) { index ->
            val value = PlaybackSpeeds.PRESETS[index]
            FilterChip(selected = abs(speed - value) < 0.01f, onClick = { onSpeed(value) }, label = { Text(formatSpeed(value)) })
        }
    }
}

@Composable
private fun AudioSection(
    player: PlaybackUiState,
    settings: AppSettings,
    onPreferredAudioLanguage: (String) -> Unit,
    onAudioDelay: (Long) -> Unit,
    viewModel: PlaybackViewModel,
) {
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
}

@Composable
private fun SubtitleSection(
    player: PlaybackUiState,
    settings: AppSettings,
    onPreferredSubtitleLanguage: (String) -> Unit,
    onSubtitlesEnabled: (Boolean) -> Unit,
    onSubtitleDelay: (Long) -> Unit,
    onSubtitleAppearance: (SubtitleAppearance) -> Unit,
    onOpenSubtitle: () -> Unit,
    viewModel: PlaybackViewModel,
) {
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
}

@Composable
private fun GeneralSection(
    player: PlaybackUiState,
    media: MediaItem?,
    aspect: VideoAspect,
    settings: AppSettings,
    onAspect: (VideoAspect) -> Unit,
    onSensitivity: (Float) -> Unit,
    viewModel: PlaybackViewModel,
) {
    MenuHeading(Icons.Outlined.AspectRatio, "Aspect ratio")
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(VideoAspect.entries.size) { index ->
            val value = VideoAspect.entries[index]
            FilterChip(selected = value == aspect, onClick = { onAspect(value) }, label = { Text(value.label) })
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
