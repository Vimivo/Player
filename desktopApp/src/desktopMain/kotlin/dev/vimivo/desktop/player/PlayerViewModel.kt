package dev.vimivo.desktop.player

import dev.vimivo.core.lyrics.LrcLyricsParser
import dev.vimivo.core.lyrics.LyricsDocument
import dev.vimivo.core.lyrics.LyricsSidecar
import dev.vimivo.core.model.Bookmark
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.streaming.StreamValidationResult
import dev.vimivo.core.streaming.StreamValidator
import dev.vimivo.desktop.diagnostics.AlwaysOnTopSmoke
import dev.vimivo.desktop.diagnostics.DesktopSmokeResult
import dev.vimivo.desktop.diagnostics.DesktopSmokeTest
import dev.vimivo.desktop.diagnostics.SnapshotSubtitleSmoke
import dev.vimivo.desktop.diagnostics.StartupTrace
import dev.vimivo.desktop.diagnostics.VideoPixelSample
import dev.vimivo.desktop.integration.mpris.MprisService
import dev.vimivo.desktop.library.DesktopPlaybackStore
import dev.vimivo.desktop.library.DesktopRecentMediaRepository
import dev.vimivo.desktop.platform.DesktopDirectories
import dev.vimivo.desktop.playback.DesktopFullscreenControl
import dev.vimivo.desktop.playback.DesktopPlayerCommandExecutor
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.desktop.playback.MpvSubtitleFiles
import dev.vimivo.desktop.playback.MpvWaveform
import dev.vimivo.desktop.playback.SnapshotPlan
import dev.vimivo.playback.PlaybackQueueController
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.RecentMediaEntry
import dev.vimivo.playback.SnapshotFormat
import dev.vimivo.playback.SnapshotOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

/**
 * Owns the desktop player: playback, queue, launch handling, HUD feedback and OS media-control updates.
 * The UI renders [state] and sends [PlayerIntent]s; it never drives the engine or queue for these actions.
 */
