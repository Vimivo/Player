@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@file:Suppress(
    "ArgumentListWrapping",
    "CyclomaticComplexMethod",
    "ImportOrdering",
    "Indentation",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "MaximumLineLength",
    "MaxLineLength",
    "MultiLineIfElse",
    "ParameterListWrapping",
    "TooManyFunctions",
    "Wrapping",
)

package dev.vimivo.platform.android.library.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.vimivo.core.model.Bookmark
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.platform.android.library.AndroidThumbnailRepository
import dev.vimivo.core.lyrics.LyricsDocument
import dev.vimivo.platform.android.playback.PlaybackController
import dev.vimivo.platform.android.playback.PlaybackUiState
import dev.vimivo.platform.android.playback.PlaybackViewModel
import dev.vimivo.platform.android.settings.SettingsViewModel
import dev.vimivo.platform.android.ui.components.MediaArtwork
import dev.vimivo.platform.android.ui.components.SettingsRow
import dev.vimivo.platform.android.ui.theme.LocalReducedMotion

private enum class NowPlayingSheet { NONE, OPTIONS, EQUALIZER, SLEEP }

/**
 * The music player: artwork, title, format, a seek bar, transport, and shortcuts to the queue, lyrics, equalizer and
 * sleep timer. Portrait stacks everything; landscape puts the artwork on the left and the shortcuts on the right.
 */
@Composable
internal fun NowPlayingScreen(
    player: PlaybackUiState,
    thumbnails: AndroidThumbnailRepository,
    playback: PlaybackController,
    viewModel: PlaybackViewModel,
    settingsViewModel: SettingsViewModel,
    artworkAccents: Boolean,
    visualizerEnabled: Boolean,
    onDismiss: () -> Unit,
    openQueue: () -> Unit,
) {
    var sheet by remember { mutableStateOf(NowPlayingSheet.NONE) }
    var lyricsMode by rememberSaveable { mutableStateOf(false) }
    val controller by playback.controller.collectAsState()
    val current = player.current?.toDomain()
    val accent by produceState<Int?>(null, current?.uri, current?.artworkUri, artworkAccents) {
        value = if (artworkAccents) current?.let { thumbnails.dominantColor(it.uri, it.artworkUri) } else null
    }
    Dialog(
        onDismissRequest = { if (sheet != NowPlayingSheet.NONE) sheet = NowPlayingSheet.NONE else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val ambient = accent?.let(::Color) ?: MaterialTheme.colorScheme.primary
        val background = MaterialTheme.colorScheme.background
        Surface(Modifier.fillMaxSize(), color = background) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ambient.copy(alpha = 0.42f), background.copy(alpha = 0.94f), background)))) {
                if (current == null) return@Box
                BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                    val landscape = maxWidth > maxHeight
                    val ui = NowPlayingUi(
                        player, current, thumbnails, controller, lyricsMode, visualizerEnabled,
                        onToggleLyrics = { lyricsMode = !lyricsMode },
                        viewModel = viewModel,
                        openQueue = openQueue,
                        openSheet = { sheet = it },
                        onDismiss = onDismiss,
                    )
                    if (landscape) LandscapeLayout(ui, maxHeight) else PortraitLayout(ui)
                }
                if (sheet != NowPlayingSheet.NONE) {
                    PlayerPanel({ sheet = NowPlayingSheet.NONE }) {
                        PlayerSheetContent(sheet, player, viewModel, settingsViewModel) { sheet = NowPlayingSheet.NONE }
                    }
                }
            }
        }
    }
}

private class NowPlayingUi(
    val player: PlaybackUiState,
    val media: MediaItem,
    val thumbnails: AndroidThumbnailRepository,
    val controller: androidx.media3.common.Player?,
    val lyricsMode: Boolean,
    val styledSeekBar: Boolean,
    val onToggleLyrics: () -> Unit,
    val viewModel: PlaybackViewModel,
    val openQueue: () -> Unit,
    val openSheet: (NowPlayingSheet) -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
private fun PortraitLayout(ui: NowPlayingUi) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Header(ui)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Stage(ui) }
        Spacer(Modifier.height(16.dp))
        TrackInfo(ui)
        Spacer(Modifier.height(12.dp))
        Scrubber(ui)
        Spacer(Modifier.height(8.dp))
        Transport(ui)
        Spacer(Modifier.height(8.dp))
        Shortcuts(ui)
    }
}

@Composable
private fun LandscapeLayout(ui: NowPlayingUi, height: androidx.compose.ui.unit.Dp) {
    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column {
            IconButton(ui.onDismiss) { Icon(Icons.Rounded.KeyboardArrowDown, "Close player") }
            Box(Modifier.weight(1f).aspectRatio(1f).widthIn(max = height), contentAlignment = Alignment.Center) { Stage(ui) }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            TrackInfo(ui)
            Spacer(Modifier.height(12.dp))
            Scrubber(ui)
            Spacer(Modifier.height(8.dp))
            Transport(ui)
        }
        Column(
            Modifier.widthIn(max = 240.dp).fillMaxHeight().clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)).padding(8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            SideAction("Queue", Icons.AutoMirrored.Outlined.QueueMusic, ui.player.queue.size.toString(), ui.openQueue)
            SideAction("Lyrics", Icons.Outlined.Lyrics, null, ui.onToggleLyrics)
            SideAction("Equalizer", Icons.Outlined.GraphicEq, null) { ui.openSheet(NowPlayingSheet.EQUALIZER) }
            SideAction("Sleep timer", Icons.Outlined.Bedtime, null) { ui.openSheet(NowPlayingSheet.SLEEP) }
            SideAction("Playback options", Icons.Outlined.Tune, null) { ui.openSheet(NowPlayingSheet.OPTIONS) }
        }
    }
}

