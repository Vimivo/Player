package dev.vimivo.desktop.player

import dev.vimivo.core.lyrics.LrcLyricsParser
import dev.vimivo.core.lyrics.LyricsDocument
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.streaming.StreamValidationResult
import dev.vimivo.core.streaming.StreamValidator
import dev.vimivo.desktop.diagnostics.DesktopSmokeResult
import dev.vimivo.desktop.diagnostics.DesktopSmokeTest
import dev.vimivo.desktop.integration.mpris.MprisService
import dev.vimivo.desktop.library.DesktopLibraryController
import dev.vimivo.desktop.library.DesktopRecentMediaRepository
import dev.vimivo.desktop.playback.DesktopFullscreenControl
import dev.vimivo.desktop.playback.DesktopPlayerCommandExecutor
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.playback.PlaybackQueueController
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.RecentMediaEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
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
    private val library: DesktopLibraryController,
    private val recentMedia: DesktopRecentMediaRepository,
    private val scope: CoroutineScope,
) : AutoCloseable {
    private val local = MutableStateFlow(LocalState())
    private var mediaControls: MprisService? = null

    internal val commands = DesktopPlayerCommandExecutor(
        engine = engine,
        togglePlayback = { scope.launch { togglePlayback() } },
        toggleShuffle = queue::toggleShuffle,
        cycleRepeat = queue::cycleRepeat,
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
            maxVolume = engine.capabilities.maxVolume,
            presentation = localState.presentation,
            loadingTitle = localState.loadingTitle,
            resumePrompt = localState.resumePrompt,
            focusRequest = localState.focusRequest,
            openMode = localState.openMode,
            folderSiblings = localState.folderSiblings,
        )
    }.stateIn(scope, SharingStarted.Eagerly, PlayerState(maxVolume = engine.capabilities.maxVolume))

    /**
     * Starts background observers, opens the files the app was launched with, then follows files the OS
     * hands over later ([externalFiles]: Finder/"Open with", drag and drop, the Open menu).
     */
    fun start(launch: LaunchArguments, externalFiles: Flow<List<File>>) {
        scope.launch {
            loadPreferences()
            // Restore first so explicitly opened files replace, rather than race with, the saved paused queue.
            library.restoredQueue()?.let { snapshot -> runCatching { queue.restore(snapshot) } }
            launch { queue.state.drop(1).collect { library.saveQueue(queue.snapshot()) } }
            if (launch.files.isNotEmpty()) {
                openFiles(PlayerIntent.OpenFiles(launch.files, launch.startPositionMillis, launch.fullscreen))
            }
            externalFiles.collect { files -> openFiles(PlayerIntent.OpenFiles(files, fromRunningInstance = true)) }
        }
        scope.launch { clearLoadingOnceMediaPlays() }
        scope.launch { queue.skipped.collect { commands.report(HudMessage.Skipped(it.fileName ?: it.title)) } }
        scope.launch { library.observePlayback(engine.state) }
        scope.launch { recordRecentMedia() }
        scope.launch { loadLyricsForCurrentMedia() }
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
            is PlayerIntent.Seek -> engine.seekTo(intent.positionMillis)
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
            PlayerIntent.ShowLibrary -> local.update {
                it.copy(
                    presentation = PlayerPresentation.LIBRARY,
                    fullscreen = false
                )
            }
            is PlayerIntent.SetOpenMode -> {
                local.update { it.copy(openMode = intent.mode) }
                library.setUiSetting(OPEN_MODE_KEY, intent.mode.name)
            }
            is PlayerIntent.SetFolderSiblings -> {
                local.update { it.copy(folderSiblings = intent.enabled) }
                library.setUiSetting(FOLDER_SIBLINGS_KEY, intent.enabled.toString())
            }
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
                val start = library.preparePlayback(media).getOrNull()?.startPositionMillis ?: 0
                queue.playCurrent(start)
            }
        }
    }

    /** Plays each file through the real queue and engine and reports whether playback advanced. */
    suspend fun runSmokeTest(files: List<File>): Boolean {
        val play: suspend (MediaItem) -> Result<Unit> = { media -> queue.play(listOf(media)) }
        val results = DesktopSmokeTest.run(files, engine.state, ::mediaItemFor, play)
        DesktopSmokeTest.publish(results, System.getenv("VIMIVO_SMOKE_REPORT"))
        return results.isNotEmpty() && results.all { it is DesktopSmokeResult.Passed }
    }

    private suspend fun openFiles(request: PlayerIntent.OpenFiles) {
        val files = request.files.filter(File::isFile)
        attachSubtitles(files)
        val plan = LaunchPlanner.plan(files, local.value.folderSiblings) ?: return
        if (request.fromRunningInstance) local.update { it.copy(focusRequest = it.focusRequest + 1) }
        val enqueue = request.fromRunningInstance && local.value.openMode == OpenMode.ADD_TO_QUEUE
        if (enqueue && engine.state.value.media != null) {
            queue.addToQueue(plan.items)
            commands.report(HudMessage.AddedToQueue(plan.items.size))
            return
        }
        val first = plan.items[plan.startIndex]
        val resumeAt = request.startPositionMillis ?: savedPosition(first)
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
        files.mapNotNull(MpvPlayerEngine::externalSubtitleFor).forEach { subtitle ->
            engine.addExternalSubtitle(subtitle)
                .onSuccess { commands.report(HudMessage.SubtitleLoaded(subtitle.label)) }
                .onFailure { commands.report(HudMessage.SubtitleFailed(subtitle.label)) }
        }
    }

    /** Local files resume where they were left, using the shared resume rules; streams always start fresh. */
    private suspend fun savedPosition(media: MediaItem): Long =
        if (media.uri.startsWith("file:")) library.preparePlayback(media).getOrNull()?.startPositionMillis ?: 0 else 0

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
        val (mode, siblings) = withContext(Dispatchers.IO) {
            library.uiSetting(OPEN_MODE_KEY) to library.uiSetting(FOLDER_SIBLINGS_KEY)
        }
        local.update {
            it.copy(
                openMode = mode?.let { name -> runCatching { OpenMode.valueOf(name) }.getOrNull() } ?: OpenMode.REPLACE,
                folderSiblings = siblings?.toBooleanStrictOrNull() ?: true,
            )
        }
    }

    private suspend fun playFrom(items: List<MediaItem>, index: Int, label: String?) {
        val start = items.getOrNull(index) ?: return
        if (start.kind == MediaKind.VIDEO) local.update { it.copy(presentation = PlayerPresentation.VIDEO) }
        val resumeAt = library.preparePlayback(start).getOrNull()?.startPositionMillis ?: 0
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
        library.close()
    }

    private data class LocalState(
        val fullscreen: Boolean = false,
        val lyrics: LyricsDocument? = null,
        val presentation: PlayerPresentation = PlayerPresentation.LIBRARY,
        val loadingTitle: String? = null,
        val loadingUri: String? = null,
        val resumePrompt: ResumePrompt? = null,
        val focusRequest: Int = 0,
        val openMode: OpenMode = OpenMode.REPLACE,
        val folderSiblings: Boolean = true,
    )

    internal companion object {
        const val HUD_VISIBLE_MILLIS = 2_000L
        const val RESUME_PROMPT_MILLIS = 4_000L
        const val OPEN_MODE_KEY = "launch_open_mode"
        const val FOLDER_SIBLINGS_KEY = "launch_folder_siblings"

        fun mediaItemFor(file: File): MediaItem = LaunchPlanner.mediaItemFor(file)

        /** "Song.lrc" or "Song.en.lrc" beside a local file. */
        fun sidecarLyrics(uri: String): LyricsDocument? {
            if (!uri.startsWith("file:")) return null
            return runCatching {
                val mediaFile = File(URI(uri))
                val base = mediaFile.nameWithoutExtension
                val candidate = mediaFile.parentFile?.listFiles()?.firstOrNull { file ->
                    val stem = file.nameWithoutExtension
                    file.isFile && file.extension.equals("lrc", true) &&
                        (stem.equals(base, true) || stem.startsWith("$base.", true))
                }
                candidate?.takeIf(File::canRead)?.let { lrc ->
                    LrcLyricsParser.parse(lrc.readText(Charsets.UTF_8), lrc.toURI().toString())
                }
            }.getOrNull()
        }
    }
}
