@file:Suppress(
    "ArgumentListWrapping",
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "ImportOrdering",
    "Indentation",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "MaximumLineLength",
    "MaxLineLength",
    "NoConsecutiveBlankLines",
    "TooManyFunctions",
    "UnusedParameter",
    "Wrapping",
)

package dev.vimivo.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.VolumeMute
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VolumeMute
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vimivo.core.model.AlbumItem
import dev.vimivo.core.model.ArtistItem
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.desktop.DesktopArtwork
import dev.vimivo.desktop.library.DesktopLibraryUiState
import dev.vimivo.desktop.library.LibraryScanState
import dev.vimivo.playback.PlaybackErrorCode
import dev.vimivo.playback.PlaybackQueueState
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.QueueRepeatMode
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlin.math.roundToInt

// Destination Enum with Icon mappings
internal enum class DesktopDestination(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val emptyTitle: String,
    val emptyDetail: String,
) {
    Home("Home", "Your private media dashboard", Icons.Outlined.Home, "Open media to begin", "Vimivo keeps your media on this device."),
    Music("Music", "Tracks in your local library", Icons.Outlined.MusicNote, "Your music library is empty", "Add and scan a folder to index your music."),
    Albums("Albums", "Browse albums and discographies", Icons.Outlined.Album, "No albums yet", "Add and scan music folders with album metadata."),
    Artists("Artists", "Musicians, bands, and composers", Icons.Outlined.Person, "No artists yet", "Add and scan music folders with artist metadata."),
    Videos("Videos", "Local videos and resume points", Icons.Outlined.Videocam, "Your video library is empty", "Add a video folder or open a video directly."),
    Playlists("Playlists", "Custom local playlists", Icons.AutoMirrored.Outlined.QueueMusic, "No playlists yet", "Create a playlist to organize your tracks."),
    SmartPlaylists("Smart Playlists", "Dynamic rule-based collections", Icons.Outlined.AutoAwesome, "No smart playlists yet", "Create a rule-based smart collection."),
    Favorites("Favorites", "Quick-access starred media", Icons.Default.Favorite, "No favorites yet", "Click the star or heart icon on any media card."),
    History("History", "Recently played and watched", Icons.Outlined.History, "No playback history", "Played library media appears here."),
    Duplicates("Duplicates", "Duplicate detector and cleaner", Icons.Outlined.ContentCopy, "No duplicate groups found", "Scan your library to detect duplicates."),
    MetadataEditor("Metadata", "Embedded tags and overrides", Icons.Outlined.DriveFileRenameOutline, "No media selected", "Select items to view or edit metadata."),
    BatchRename("Rename", "Pattern-based file renaming", Icons.Outlined.DriveFileRenameOutline, "No files ready for renaming", "Select a pattern to preview renames."),
    MissingMedia("Relink", "Reconcile moved or missing files", Icons.Outlined.Link, "All library media is available", "No missing files detected."),
    Automation("Automation", "Automated library maintenance", Icons.Outlined.Build, "Library is healthy", "Configure maintenance automation."),
    Insights("Insights", "Private listening and watching statistics", Icons.Outlined.Insights, "No playback history", "Insights appear as you play media offline."),
    Network("Network", "Authorized streams and remote feeds", Icons.Outlined.Podcasts, "Open an authorized stream", "Paste an HTTP, HTTPS, or RTSP address."),
    Settings("Settings", "Preferences and audio profiles", Icons.Outlined.Settings, "Settings", "Configure theme, profiles, and persistence."),
}

internal data class DesktopNavGroup(
    val title: String,
    val destinations: List<DesktopDestination>,
)