@Composable
private fun SideAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, count: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = label, onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        count?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun Header(ui: NowPlayingUi) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(ui.onDismiss) { Icon(Icons.Rounded.KeyboardArrowDown, "Close player", Modifier.size(30.dp)) }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Text(
                "  Now Playing",
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton({ ui.openSheet(NowPlayingSheet.OPTIONS) }) { Icon(Icons.Rounded.MoreVert, "Playback options") }
    }
}

/** The artwork (or the video, for a video opened here), or the lyrics when they are switched on. */
@Composable
private fun Stage(ui: NowPlayingUi) {
    val reduced = LocalReducedMotion.current
    if (ui.lyricsMode) {
        LyricsPanel(ui.player.lyrics, ui.player.currentLyricIndex, reduced, ui.viewModel::seekTo, ui.viewModel::loadLyrics)
        return
    }
    if (ui.media.kind == MediaKind.VIDEO && ui.controller != null) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { androidx.media3.ui.PlayerView(it).apply { setShutterBackgroundColor(android.graphics.Color.TRANSPARENT); player = ui.controller } },
            update = { it.player = ui.controller },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)),
        )
    } else {
        MediaArtwork(
            ui.media, ui.thumbnails,
            Modifier.fillMaxSize().aspectRatio(1f).shadow(24.dp, RoundedCornerShape(24.dp), ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary)
                .clip(RoundedCornerShape(24.dp)),
        )
    }
}