internal class PlayerViewModel(
    private val engine: MpvPlayerEngine,
    private val queue: PlaybackQueueController,
    private val store: DesktopPlaybackStore,
    private val recentMedia: DesktopRecentMediaRepository,
    private val scope: CoroutineScope,
    private val waveform: MpvWaveform = MpvWaveform(DesktopDirectories.resolve().waveforms),
) : AutoCloseable {
    private val local = MutableStateFlow(LocalState())
    private var mediaControls: MprisService? = null

    internal val commands = DesktopPlayerCommandExecutor(
        engine = engine,
        togglePlayback = { scope.launch { togglePlayback() } },
        toggleShuffle = queue::toggleShuffle,
        cycleRepeat = queue::cycleRepeat,
        snapshots = SnapshotPlan(options = { local.value.snapshot }),
        fullscreen = object : DesktopFullscreenControl {
            override fun toggle() = local.update { it.copy(fullscreen = !it.fullscreen) }
            override fun exit(): Boolean = local.value.fullscreen.also { wasFullscreen ->
                if (wasFullscreen) local.update { it.copy(fullscreen = false) }
            }
        },
    )

    val state: StateFlow<PlayerState> = combine(
        engine.state,
        queue.state,
        commands.status,
        commands.mediaInfoVisible,
        local,
    ) { playback, queueState, hud, mediaInfo, localState ->
        PlayerState(
            playback = playback,
            queue = queueState,
            hud = hud,
            fullscreen = localState.fullscreen,
            mediaInfoVisible = mediaInfo,
            lyrics = localState.lyrics,
            waveformPeaks = localState.waveformPeaks,
            maxVolume = engine.capabilities.maxVolume,
            presentation = localState.presentation,
            libraryRequest = localState.libraryRequest,
            bookmarks = localState.bookmarks,
            loadingTitle = localState.loadingTitle,
            resumePrompt = localState.resumePrompt,
            focusRequest = localState.focusRequest,
            openMode = localState.openMode,
            folderSiblings = localState.folderSiblings,
            panel = localState.panel,
            showRemainingTime = localState.showRemainingTime,
            controllerOffset = localState.controllerOffset,
            alwaysOnTop = localState.alwaysOnTop,
            autoHideControls = localState.autoHideControls,
            snapshot = localState.snapshot,
        )
    }.stateIn(scope, SharingStarted.Eagerly, PlayerState(maxVolume = engine.capabilities.maxVolume))

    /**
     * Starts background observers, opens the files the app was launched with, then follows files the OS
     * hands over later ([externalFiles]: Finder/"Open with", drag and drop, the Open menu).
     */
    fun start(launch: LaunchArguments, externalFiles: Flow<List<File>>) {
        LaunchPlanner.initialPresentation(launch.files)?.let { presentation ->
            // The player window shows in the first frame; the plan and resume position follow.
            val title = launch.files.first().nameWithoutExtension
            local.update { it.copy(presentation = presentation, loadingTitle = title) }
        }
        scope.launch {
            StartupTrace.mark("startRunning")
            loadPreferences()
            StartupTrace.mark("preferences")
            // Restore first so explicitly opened files replace, rather than race with, the saved paused queue.
            store.restoredQueue()?.let { snapshot -> runCatching { queue.restore(snapshot) } }
            StartupTrace.mark("restoredQueue")
            launch { queue.state.drop(1).collect { store.saveQueue(queue.snapshot()) } }
            if (launch.files.isNotEmpty()) {
                openFiles(PlayerIntent.OpenFiles(launch.files, launch.startPositionMillis, launch.fullscreen))
            }
            externalFiles.collect { files -> openFiles(PlayerIntent.OpenFiles(files, fromRunningInstance = true)) }
        }
        scope.launch { clearLoadingOnceMediaPlays() }
        scope.launch { followMediaKind() }
        scope.launch { trackBookmarks() }
        scope.launch { queue.skipped.collect { commands.report(HudMessage.Skipped(it.fileName ?: it.title)) } }
        scope.launch { store.observePlayback(engine.state) }
        scope.launch { recordRecentMedia() }
        scope.launch { loadLyricsForCurrentMedia() }
        scope.launch { loadWaveformForCurrentMedia() }
        scope.launch { clearHudAfterDelay() }
    }

    /** OS media controls (Now Playing, SMTC, MPRIS) follow playback once attached. */
    internal fun attachMediaControls(service: MprisService) {
        mediaControls = service
        scope.launch { engine.state.collect(service::updatePlayback) }
        scope.launch { queue.state.collect(service::updateQueue) }
    }

    fun dispatch(intent: PlayerIntent) {
        when (intent) {
            is PlayerIntent.Command -> commands.execute(intent.command)
            PlayerIntent.TogglePlayback -> scope.launch { togglePlayback() }
            PlayerIntent.Next -> scope.launch { queue.next() }
            PlayerIntent.Previous -> scope.launch { queue.previous() }
            PlayerIntent.Stop -> engine.stop()
            is PlayerIntent.Seek -> engine.seekTo(intent.positionMillis, intent.exact)
            is PlayerIntent.SetAbRepeat -> commands.perform("Repeat region") { engine.setABRepeat(intent.range) }
            is PlayerIntent.SetVolume -> commands.setVolumeLevel(intent.volume)
            is PlayerIntent.SetSpeed -> engine.setPlaybackSpeed(intent.speed)
            else -> dispatchNavigation(intent)
        }
    }

    /** Opening media, presentation, prompts and launch preferences. */
    private fun dispatchNavigation(intent: PlayerIntent) {
        when (intent) {
            is PlayerIntent.OpenFiles -> scope.launch { openFiles(intent) }
            is PlayerIntent.OpenLink -> scope.launch { openLink(intent.text) }
            is PlayerIntent.PlayLibraryItem -> scope.launch { playFrom(listOf(intent.media), 0, null) }
            is PlayerIntent.PlayFrom -> scope.launch { playFrom(intent.items, intent.index, intent.label) }
            is PlayerIntent.SetFullscreen -> local.update { it.copy(fullscreen = intent.enabled) }
            PlayerIntent.DismissMediaInfo -> commands.dismissMediaInfo()
            PlayerIntent.StartOver -> {
                engine.seekTo(0)
                local.update { it.copy(resumePrompt = null) }
            }
            PlayerIntent.DismissResume -> local.update { it.copy(resumePrompt = null) }
            PlayerIntent.ShowLibrary -> local.update { it.copy(libraryRequest = it.libraryRequest + 1) }
            PlayerIntent.ClosePlayer -> {
                // The player window hides; its video surface stays alive for the next video (see PlayerWindow).
                engine.stop()
                local.update {
                    it.copy(
                        presentation = PlayerPresentation.LIBRARY,
                        fullscreen = false,
                        loadingTitle = null,
                        loadingUri = null
                    )
                }
            }
            is PlayerIntent.SetOpenMode -> {
                local.update { it.copy(openMode = intent.mode) }
                scope.launch { store.setUiSetting(OPEN_MODE_KEY, intent.mode.name) }
            }
            is PlayerIntent.SetFolderSiblings -> {
                local.update { it.copy(folderSiblings = intent.enabled) }
                scope.launch { store.setUiSetting(FOLDER_SIBLINGS_KEY, intent.enabled.toString()) }
            }
            else -> dispatchPlayerUi(intent)
        }
    }

    /** Side panel, time display and fullscreen controller position; the last two are remembered. */
    private fun dispatchPlayerUi(intent: PlayerIntent) {
        when (intent) {
            is PlayerIntent.ShowPanel -> local.update { it.copy(panel = intent.panel) }
            PlayerIntent.ToggleAlwaysOnTop -> local.update { it.copy(alwaysOnTop = !it.alwaysOnTop) }
            is PlayerIntent.SetSnapshotOptions -> {
                local.update { it.copy(snapshot = intent.options) }
                scope.launch {
                    store.setUiSetting(SNAPSHOT_FORMAT_KEY, intent.options.format.name)
                    store.setUiSetting(SNAPSHOT_SUBTITLES_KEY, intent.options.includeSubtitles.toString())
                    store.setUiSetting(SNAPSHOT_FOLDER_KEY, intent.options.folder.orEmpty())
                }
            }
            PlayerIntent.ToggleAutoHideControls -> {
                val enabled = !local.value.autoHideControls
                local.update { it.copy(autoHideControls = enabled) }
                scope.launch { store.setUiSetting(AUTO_HIDE_CONTROLS_KEY, enabled.toString()) }
            }
            PlayerIntent.ToggleRemainingTime -> {
                val remaining = !local.value.showRemainingTime
                local.update { it.copy(showRemainingTime = remaining) }
                scope.launch { store.setUiSetting(REMAINING_TIME_KEY, remaining.toString()) }
            }
            is PlayerIntent.MoveController -> {
                local.update { it.copy(controllerOffset = intent.offset) }
                scope.launch { store.setUiSetting(CONTROLLER_OFFSET_KEY, "${intent.offset.x},${intent.offset.y}") }
            }
            else -> dispatchBookmark(intent)
        }
    }

    private fun dispatchBookmark(intent: PlayerIntent) {
        when (intent) {
            is PlayerIntent.AddBookmark -> engine.state.value.media?.let { media ->
                scope.launch { store.addBookmark(media.id, engine.state.value.positionMillis, intent.label) }
            }
            is PlayerIntent.RenameBookmark -> scope.launch { store.renameBookmark(intent.id, intent.label) }
            is PlayerIntent.DeleteBookmark -> scope.launch { store.deleteBookmark(intent.id) }
            else -> Unit
        }
    }

    /** Pauses active playback, resumes loaded media, or opens a restored queue entry only after a user action. */
    suspend fun togglePlayback() {
        val playback = engine.state.value
        when {
            playback.status == PlaybackStatus.Playing -> engine.pause()
            playback.media != null -> engine.play()
            else -> queue.state.value.currentMedia?.let { media ->
                val start = store.preparePlayback(media).getOrNull()?.startPositionMillis ?: 0
                queue.playCurrent(start)
            }
        }
    }

    /** The player window shows video for video and switches to the audio layout when an audio item follows. */
    private suspend fun followMediaKind() {
        engine.state.map { it.media?.kind }.distinctUntilChanged().collect { kind ->
            local.update { it.copy(presentation = nextPresentation(it.presentation, kind)) }
        }
    }

    private suspend fun trackBookmarks() {
        val currentMedia = engine.state.map { it.media?.id }.distinctUntilChanged()
        val refreshes = merge(currentMedia.map { }, store.changes)
        refreshes.collect {
            val id = engine.state.value.media?.id
            val bookmarks = if (id == null) {
                emptyList()
            } else {
                withContext(
                    Dispatchers.IO
                ) { runCatching { store.bookmarks(id) }.getOrDefault(emptyList()) }
            }
            local.update { it.copy(bookmarks = bookmarks) }
        }
    }

    /** Plays each file through the real queue and engine and reports whether playback advanced. */
    suspend fun runSmokeTest(
        files: List<File>,
        windowChanges: List<Pair<String, () -> Unit>> = emptyList(),
        probePicture: () -> VideoPixelSample? = { null },
        isAlwaysOnTop: () -> Boolean? = { null },
    ): Boolean {
        val play: suspend (MediaItem) -> Result<Unit> = { media -> queue.play(listOf(media)) }
        val results = DesktopSmokeTest.run(files, engine.state, ::mediaItemFor, play, probePicture = probePicture)
        // The picture must survive fullscreen and resize too; replay the first video for each window change.
        val video = files.firstOrNull { mediaItemFor(it).kind == MediaKind.VIDEO }
        val transitions = video?.let { file ->
            DesktopSmokeTest.checkTransitions(
                file,
                windowChanges,
                replay = { play(mediaItemFor(file)) },
                probePicture = probePicture,
                presentedFrame = engine::presentedFrame,
            )
        }.orEmpty()
        val snapshots = files.firstOrNull { it.name == SUBTITLED_SMOKE_FILE }?.let { file ->
            listOf(
                SnapshotSubtitleSmoke.check(
                    file,
                    replay = { play(mediaItemFor(file)) },
                    playback = { engine.state.value },
                    selectSubtitles = { engine.selectSubtitleTrack("1") },
                    pause = { engine.pause() },
                    snapshot = { subtitles, destination ->
                        engine.takeSnapshot(destination.path, SnapshotOptions(includeSubtitles = subtitles))
                    },
                ),
            )
        }.orEmpty()
        val onTop = video?.let { file ->
            listOf(
                AlwaysOnTopSmoke.check(
                    file,
                    toggle = { dispatch(PlayerIntent.ToggleAlwaysOnTop) },
                    isOnTop = isAlwaysOnTop,
                ),
            )
        }.orEmpty()
        val all = results + transitions + snapshots + onTop
        DesktopSmokeTest.publish(all, System.getenv("VIMIVO_SMOKE_REPORT"))
        // What the Info panel reports as the renderer (software rendering switches to performance mode).
        engine.containerInfo()?.rendering?.let { println("VIMIVO_SMOKE INFO rendering=$it") }
        return all.isNotEmpty() && all.all { it is DesktopSmokeResult.Passed }
    }

    private suspend fun openFiles(request: PlayerIntent.OpenFiles) {
        val files = request.files.filter(File::isFile)
        attachSubtitles(files)
        val plan = LaunchPlanner.plan(files, local.value.folderSiblings)
            ?: return local.update { it.copy(loadingTitle = null, loadingUri = null) }
        StartupTrace.mark("plan")
        if (request.fromRunningInstance) local.update { it.copy(focusRequest = it.focusRequest + 1) }
        val enqueue = request.fromRunningInstance && local.value.openMode == OpenMode.ADD_TO_QUEUE
        if (enqueue && engine.state.value.media != null) {
            queue.addToQueue(plan.items)
            commands.report(HudMessage.AddedToQueue(plan.items.size))
            return
        }
        val first = plan.items[plan.startIndex]
        val resumeAt = request.startPositionMillis ?: savedPosition(first)
        StartupTrace.mark("resume")
        local.update {
            it.copy(
                presentation = plan.presentation,
                loadingTitle = first.title,
                loadingUri = first.uri,
                resumePrompt = null,
                fullscreen = it.fullscreen || request.fullscreen,
            )
        }
        queue.play(plan.items, startIndex = plan.startIndex, startPositionMillis = resumeAt)
            .onFailure { local.update { it.copy(loadingTitle = null, loadingUri = null) } }
        if (request.startPositionMillis == null && resumeAt > 0) showResumePrompt(first.title, resumeAt)
    }

    /** Subtitle files dropped or opened while something plays attach to it instead of replacing it. */
    private fun attachSubtitles(files: List<File>) {
        if (engine.state.value.media == null) return
        files.mapNotNull(MpvSubtitleFiles::forFile).forEach { subtitle ->
            engine.addExternalSubtitle(subtitle)
                .onSuccess { commands.report(HudMessage.SubtitleLoaded(subtitle.label)) }
                .onFailure { commands.report(HudMessage.SubtitleFailed(subtitle.label)) }
        }
    }

    /** Local files resume where they were left, using the shared resume rules; streams always start fresh. */
    private suspend fun savedPosition(media: MediaItem): Long =
        if (media.uri.startsWith("file:")) store.preparePlayback(media).getOrNull()?.startPositionMillis ?: 0 else 0

    private fun showResumePrompt(title: String, positionMillis: Long) {
        val prompt = ResumePrompt(title, positionMillis)
        local.update { it.copy(resumePrompt = prompt) }
        scope.launch {
            delay(RESUME_PROMPT_MILLIS)
            local.update { if (it.resumePrompt == prompt) it.copy(resumePrompt = null) else it }
        }
    }

    private suspend fun openLink(text: String) {
        val link = text.trim()
        val local = File(link).takeIf { it.isAbsolute && it.isFile }
        if (local != null) return openFiles(PlayerIntent.OpenFiles(listOf(local)))
        when (val result = StreamValidator.validate(link)) {
            is StreamValidationResult.Valid -> {
                val item = MediaItem(
                    MediaId(result.normalizedUrl),
                    result.normalizedUrl,
                    result.displayTitle,
                    MediaKind.STREAM
                )
                this.local.update {
                    it.copy(
                        presentation = PlayerPresentation.VIDEO,
                        loadingTitle = item.title,
                        loadingUri = item.uri,
                        resumePrompt = null
                    )
                }
                commands.report(HudMessage.StreamOpened)
                queue.play(
                    listOf(item)
                ).onFailure { this.local.update { it.copy(loadingTitle = null, loadingUri = null) } }
            }
            else -> commands.report(HudMessage.ClipboardNotUrl)
        }
    }

    /** The loading card stays until the opened item actually plays (or fails), never on a black surface. */
    private suspend fun clearLoadingOnceMediaPlays() {
        engine.state.collect { playback ->
            val loading = local.value.loadingUri ?: return@collect
            val sameMedia = playback.media?.uri == loading
            val started = playback.status == PlaybackStatus.Playing && playback.positionMillis > 0
            if (sameMedia && (started || playback.status is PlaybackStatus.Error)) {
                local.update { it.copy(loadingTitle = null, loadingUri = null) }
            }
        }
    }

    private suspend fun loadPreferences() {
        val keys = listOf(
            OPEN_MODE_KEY,
            FOLDER_SIBLINGS_KEY,
            REMAINING_TIME_KEY,
            CONTROLLER_OFFSET_KEY,
            AUTO_HIDE_CONTROLS_KEY,
            SNAPSHOT_FORMAT_KEY,
            SNAPSHOT_SUBTITLES_KEY,
            SNAPSHOT_FOLDER_KEY,
        )
        val saved = withContext(Dispatchers.IO) {
            StartupTrace.mark("preferencesIo")
            keys.associateWith(store::uiSetting)
        }
        local.update {
            it.copy(
                openMode = saved[OPEN_MODE_KEY]?.let { name -> runCatching { OpenMode.valueOf(name) }.getOrNull() }
                    ?: OpenMode.REPLACE,
                folderSiblings = saved[FOLDER_SIBLINGS_KEY]?.toBooleanStrictOrNull() ?: true,
                showRemainingTime = saved[REMAINING_TIME_KEY]?.toBooleanStrictOrNull() ?: false,
                autoHideControls = saved[AUTO_HIDE_CONTROLS_KEY]?.toBooleanStrictOrNull() ?: false,
                snapshot = SnapshotOptions(
                    format = saved[SNAPSHOT_FORMAT_KEY]
                        ?.let { name -> runCatching { SnapshotFormat.valueOf(name) }.getOrNull() }
                        ?: SnapshotFormat.PNG,
                    includeSubtitles = saved[SNAPSHOT_SUBTITLES_KEY]?.toBooleanStrictOrNull() ?: false,
                    folder = saved[SNAPSHOT_FOLDER_KEY]?.takeIf(String::isNotBlank),
                ),
                controllerOffset = saved[CONTROLLER_OFFSET_KEY]?.split(',')?.mapNotNull(String::toIntOrNull)
                    ?.takeIf { offset -> offset.size == 2 }?.let { (x, y) -> ControllerOffset(x, y) }
                    ?: ControllerOffset(0, 0),
            )
        }
    }

    private suspend fun playFrom(items: List<MediaItem>, index: Int, label: String?) {
        val start = items.getOrNull(index) ?: return
        if (start.kind == MediaKind.VIDEO) local.update { it.copy(presentation = PlayerPresentation.VIDEO) }
        val resumeAt = store.preparePlayback(start).getOrNull()?.startPositionMillis ?: 0
        queue.play(items, startIndex = index, startPositionMillis = resumeAt, sourceLabel = label)
    }

    private suspend fun recordRecentMedia() {
        // Only local files the user opened are remembered; stream URLs stay in the redacted stream history.
        engine.state.collect { playback ->
            val media = playback.media ?: return@collect
            if (media.uri.startsWith("file:") && playback.status == PlaybackStatus.Playing) {
                recentMedia.record(RecentMediaEntry(media.uri, media.title, media.kind == MediaKind.VIDEO))
            }
        }
    }

    /** Computes the real peaks of the current audio file in the background; the timeline is a plain line until then. */
    private suspend fun loadWaveformForCurrentMedia() {
        engine.state.distinctUntilChangedBy { it.media?.uri }.collectLatest { playback ->
            local.update { it.copy(waveformPeaks = null) }
            val media = playback.media
            val path = media?.takeIf { it.kind == MediaKind.AUDIO && it.uri.startsWith("file:") }
                ?.let { runCatching { File(URI(it.uri)).path }.getOrNull() }
            if (path != null) {
                val peaks = waveform.peaks(path)
                local.update { if (engine.state.value.media?.uri == media.uri) it.copy(waveformPeaks = peaks) else it }
            }
        }
    }

    private suspend fun loadLyricsForCurrentMedia() {
        engine.state.distinctUntilChangedBy { it.media?.uri }.collect { playback ->
            val lyrics = playback.media?.uri?.let { uri -> withContext(Dispatchers.IO) { sidecarLyrics(uri) } }
            local.update { it.copy(lyrics = lyrics) }
        }
    }

    private suspend fun clearHudAfterDelay() {
        commands.status.collect { message ->
            if (message != null) {
                delay(HUD_VISIBLE_MILLIS)
                if (commands.status.value === message) commands.clearStatus()
            }
        }
    }

    private var closed = false

    /** Idempotent: called explicitly at exit and again by Koin when the graph closes. */
    override fun close() {
        if (closed) return
        closed = true
        mediaControls?.close()
        queue.close()
        engine.close()
    }

    private data class LocalState(
        val fullscreen: Boolean = false,
        val lyrics: LyricsDocument? = null,
        val waveformPeaks: FloatArray? = null,
        val presentation: PlayerPresentation = PlayerPresentation.LIBRARY,
        val loadingTitle: String? = null,
        val loadingUri: String? = null,
        val resumePrompt: ResumePrompt? = null,
        val focusRequest: Int = 0,
        val libraryRequest: Int = 0,
        val bookmarks: List<Bookmark> = emptyList(),
        val openMode: OpenMode = OpenMode.REPLACE,
        val folderSiblings: Boolean = true,
        val panel: PlayerPanel? = null,
        val showRemainingTime: Boolean = false,
        val controllerOffset: ControllerOffset = ControllerOffset(0, 0),
        val alwaysOnTop: Boolean = false,
        val autoHideControls: Boolean = false,
        val snapshot: SnapshotOptions = SnapshotOptions(),
    )

    internal companion object {
        /** The smoke fixture with an embedded subtitle track, used to prove subtitles reach saved snapshots. */
        private const val SUBTITLED_SMOKE_FILE = "video_sample.mkv"

        const val HUD_VISIBLE_MILLIS = 2_000L
        const val RESUME_PROMPT_MILLIS = 4_000L
        const val OPEN_MODE_KEY = "launch_open_mode"
        const val FOLDER_SIBLINGS_KEY = "launch_folder_siblings"
        const val REMAINING_TIME_KEY = "player_show_remaining_time"
        const val CONTROLLER_OFFSET_KEY = "player_fullscreen_controller_offset"
        const val AUTO_HIDE_CONTROLS_KEY = "player_auto_hide_controls"
        const val SNAPSHOT_FORMAT_KEY = "player_snapshot_format"
        const val SNAPSHOT_SUBTITLES_KEY = "player_snapshot_subtitles"
        const val SNAPSHOT_FOLDER_KEY = "player_snapshot_folder"

        fun mediaItemFor(file: File): MediaItem = LaunchPlanner.mediaItemFor(file)

        /** "Song.lrc" or "Song.en.lrc" beside a local file. */
        fun sidecarLyrics(uri: String): LyricsDocument? {
            if (!uri.startsWith("file:")) return null
            return runCatching {
                val mediaFile = File(URI(uri))
                val folder = mediaFile.parentFile ?: return@runCatching null
                val chosen = LyricsSidecar.best(mediaFile.name, folder.list()?.toList().orEmpty())
                chosen?.let { File(folder, it) }?.takeIf { it.isFile && it.canRead() }?.let { lrc ->
                    LrcLyricsParser.parse(lrc.readText(Charsets.UTF_8), lrc.toURI().toString())
                }
            }.getOrNull()
        }
    }
}