internal val DesktopNavGroups = listOf(
    DesktopNavGroup(
        title = "LIBRARY",
        destinations = listOf(
            DesktopDestination.Home,
            DesktopDestination.Music,
            DesktopDestination.Albums,
            DesktopDestination.Artists,
            DesktopDestination.Videos,
        ),
    ),
    DesktopNavGroup(
        title = "COLLECTIONS",
        destinations = listOf(
            DesktopDestination.Playlists,
            DesktopDestination.SmartPlaylists,
            DesktopDestination.Favorites,
            DesktopDestination.History,
        ),
    ),
    DesktopNavGroup(
        title = "TOOLS",
        destinations = listOf(
            DesktopDestination.Duplicates,
            DesktopDestination.MetadataEditor,
            DesktopDestination.BatchRename,
            DesktopDestination.MissingMedia,
            DesktopDestination.Automation,
            DesktopDestination.Insights,
        ),
    ),
    DesktopNavGroup(
        title = "SYSTEM",
        destinations = listOf(
            DesktopDestination.Network,
            DesktopDestination.Settings,
        ),
    ),
)

/**
 * Modern, polished macOS-style application sidebar.
 */
@Composable
internal fun DesktopSidebarView(
    selected: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = colors.surfaceContainerLow,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = if (collapsed) 8.dp else 12.dp),
        ) {
            // Brand Header Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (collapsed) 4.dp else 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Headphones,
                            contentDescription = "Vimivo Emblem",
                            tint = colors.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (!collapsed) {
                        Column {
                            Text(
                                text = "Vimivo",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                ),
                                color = colors.onSurface,
                            )
                            Text(
                                text = "Every Sound. Every Frame.",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (!collapsed) {
                    IconButton(
                        onClick = onToggleCollapse,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Collapse sidebar",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Navigation groups
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DesktopNavGroups.forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (!collapsed) {
                            Text(
                                text = group.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.2.sp,
                                    fontSize = 10.sp,
                                ),
                                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        group.destinations.forEach { dest ->
                            DesktopSidebarRow(
                                destination = dest,
                                isSelected = selected == dest,
                                collapsed = collapsed,
                                onClick = { onDestinationSelected(dest) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopSidebarRow(
    destination: DesktopDestination,
    isSelected: Boolean,
    collapsed: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val backgroundColor = if (isSelected) {
        colors.primary.copy(alpha = 0.14f)
    } else {
        Color.Transparent
    }
    val contentColor = if (isSelected) {
        colors.primary
    } else {
        colors.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics { contentDescription = "Navigate to ${destination.title}" },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (collapsed) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(10.dp),
        ) {
            if (isSelected && !collapsed) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.primary),
                )
            }
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            if (!collapsed) {
                Text(
                    text = destination.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Top application header: Search, view switcher, queue toggle.
 */
@Composable
internal fun DesktopTopHeaderView(
    destination: DesktopDestination,
    search: String,
    showLibraryControls: Boolean,
    gridView: Boolean,
    descendingSort: Boolean,
    queueVisible: Boolean,
    queueItemCount: Int,
    onToggleSidebar: () -> Unit,
    onSearch: (String) -> Unit,
    onToggleView: () -> Unit,
    onToggleSort: () -> Unit,
    onToggleQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surfaceContainerLowest,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(
                    onClick = onToggleSidebar,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Toggle sidebar",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column {
                    Text(
                        text = destination.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface,
                    )
                    Text(
                        text = destination.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showLibraryControls) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = onSearch,
                        placeholder = { Text("Search library…", style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = colors.onSurfaceVariant,
                            )
                        },
                        trailingIcon = {
                            if (search.isNotBlank()) {
                                IconButton(onClick = { onSearch("") }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Outlined.Close, "Clear", modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.width(220.dp).height(44.dp),
                    )

                    IconButton(
                        onClick = onToggleView,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = if (gridView) Icons.AutoMirrored.Outlined.List else Icons.Outlined.GridView,
                            contentDescription = if (gridView) "Switch to list view" else "Switch to grid view",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    OutlinedButton(
                        onClick = onToggleSort,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text(if (descendingSort) "Z → A" else "A → Z", style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Queue access button
                BadgedBox(
                    badge = {
                        if (queueItemCount > 0) {
                            Badge(containerColor = colors.primary, contentColor = colors.onPrimary) {
                                Text("$queueItemCount")
                            }
                        }
                    },
                ) {
                    Button(
                        onClick = onToggleQueue,
                        shape = RoundedCornerShape(8.dp),
                        colors = if (queueVisible) {
                            ButtonDefaults.buttonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer)
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                        border = if (!queueVisible) BorderStroke(1.dp, colors.outline.copy(alpha = 0.5f)) else null,
                        modifier = Modifier.height(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.QueueMusic,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (queueVisible) "Hide Queue" else "Queue", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/**
 * Human-friendly, non-intrusive playback error presentation.
 * Replaces the raw 20-line JNA dlopen stack trace with concise actionable UI.
 */
@Composable
internal fun DesktopPlaybackErrorCard(
    error: PlaybackStatus.Error,
    onRetry: () -> Unit,
    onOpenOtherMedia: () -> Unit,
    onSkipNext: (() -> Unit)?,
    onDismiss: () -> Unit,
    onReportIssue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var expandedDiagnostics by remember { mutableStateOf(false) }
    var copiedToClipboard by remember { mutableStateOf(false) }

    val userMessage = when (error.error.code) {
        PlaybackErrorCode.ENGINE_INITIALIZATION ->
            "Vimivo could not initialize the video playback engine. On macOS, ensure libmpv is installed via Homebrew ('brew install mpv') or bundled with the application."
        PlaybackErrorCode.NETWORK ->
            "The network stream could not be reached. Verify your network connection and streaming URL."
        PlaybackErrorCode.UNSUPPORTED_CONTAINER, PlaybackErrorCode.UNSUPPORTED_CODEC ->
            "This media format or codec is not supported by the playback engine."
        else ->
            "The selected media file could not be played. Check that the source file is accessible."
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.errorContainer.copy(alpha = 0.85f),
            contentColor = colors.onErrorContainer,
        ),
        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = "Playback Error",
                    tint = colors.error,
                    modifier = Modifier.size(24.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Playback couldn't start",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onErrorContainer,
                    )
                    Text(
                        text = userMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onErrorContainer.copy(alpha = 0.9f),
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Dismiss error",
                        tint = colors.onErrorContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError),
                ) {
                    Text("Retry", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onOpenOtherMedia,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onErrorContainer),
                    border = BorderStroke(1.dp, colors.onErrorContainer.copy(alpha = 0.5f)),
                ) {
                    Text("Open Media", style = MaterialTheme.typography.labelSmall)
                }
                if (onSkipNext != null) {
                    OutlinedButton(
                        onClick = onSkipNext,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onErrorContainer),
                        border = BorderStroke(1.dp, colors.onErrorContainer.copy(alpha = 0.5f)),
                    ) {
                        Text("Skip to Next", style = MaterialTheme.typography.labelSmall)
                    }
                }
                OutlinedButton(
                    onClick = { expandedDiagnostics = !expandedDiagnostics },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onErrorContainer),
                    border = BorderStroke(1.dp, colors.onErrorContainer.copy(alpha = 0.5f)),
                ) {
                    Text(
                        if (expandedDiagnostics) "Hide Technical Details" else "View Technical Details",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                OutlinedButton(
                    onClick = onReportIssue,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onErrorContainer),
                    border = BorderStroke(1.dp, colors.onErrorContainer.copy(alpha = 0.5f)),
                ) {
                    Text("Report Issue", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Expandable Technical Diagnostics
            AnimatedVisibility(visible = expandedDiagnostics) {
                Surface(
                    color = colors.surfaceContainerLowest,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Technical Diagnostic Output (${error.error.code.name})",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.onSurface,
                            )
                            Button(
                                onClick = {
                                    runCatching {
                                        val sel = StringSelection("${error.error.code.name}: ${error.error.message}")
                                        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                                        copiedToClipboard = true
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp),
                            ) {
                                Text(
                                    if (copiedToClipboard) "Copied!" else "Copy Diagnostics",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                )
                            }
                        }
                        Text(
                            text = error.error.message,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = colors.onSurfaceVariant,
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Redesigned Home Media Dashboard:
 * - Empty state when no media exists (intentional, clean, privacy-focused)
 * - Populated dashboard with Recently Played, Insights stat chips, and Quick Shortcuts.
 */
@Composable
internal fun DesktopHomeScreenView(
    state: DesktopLibraryUiState,
    onPlay: (MediaItem) -> Unit,
    onOpenFile: () -> Unit,
    onAddFolder: () -> Unit,
    onOpenStreamDialog: () -> Unit,
    onDismissRecommendation: (String, String) -> Unit,
    onTogglePersonalization: (Boolean) -> Unit,
    onScan: (String) -> Unit,
    onCancelScan: (String) -> Unit,
    onRemoveRoot: (String) -> Unit,
    onSelectAlbum: (AlbumItem) -> Unit,
    onSelectArtist: (ArtistItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val hasMedia = state.audio.isNotEmpty() || state.video.isNotEmpty() || state.roots.isNotEmpty()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (!hasMedia) {
            // Intentional, beautiful empty state card
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.4f)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Headphones,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    Text(
                        text = "Your music and videos, in one place.",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface,
                    )

                    Text(
                        text = "Add a folder or open local audio and video files to begin playback.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Button(
                            onClick = onOpenFile,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open Media Files")
                        }
                        OutlinedButton(
                            onClick = onAddFolder,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("Add Media Folder")
                        }
                        OutlinedButton(
                            onClick = onOpenStreamDialog,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Outlined.Link, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Network Stream")
                        }
                    }
                }
            }
        } else {
            // Quick Action Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onOpenFile, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open File(s)")
                }
                OutlinedButton(onClick = onAddFolder, shape = RoundedCornerShape(8.dp)) {
                    Text("Add Folder")
                }
                OutlinedButton(onClick = onOpenStreamDialog, shape = RoundedCornerShape(8.dp)) {
                    Text("Open Stream…")
                }
            }

            // Compact Insights Stat Chips
            state.listeningInsights?.let { insights ->
                Text("Library Statistics", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DesktopStatCard(
                        icon = Icons.Outlined.Headphones,
                        label = "Listening Time",
                        value = formatDuration(insights.totalListeningDurationMillis),
                        modifier = Modifier.weight(1f),
                    )
                    DesktopStatCard(
                        icon = Icons.Outlined.MusicNote,
                        label = "Tracks Played",
                        value = "${insights.totalTracksPlayed}",
                        modifier = Modifier.weight(1f),
                    )
                    DesktopStatCard(
                        icon = Icons.Outlined.FolderOpen,
                        label = "Library Size",
                        value = "${state.audio.size + state.video.size} items",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Jump Back In (Recent Tracks)
            if (state.history.isNotEmpty()) {
                Text(
                    text = "Jump Back In",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.history.take(4).forEach { item ->
                        val media = item.media
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onPlay(media) },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DesktopArtwork(media.artworkUri, media.title, 80.dp)
                                Text(
                                    text = media.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = media.artist ?: "Unknown Artist",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }


            // Offline Personalization Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Recommendations",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = "Queue suggestions tailored to your library and listening history.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.personalizationEnabled,
                            onCheckedChange = onTogglePersonalization,
                        )
                    }

                    if (state.personalizationEnabled && state.recommendations.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.recommendations.take(3).forEach { rec ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(rec.media.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                        Text(
                                            "${rec.media.artist ?: "Unknown"} · ${rec.explanation}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.primary,
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(onClick = { onPlay(rec.media) }, shape = RoundedCornerShape(6.dp)) {
                                            Text("Play", style = MaterialTheme.typography.labelSmall)
                                        }
                                        OutlinedButton(
                                            onClick = { onDismissRecommendation(rec.category.name, rec.media.id.value) },
                                            shape = RoundedCornerShape(6.dp),
                                        ) {
                                            Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Library Folders Status
            if (state.roots.isNotEmpty()) {
                Text("Configured Media Folders", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                state.roots.forEach { root ->
                    val scan = state.scanStates[root.id] ?: LibraryScanState.Idle
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(root.displayName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    "${root.mediaCount} indexed files · ${if (root.available) scanLabel(scan) else "Folder unavailable"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (scan is LibraryScanState.Scanning) {
                                    OutlinedButton(onClick = { onCancelScan(root.id) }) { Text("Cancel") }
                                } else {
                                    OutlinedButton(onClick = { onScan(root.id) }) { Text("Rescan") }
                                }
                                OutlinedButton(onClick = { onRemoveRoot(root.id) }) { Text("Remove") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = colors.primary, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(text = value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

/**
 * Modern Persistent Playback Bar docked at the bottom of the window.
 * Clean 3-column layout:
 * - Left: Artwork, Title, Artist, Favorite heart toggle
 * - Center: Transport controls (Shuffle, Previous, Play/Pause circle, Next, Repeat), Seekbar with timestamps
 * - Right: Volume slider + mute, Speed pill, Equalizer toggle, Stats toggle, Queue toggle
 */
@Composable
internal fun DesktopPlaybackBarView(
    state: PlaybackState,
    queueState: PlaybackQueueState,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onSetVolume: (Float) -> Unit,
    onSetSpeed: (Float) -> Unit,
    maxVolume: Float = 1f,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onToggleEqualizer: () -> Unit,
    showEqualizer: Boolean,
    onToggleDiagnostics: () -> Unit,
    showDiagnostics: Boolean,
    onToggleQueue: () -> Unit,
    queueVisible: Boolean,
    onOpenMedia: () -> Unit,
    onToggleLyrics: (() -> Unit)? = null,
    showLyrics: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val media = state.media
    val isPlaying = state.status == PlaybackStatus.Playing

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surfaceContainer,
        tonalElevation = 4.dp,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.4f)),
    ) {
        if (media == null) {
            // Idle State: Clean bar with quick CTA
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Ready to play",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface,
                        )
                        Text(
                            text = "Select a track from your library or open a local file",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Button(
                    onClick = onOpenMedia,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open Media", style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            // Active Media Playback Bar (3 columns)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // LEFT: Artwork, Title, Artist, Favorite
                Row(
                    modifier = Modifier.width(260.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DesktopArtwork(media.artworkUri, media.title, 48.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = media.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = media.artist ?: (if (media.kind == MediaKind.VIDEO) "Video" else "Unknown Artist"),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = { onToggleFavorite(media) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (media.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (media.isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (media.isFavorite) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // CENTER: Transport & Seekbar
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // Transport controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onToggleShuffle,
                            enabled = queueState.items.size > 1,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (queueState.shuffleEnabled) colors.primary else colors.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        IconButton(
                            onClick = onPrevious,
                            enabled = queueState.items.isNotEmpty(),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SkipPrevious,
                                contentDescription = "Previous",
                                tint = colors.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        // Play/Pause circular primary button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(colors.primary)
                                .clickable { onTogglePlayback() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = colors.onPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        IconButton(
                            onClick = onNext,
                            enabled = queueState.items.isNotEmpty(),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SkipNext,
                                contentDescription = "Next",
                                tint = colors.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        IconButton(
                            onClick = onCycleRepeat,
                            enabled = queueState.items.isNotEmpty(),
                            modifier = Modifier.size(28.dp),
                        ) {
                            val repeatIcon = if (queueState.repeatMode == QueueRepeatMode.ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat
                            val repeatTint = if (queueState.repeatMode != QueueRepeatMode.OFF) colors.primary else colors.onSurfaceVariant
                            Icon(
                                imageVector = repeatIcon,
                                contentDescription = "Repeat",
                                tint = repeatTint,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    // Seekbar with timestamps
                    Row(
                        modifier = Modifier.fillMaxWidth().height(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = formatDuration(state.positionMillis),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = colors.onSurfaceVariant,
                        )

                        Slider(
                            value = state.positionMillis.toFloat().coerceAtMost((state.durationMillis ?: 1).toFloat()),
                            onValueChange = { onSeek(it.toLong()) },
                            valueRange = 0f..(state.durationMillis ?: 1).coerceAtLeast(1).toFloat(),
                            enabled = state.isSeekable && state.durationMillis != null,
                            modifier = Modifier.weight(1f).height(20.dp),
                        )

                        if (media.kind == MediaKind.STREAM && state.durationMillis == null) {
                            Surface(
                                color = colors.errorContainer,
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    text = "LIVE",
                                    color = colors.onErrorContainer,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        } else {
                            Text(
                                text = formatDuration(state.durationMillis ?: 0L),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }

                // RIGHT: Volume, Speed, EQ, Stats, Queue
                Row(
                    modifier = Modifier.width(260.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    val isMuted = state.volume == 0f
                    IconButton(
                        onClick = { onSetVolume(if (isMuted) 1f else 0f) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Outlined.VolumeMute else Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    // Above 100% the track switches to the warning colour so amplification is never a surprise.
                    val boosted = state.volume > 1f
                    val volumeTint = if (boosted) colors.error else colors.primary
                    Slider(
                        value = state.volume,
                        onValueChange = onSetVolume,
                        valueRange = 0f..maxVolume,
                        colors = SliderDefaults.colors(thumbColor = volumeTint, activeTrackColor = volumeTint),
                        modifier = Modifier.width(96.dp).height(20.dp)
                            .semantics { stateDescription = "Volume ${(state.volume * 100).roundToInt()} percent" },
                    )
                    Text(
                        text = "${(state.volume * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = if (boosted) colors.error else colors.onSurfaceVariant,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End,
                    )

                    Spacer(Modifier.width(6.dp))

                    // Speed button
                    val speeds = dev.vimivo.playback.PlaybackSpeeds.PRESETS
                    Surface(
                        onClick = {
                            val nextIdx = (speeds.indexOfFirst { kotlin.math.abs(it - state.speed) < 0.05f } + 1) % speeds.size
                            onSetSpeed(speeds[nextIdx])
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = colors.surfaceContainerHigh,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    ) {
                        Text(
                            text = "${state.speed}x",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }

                    // Equalizer toggle
                    IconButton(
                        onClick = onToggleEqualizer,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "Equalizer",
                            tint = if (showEqualizer) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    // Stats toggle
                    IconButton(
                        onClick = onToggleDiagnostics,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Assessment,
                            contentDescription = "Diagnostics",
                            tint = if (showDiagnostics) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    // Lyrics button
                    if (onToggleLyrics != null && state.media?.kind == MediaKind.AUDIO) {
                        IconButton(
                            onClick = onToggleLyrics,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MusicNote,
                                contentDescription = if (showLyrics) "Hide lyrics" else "Show lyrics",
                                tint = if (showLyrics) colors.primary else colors.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    // Queue button
                    IconButton(
                        onClick = onToggleQueue,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.QueueMusic,
                            contentDescription = "Queue",
                            tint = if (queueVisible) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Universal empty state card for destinations.
 */
@Composable
internal fun DesktopEmptyState(
    destination: DesktopDestination,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                destination.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Text(destination.emptyTitle, style = MaterialTheme.typography.titleMedium)
            Text(
                destination.emptyDetail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val remMinutes = minutes % 60
        "%d:%02d:%02d".format(hours, remMinutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun scanLabel(scan: LibraryScanState): String = when (scan) {
    LibraryScanState.Idle -> "Idle"
    is LibraryScanState.Scanning -> "Scanning · ${scan.discovered} discovered"
    is LibraryScanState.Completed -> "Indexed ${scan.result.indexed} files"
    is LibraryScanState.Failed -> "Failed: ${scan.message}"
}