@Composable
private fun TrackInfo(ui: NowPlayingUi) {
    val item = ui.player.current ?: return
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.mediaMetadata.title?.toString() ?: item.mediaId,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.mediaMetadata.artist?.toString() ?: "Unknown artist",
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(ui.viewModel::toggleFavorite) {
                Icon(
                    if (ui.player.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (ui.player.isFavorite) "Remove favorite" else "Add favorite",
                    tint = if (ui.player.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        FormatBadges(ui)
    }
}

@Composable
private fun FormatBadges(ui: NowPlayingUi) {
    val track = ui.player.audioTracks.firstOrNull { it.isSelected } ?: ui.player.audioTracks.firstOrNull()
    val codec = (track?.codec ?: track?.mimeType)?.substringAfterLast('/')?.substringAfterLast('.')?.uppercase()?.takeIf { it.isNotBlank() }
    val kbps = (track?.bitrate ?: ui.media.bitrate)?.takeIf { it > 0 }?.let { "${it / 1_000} kbps" }
    val hz = track?.sampleRate?.takeIf { it > 0 }?.let { "%.1f kHz".format(it / 1_000f) }
    val parts = listOfNotNull(kbps, hz)
    if (codec == null && parts.isEmpty()) return
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        codec?.let {
            Text(
                it,
                Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
            )
        }
        if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Scrubber(ui: NowPlayingUi) {
    Column(Modifier.fillMaxWidth()) {
        WaveformSeekBar(ui.player.positionMillis, ui.player.durationMillis, ui.media.id.value, ui.viewModel::seekTo, styled = ui.styledSeekBar)
        TimeLabels(ui.player.positionMillis, ui.player.durationMillis)
    }
}

@Composable
private fun Transport(ui: NowPlayingUi) {
    val vm = ui.viewModel
    TransportRow(ui.player.isPlaying, ui.player.shuffleEnabled, ui.player.repeatMode, vm::toggleShuffle, vm::previous, vm::togglePlayback, vm::next, vm::cycleRepeat)
    ui.player.errorMessage?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun Shortcuts(ui: NowPlayingUi) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        PlayerAction(Icons.AutoMirrored.Outlined.QueueMusic, "Queue", ui.openQueue, badge = ui.player.queue.size.takeIf { it > 1 }?.toString())
        PlayerAction(Icons.Outlined.Lyrics, "Lyrics", ui.onToggleLyrics, active = ui.lyricsMode)
        PlayerAction(Icons.Outlined.GraphicEq, "Equalizer", { ui.openSheet(NowPlayingSheet.EQUALIZER) })
        PlayerAction(Icons.Outlined.Bedtime, "Sleep", { ui.openSheet(NowPlayingSheet.SLEEP) })
    }
}

/** Synced lyrics as a centred, fading list around the current line; or a prompt to load a file when there are none. */
@Composable
private fun LyricsPanel(
    lyrics: LyricsDocument?,
    currentIndex: Int,
    reducedMotion: Boolean,
    onSeek: (Long) -> Unit,
    onLoad: (android.net.Uri) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onLoad) }
    if (lyrics == null || lyrics.lines.isEmpty()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.MusicNote, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("No lyrics for this track", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
            Text("Load a local .lrc or text file.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button({ picker.launch(arrayOf("text/plain", "application/octet-stream")) }, Modifier.padding(top = 16.dp)) { Text("Load lyrics") }
        }
        return
    }
    val state = rememberLazyListState()
    LaunchedEffect(currentIndex, lyrics.lines.size) {
        if (currentIndex >= 0) {
            val target = (currentIndex - 2).coerceAtLeast(0)
            if (reducedMotion) state.scrollToItem(target) else state.animateScrollToItem(target)
        }
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        itemsIndexed(lyrics.lines, key = { index, line -> "${line.timeMillis ?: -1}:$index" }) { index, line ->
            val distance = if (currentIndex < 0) 1 else kotlin.math.abs(index - currentIndex)
            val active = index == currentIndex
            Text(
                line.text.ifBlank { "♪" },
                Modifier.fillMaxWidth().alpha(if (active) 1f else (0.62f - 0.12f * distance).coerceAtLeast(0.22f))
                    .clickable(enabled = line.timeMillis != null) { line.timeMillis?.let(onSeek) },
                style = if (active) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A bottom panel drawn over the player with a scrim; tapping the scrim closes it. */
@Composable
private fun PlayerPanel(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
        Surface(
            Modifier.fillMaxWidth().widthIn(max = 640.dp).heightIn(max = 640.dp).clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).safeDrawingPadding().padding(bottom = 16.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).size(width = 36.dp, height = 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape))
                content()
            }
        }
    }
}

@Composable
private fun PlayerSheetContent(
    sheet: NowPlayingSheet,
    player: PlaybackUiState,
    viewModel: PlaybackViewModel,
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    when (sheet) {
        NowPlayingSheet.EQUALIZER -> {
            val settings by settingsViewModel.state.collectAsState()
            val audio by settingsViewModel.audio.collectAsState()
            EqualizerContent(settings.audioEnhancements, audio, settingsViewModel::setAudioEnhancements)
        }
        NowPlayingSheet.SLEEP -> SleepSheet(viewModel, onDismiss)
        else -> OptionsSheet(player, viewModel, onDismiss)
    }
}

@Composable
private fun SleepSheet(viewModel: PlaybackViewModel, onDismiss: () -> Unit) {
    val sleepTimer by viewModel.sleepTimerState.collectAsState()
    Column {
        Text("Sleep timer", Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60).forEach { minutes ->
                AssistChip({ viewModel.startSleepTimer(minutes, 10); onDismiss() }, { Text("$minutes min") })
            }
        }
        SettingsRow(Icons.Outlined.MusicNote, "Stop after current track", onClick = { viewModel.stopAfterCurrent(10); onDismiss() })
        if (sleepTimer.deadlineEpochMillis != null || sleepTimer.stopAfterCurrent) {
            SettingsRow(
                Icons.Outlined.Close, "Cancel sleep timer",
                detail = sleepTimer.remainingMillis?.let(::formatClock) ?: "After current track",
                onClick = { viewModel.cancelSleepTimer(); onDismiss() },
            )
        }
    }
}

@Composable
private fun OptionsSheet(player: PlaybackUiState, viewModel: PlaybackViewModel, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    val lyricsPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::loadLyrics) }
    Column {
        Text("Playback options", Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall)
        Text("Speed", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleSmall)
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).size) { index ->
                val speed = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)[index]
                FilterChip(player.speed == speed, { viewModel.setSpeed(speed) }, { Text("$speed×") })
            }
        }
        SettingsRow(Icons.Outlined.BookmarkAdd, "Save bookmark", detail = formatClock(player.positionMillis), onClick = {
            viewModel.addBookmark("Bookmark at " + formatClock(player.positionMillis))
        })
        SettingsRow(Icons.Outlined.Lyrics, "Load LRC lyrics", detail = "Local synchronized or plain lyrics", onClick = {
            lyricsPicker.launch(arrayOf("text/plain", "application/octet-stream"))
        })
        if (player.bookmarks.isNotEmpty()) {
            Text("Bookmarks", Modifier.padding(20.dp), style = MaterialTheme.typography.titleSmall)
            player.bookmarks.forEach { bookmark ->
                ListItem(
                    modifier = Modifier.clickable { viewModel.seekToBookmark(bookmark); onDismiss() },
                    headlineContent = { Text(bookmark.name) },
                    supportingContent = { Text(formatClock(bookmark.positionMillis)) },
                    trailingContent = {
                        Row {
                            IconButton({ editing = bookmark }) { Icon(Icons.Outlined.Edit, "Rename bookmark") }
                            IconButton({ viewModel.deleteBookmark(bookmark) }) { Icon(Icons.Outlined.Delete, "Delete bookmark") }
                        }
                    },
                )
            }
        }
    }
    editing?.let { bookmark ->
        PlaylistNameDialog("Rename bookmark", bookmark.name, { viewModel.renameBookmark(bookmark, it); editing = null }) { editing = null }
    }
}
