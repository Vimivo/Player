@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "ImportOrdering", "Indentation",
    "LongParameterList", "MaximumLineLength", "MaxLineLength", "ParameterListWrapping",
    "TooGenericExceptionCaught", "TooManyFunctions", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.AlbumDetails
import dev.vimivo.core.model.AlbumItem
import dev.vimivo.core.model.ArtistDetails
import dev.vimivo.core.model.ArtistItem
import dev.vimivo.core.model.AutomationActionType
import dev.vimivo.core.model.AutomationTrigger
import dev.vimivo.core.model.Bookmark
import dev.vimivo.core.model.DuplicateGroup
import dev.vimivo.core.model.JournalEntry
import dev.vimivo.core.model.LibraryHealthReport
import dev.vimivo.core.model.ListeningInsights
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MediaPlaybackPreference
import dev.vimivo.core.model.MetadataChangeset
import dev.vimivo.core.model.MetadataWriteMode
import dev.vimivo.core.model.MissingMediaCandidate
import dev.vimivo.core.model.OrganizePlanItem
import dev.vimivo.core.model.PlaybackHistory
import dev.vimivo.core.model.Playlist
import dev.vimivo.core.model.RecommendationItem
import dev.vimivo.core.model.LibrarySort
import dev.vimivo.core.model.RenamePlanItem
import dev.vimivo.core.model.SmartPlaylistDefinition
import dev.vimivo.core.model.SmartRuleCondition
import dev.vimivo.core.model.WatchingInsights
import dev.vimivo.core.model.WorkflowRule
import dev.vimivo.playback.PlaybackQueueSnapshot
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.QueueItem
import dev.vimivo.settings.LibraryViewMode
import dev.vimivo.settings.RepeatBehavior
import dev.vimivo.settings.ShuffleBehavior
import dev.vimivo.settings.ThemeMode
import java.io.File
import java.net.URI
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class DesktopLibraryUiState(
    val roots: List<LibraryRoot> = emptyList(),
    val audio: List<MediaItem> = emptyList(),
    val video: List<MediaItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val smartPlaylists: List<SmartPlaylistDefinition> = emptyList(),
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val recommendations: List<RecommendationItem> = emptyList(),
    val listeningInsights: ListeningInsights? = null,
    val watchingInsights: WatchingInsights? = null,
    val workflowRules: List<WorkflowRule> = emptyList(),
    val journalEntries: List<JournalEntry> = emptyList(),
    val healthReport: LibraryHealthReport? = null,
    val selectedMediaIds: Set<MediaId> = emptySet(),
    val personalizationEnabled: Boolean = true,
    val personalMedia: Map<MediaId, MediaItem> = emptyMap(),
    val favorites: List<MediaItem> = emptyList(),
    val history: List<DesktopHistoryItem> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val scanStates: Map<String, LibraryScanState> = emptyMap(),
    val search: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val libraryViewMode: LibraryViewMode = LibraryViewMode.LIST,
    val defaultSort: LibrarySort = LibrarySort.TITLE,
    val reduceAnimations: Boolean = false,
    val highContrast: Boolean = false,
    val reduceTransparency: Boolean = false,
    val resumePlayback: Boolean = true,
    val defaultSpeed: Float = 1.0f,
    val repeatBehavior: RepeatBehavior = RepeatBehavior.REMEMBER,
    val shuffleBehavior: ShuffleBehavior = ShuffleBehavior.REMEMBER,
    val loading: Boolean = true,
    val error: String? = null,
)

internal data class DesktopPlaybackRequest(val media: MediaItem, val startPositionMillis: Long)
internal data class DesktopHistoryItem(val media: MediaItem, val history: PlaybackHistory)

internal class DesktopLibraryController(
    private val database: DesktopDatabase = DesktopDatabase.open(),
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository = DesktopLibraryRepository(database)
    private val personalRepository = DesktopPersonalMediaRepository(database)
    private val queueRepository = DesktopPlaybackQueueRepository(database)
    val metadataEditor = DesktopMetadataEditorService(database)
    val renameOrganizer = DesktopBatchRenameOrganizer(database)
    val missingMediaRelinker = DesktopMissingMediaRelinker(database)
    val automationEngine = DesktopAutomationEngine(database)
    val smartPlaylistRepo = DesktopSmartPlaylistRepository(database)
    val duplicateService = DesktopDuplicateDetectionService(database)
    val recommendationEngine = DesktopRecommendationEngine(database)
    val insightsCalculator = DesktopInsightsCalculator()
    val csvExporterImporter = DesktopMetadataCsvExporterImporter(metadataEditor)
    val mediaCache = DesktopMediaCache()
    private val coordinator = LibraryScanCoordinator(
        repository,
        DesktopMediaScanner(database),
        DesktopMetadataCoordinator(DesktopMetadataRepository(database), DesktopMediaMetadataExtractor(), scope) {
            repository.invalidate()
        },
        scope,
    )
    private val mutableState = MutableStateFlow(DesktopLibraryUiState())
    private val scanObservers = mutableMapOf<String, Job>()
    val state: StateFlow<DesktopLibraryUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            repository.generation.collectLatest { refresh() }
        }
    }

    fun setSearch(query: String) {
        mutableState.value = mutableState.value.copy(search = query)
        scope.launch { refresh() }
    }

    fun addFolder() = scope.launch {
        val selected = withContext(Dispatchers.IO) { DesktopFolderPicker.chooseDirectory().getOrThrow() } ?: return@launch
        repository.addRoot(selected.toPath()).onSuccess { root ->
            observeScan(root)
            coordinator.start(root.id)
        }.onFailure(::report)
    }

    fun scan(rootId: String) = coordinator.start(rootId)
    fun cancelScan(rootId: String) = coordinator.cancel(rootId)

    fun removeRoot(rootId: String) = scope.launch {
        coordinator.cancel(rootId)
        repository.removeRoot(rootId).onFailure(::report)
    }

    fun refreshAvailability() = scope.launch {
        repository.refreshAvailability().onFailure(::report)
    }

    fun createPlaylist(name: String) = mutatePersonalData { personalRepository.createPlaylist(name) }
    fun renamePlaylist(id: String, name: String) = mutatePersonalData { personalRepository.renamePlaylist(id, name) }
    fun deletePlaylist(id: String) = mutatePersonalData { personalRepository.deletePlaylist(id) }
    fun duplicatePlaylist(id: String) = mutatePersonalData { personalRepository.duplicatePlaylist(id) }
    fun addToPlaylist(playlistId: String, media: MediaItem) =
        mutatePersonalData { personalRepository.addToPlaylist(playlistId, media.id) }
    fun removeFromPlaylist(playlistId: String, mediaId: MediaId) =
        mutatePersonalData { personalRepository.removeFromPlaylist(playlistId, mediaId) }
    fun movePlaylistItem(playlistId: String, mediaId: MediaId, position: Int) =
        mutatePersonalData { personalRepository.movePlaylistItem(playlistId, mediaId, position) }
    fun exportPlaylistM3u(playlistId: String, targetFile: File): Result<Unit> = runCatching {
        val playlist = personalRepository.playlists().firstOrNull { it.id == playlistId }
            ?: error("Playlist was not found")
        val items = repository.media(playlist.items)
        val entries = items.map { item ->
            dev.vimivo.core.playlist.PlaylistItemEntry(
                uri = item.uri,
                title = listOfNotNull(item.artist, item.title).joinToString(" - "),
                durationSeconds = item.durationMillis?.let { (it / 1000).toInt() },
            )
        }
        val content = if (dev.vimivo.core.playlist.PlaylistXspfHandler.isXspf(targetFile.name)) {
            dev.vimivo.core.playlist.PlaylistXspfHandler.export(playlist.name, entries)
        } else {
            dev.vimivo.core.playlist.PlaylistM3uHandler.exportM3u8(playlist.name, entries)
        }
        targetFile.writeText(content, Charsets.UTF_8)
    }
    fun importPlaylistM3u(file: File) = mutatePersonalData {
        require(file.isFile && file.canRead()) { "Selected file cannot be read" }
        val content = file.readText(Charsets.UTF_8)
        val parsed = if (dev.vimivo.core.playlist.PlaylistXspfHandler.isXspf(file.name)) {
            dev.vimivo.core.playlist.PlaylistXspfHandler.parse(content, file.nameWithoutExtension)
        } else {
            dev.vimivo.core.playlist.PlaylistM3uHandler.parseM3u(content, file.nameWithoutExtension)
        }
        val playlist = personalRepository.createPlaylist(parsed.name)
        parsed.entries.forEach { entry ->
            val candidate = repository.findMediaByUriOrFileName(entry.uri)
                ?: repository.findMediaByUriOrFileName(entry.uri.substringAfterLast('/').substringAfterLast('\\'))
            if (candidate != null) {
                personalRepository.addToPlaylist(playlist.id, candidate.id)
            }
        }
    }
    fun toggleFavorite(media: MediaItem) = mutatePersonalData {
        personalRepository.setFavorite(FavoriteTarget.MEDIA, media.id.value, !media.isFavorite)
    }
    fun addMultipleToPlaylist(playlistId: String, mediaIds: List<MediaId>) = mutatePersonalData {
        personalRepository.addMultipleToPlaylist(playlistId, mediaIds)
    }
    fun removeMultipleFromPlaylist(playlistId: String, mediaIds: List<MediaId>) = mutatePersonalData {
        personalRepository.removeMultipleFromPlaylist(playlistId, mediaIds)
    }
    fun setFavoritesBulk(mediaIds: List<MediaId>, favorite: Boolean) = mutatePersonalData {
        personalRepository.setFavoritesBulk(FavoriteTarget.MEDIA, mediaIds.map { it.value }, favorite)
    }
    fun saveMediaPreference(pref: MediaPlaybackPreference) = mutatePersonalData {
        personalRepository.saveMediaPreference(pref)
    }
    fun mediaPreference(mediaId: MediaId): MediaPlaybackPreference? =
        personalRepository.mediaPreference(mediaId)
    fun clearMediaPreference(mediaId: MediaId) = mutatePersonalData {
        personalRepository.clearMediaPreference(mediaId)
    }
    fun cleanArtworkCache(): Long = mediaCache.cleanAllCache()

    fun removeHistory(mediaId: MediaId) = mutatePersonalData { personalRepository.removeHistory(mediaId) }
    fun clearHistory() = mutatePersonalData { personalRepository.clearHistory() }
    fun setThemeMode(mode: ThemeMode): Job {
        mutableState.value = mutableState.value.copy(themeMode = mode)
        return mutatePersonalData { personalRepository.putSetting("theme_mode", mode.name) }
    }
    fun setLibraryViewMode(mode: LibraryViewMode): Job {
        mutableState.value = mutableState.value.copy(libraryViewMode = mode)
        return mutatePersonalData { personalRepository.putSetting("library_view_mode", mode.name) }
    }
    fun setDefaultSort(sort: LibrarySort): Job {
        mutableState.value = mutableState.value.copy(defaultSort = sort)
        return mutatePersonalData { personalRepository.putSetting("default_sort", sort.name) }
    }
    fun setReduceAnimations(enabled: Boolean): Job {
        mutableState.value = mutableState.value.copy(reduceAnimations = enabled)
        return mutatePersonalData { personalRepository.putSetting("reduce_animations", enabled.toString()) }
    }
    fun setHighContrast(enabled: Boolean): Job {
        mutableState.value = mutableState.value.copy(highContrast = enabled)
        return mutatePersonalData { personalRepository.putSetting("high_contrast", enabled.toString()) }
    }
    fun setReduceTransparency(enabled: Boolean): Job {
        mutableState.value = mutableState.value.copy(reduceTransparency = enabled)
        return mutatePersonalData { personalRepository.putSetting("reduce_transparency", enabled.toString()) }
    }
    fun setResumePlayback(enabled: Boolean): Job {
        mutableState.value = mutableState.value.copy(resumePlayback = enabled)
        return mutatePersonalData { personalRepository.putSetting("resume_playback", enabled.toString()) }
    }
    fun setDefaultSpeed(speed: Float): Job {
        val clamped = speed.coerceIn(0.25f, 3.0f)
        mutableState.value = mutableState.value.copy(defaultSpeed = clamped)
        return mutatePersonalData { personalRepository.putSetting("default_speed", clamped.toString()) }
    }
    fun setRepeatBehavior(behavior: RepeatBehavior): Job {
        mutableState.value = mutableState.value.copy(repeatBehavior = behavior)
        return mutatePersonalData { personalRepository.putSetting("repeat_behavior", behavior.name) }
    }
    fun setShuffleBehavior(behavior: ShuffleBehavior): Job {
        mutableState.value = mutableState.value.copy(shuffleBehavior = behavior)
        return mutatePersonalData { personalRepository.putSetting("shuffle_behavior", behavior.name) }
    }
    fun resetSettings(): Job {
        mutableState.value = mutableState.value.copy(
            themeMode = ThemeMode.SYSTEM,
            libraryViewMode = LibraryViewMode.LIST,
            defaultSort = LibrarySort.TITLE,
            reduceAnimations = false,
            highContrast = false,
            reduceTransparency = false,
            resumePlayback = true,
            defaultSpeed = 1.0f,
            repeatBehavior = RepeatBehavior.REMEMBER,
            shuffleBehavior = ShuffleBehavior.REMEMBER,
        )
        return mutatePersonalData {
            personalRepository.clearSettings()
        }
    }
    fun uiSetting(key: String): String? = personalRepository.setting(key)
    fun setUiSetting(key: String, value: String) = mutatePersonalData {
        personalRepository.putSetting(key, value)
    }
    fun addBookmark(mediaId: MediaId, positionMillis: Long, label: String?) =
        mutatePersonalData { personalRepository.addBookmark(mediaId, positionMillis, label) }
    fun renameBookmark(id: String, label: String) = mutatePersonalData { personalRepository.renameBookmark(id, label) }
    fun albumDetails(albumId: String): AlbumDetails? = repository.albumDetails(albumId)
    fun artistDetails(artistId: String): ArtistDetails? = repository.artistDetails(artistId)
    fun deleteBookmark(id: String) = mutatePersonalData { personalRepository.deleteBookmark(id) }

    /**
     * Persists indexed media only; transient file and network entries cannot be restored safely, so they are
     * filtered while preserving occurrence identities, ordering, and the nearest active entry.
     */
    suspend fun saveQueue(snapshot: PlaybackQueueSnapshot): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val indexed = queueRepository.indexedMediaIds(snapshot.playbackItems.map { it.media.id })
            val persistable = snapshot.retainOnly { it.id in indexed }
            if (persistable.playbackItems.isEmpty()) queueRepository.clear() else queueRepository.save(persistable)
        }.onFailure(::report)
    }

    /**
     * Restores a paused queue. Temporarily unavailable media (for example an unmounted drive) is kept and marked
     * unavailable so the queue can skip it; media removed from the library is dropped. Corrupt state is discarded.
     */
    suspend fun restoredQueue(): PlaybackQueueSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            val stored = queueRepository.load() ?: return@runCatching null
            val mediaById = repository.media(stored.items.map(StoredQueueItem::mediaId), includeUnavailable = true).associateBy { it.id }
            val entries = stored.items.map { storedItem -> storedItem to mediaById[storedItem.mediaId] }
            val queueItem = { pair: Pair<StoredQueueItem, MediaItem?> ->
                QueueItem(pair.first.queueId, pair.second ?: MediaItem(pair.first.mediaId, "", "", MediaKind.AUDIO, isAvailable = false))
            }
            val snapshot = PlaybackQueueSnapshot(
                sourceItems = entries.sortedBy { it.first.sourcePosition }.map(queueItem),
                playbackItems = entries.sortedBy { it.first.playbackPosition }.map(queueItem),
                currentQueueId = stored.currentQueueId,
                shuffleEnabled = stored.shuffleEnabled,
                repeatMode = stored.repeatMode,
                sourceLabel = stored.sourceLabel,
            ).retainOnly { it.id in mediaById }
            snapshot.takeIf { it.playbackItems.isNotEmpty() }
        }.getOrElse { error ->
            report(IllegalStateException("The saved playback queue could not be restored and was discarded", error))
            runCatching { queueRepository.clear() }
            null
        }
    }

    suspend fun playlistMedia(playlistId: String): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val playlist = personalRepository.playlists().firstOrNull { it.id == playlistId }
                ?: error("Playlist was not found")
            repository.media(playlist.items)
        }.onFailure(::report)
    }

    suspend fun preparePlayback(media: MediaItem): Result<DesktopPlaybackRequest> = withContext(Dispatchers.IO) {
        runCatching {
            require(media.uri.startsWith("file:")) { "Only indexed local files can be opened from the library" }
            require(File(URI(media.uri)).isFile) { "The indexed media file is unavailable" }
            val resumeEnabled = personalRepository.setting("resume_playback")?.toBooleanStrictOrNull() ?: true
            val pref = personalRepository.mediaPreference(media.id)
            val pos = if (resumeEnabled && (pref?.resumeEligible != false)) {
                resumePositionMillis(media, personalRepository.position(media.id))
            } else {
                0L
            }
            DesktopPlaybackRequest(media, pos)
        }.onFailure(::report)
    }

    suspend fun observePlayback(playback: StateFlow<PlaybackState>) {
        var mediaId: String? = null
        var persistable = false
        var historyRecorded = false
        var completionSaved = false
        var lastSavedPosition = -PLAYBACK_SAVE_INTERVAL_MILLIS
        var previousState: PlaybackState? = null
        playback.collect { playbackState ->
            val media = playbackState.media
            if (media?.id?.value != mediaId) {
                val leaving = previousState
                val leavingMedia = leaving?.media
                if (persistable && leavingMedia != null) {
                    withContext(Dispatchers.IO) {
                        personalRepository.savePosition(
                            leavingMedia.id,
                            leaving.positionMillis,
                            leaving.durationMillis,
                            leaving.status == PlaybackStatus.Completed,
                        )
                    }
                }
                mediaId = media?.id?.value
                persistable = media != null && withContext(Dispatchers.IO) { personalRepository.containsMedia(media.id) }
                historyRecorded = false
                completionSaved = false
                lastSavedPosition = -PLAYBACK_SAVE_INTERVAL_MILLIS
            }
            if (media == null || !persistable) {
                previousState = playbackState
                return@collect
            }
            if (playbackState.status == PlaybackStatus.Playing && !historyRecorded) {
                withContext(Dispatchers.IO) {
                    personalRepository.recordPlayback(media.id, playbackState.positionMillis, completed = false)
                }
                historyRecorded = true
                refresh()
            }
            val completed = playbackState.status == PlaybackStatus.Completed
            val shouldPersist = when {
                completed && !completionSaved -> true
                playbackState.status == PlaybackStatus.Paused -> playbackState.positionMillis != lastSavedPosition
                playbackState.status == PlaybackStatus.Playing ->
                    playbackState.positionMillis - lastSavedPosition >= PLAYBACK_SAVE_INTERVAL_MILLIS
                else -> false
            }
            if (shouldPersist) {
                withContext(Dispatchers.IO) {
                    personalRepository.savePosition(
                        media.id,
                        playbackState.positionMillis,
                        playbackState.durationMillis,
                        completed,
                    )
                    if (completed) personalRepository.markPlaybackCompleted(media.id, playbackState.positionMillis)
                }
                lastSavedPosition = playbackState.positionMillis
                completionSaved = completed
                if (completed) refresh()
            }
            previousState = playbackState
        }
    }

    private data class GenerationCache(
        val generation: Long,
        val roots: List<LibraryRoot>,
        val playlists: List<Playlist>,
        val favoriteIdSet: Set<String>,
        val favorites: List<MediaItem>,
        val personalMedia: Map<MediaId, MediaItem>,
        val history: List<PlaybackHistory>,
        val themeMode: ThemeMode,
        val libraryViewMode: LibraryViewMode,
        val defaultSort: LibrarySort,
        val reduceAnimations: Boolean,
        val highContrast: Boolean,
        val reduceTransparency: Boolean,
        val resumePlayback: Boolean,
        val defaultSpeed: Float,
        val repeatBehavior: RepeatBehavior,
        val shuffleBehavior: ShuffleBehavior,
        val smartPlaylists: List<SmartPlaylistDefinition>,
        val workflowRules: List<WorkflowRule>,
        val journalEntries: List<JournalEntry>,
        val personalizationEnabled: Boolean,
        val recommendations: List<RecommendationItem>,
        val listeningInsights: ListeningInsights?,
        val watchingInsights: WatchingInsights?,
        val healthReport: LibraryHealthReport?,
    )

    @Volatile
    private var generationCache: GenerationCache? = null

    private suspend fun refresh() = withContext(Dispatchers.IO) {
        runCatching {
            val query = mutableState.value.search.trim()
            val currentGen = repository.generation.value
            val cache = generationCache?.takeIf { it.generation == currentGen } ?: run {
                val roots = repository.roots()
                val playlists = personalRepository.playlists()
                val favoriteIds = personalRepository.favoriteIds(FavoriteTarget.MEDIA).map(::MediaId)
                val favorites = repository.media(favoriteIds).map { it.copy(isFavorite = true) }
                val favoriteIdSet = favoriteIds.mapTo(mutableSetOf(), MediaId::value)
                val history = personalRepository.history()
                val themeMode = personalRepository.setting("theme_mode")?.let {
                    runCatching { ThemeMode.valueOf(it) }.getOrNull()
                } ?: ThemeMode.SYSTEM
                val libraryViewMode = personalRepository.setting("library_view_mode")?.let {
                    runCatching { LibraryViewMode.valueOf(it) }.getOrNull()
                } ?: LibraryViewMode.LIST
                val defaultSort = personalRepository.setting("default_sort")?.let {
                    runCatching { LibrarySort.valueOf(it) }.getOrNull()
                } ?: LibrarySort.TITLE
                val reduceAnimations = personalRepository.setting("reduce_animations")?.toBooleanStrictOrNull() ?: false
                val highContrast = personalRepository.setting("high_contrast")?.toBooleanStrictOrNull() ?: false
                val reduceTransparency = personalRepository.setting("reduce_transparency")?.toBooleanStrictOrNull() ?: false
                val resumePlayback = personalRepository.setting("resume_playback")?.toBooleanStrictOrNull() ?: true
                val defaultSpeed = personalRepository.setting("default_speed")?.toFloatOrNull() ?: 1.0f
                val repeatBehavior = personalRepository.setting("repeat_behavior")?.let {
                    runCatching { RepeatBehavior.valueOf(it) }.getOrNull()
                } ?: RepeatBehavior.REMEMBER
                val shuffleBehavior = personalRepository.setting("shuffle_behavior")?.let {
                    runCatching { ShuffleBehavior.valueOf(it) }.getOrNull()
                } ?: ShuffleBehavior.REMEMBER
                val personalMedia = repository.media(
                    playlists.flatMap(Playlist::items) + favoriteIds + history.map(PlaybackHistory::mediaId),
                ).associateBy { it.id }

                val allAudio = repository.allMedia(MediaKind.AUDIO).map { it.copy(isFavorite = it.id.value in favoriteIdSet) }
                val allVideo = repository.allMedia(MediaKind.VIDEO).map { it.copy(isFavorite = it.id.value in favoriteIdSet) }
                val allMediaList = allAudio + allVideo

                val smartPlaylists = smartPlaylistRepo.smartPlaylists()
                val workflowRules = automationEngine.rules()
                val journalEntries = automationEngine.journalEntries(20)
                val personalizationEnabled = recommendationEngine.isPersonalizationEnabled()
                val positions = personalRepository.allPositions()
                val recommendations = recommendationEngine.generateRecommendations(allMediaList, history, positions)
                val listeningInsights = insightsCalculator.calculateListeningInsights(allMediaList, history)
                val watchingInsights = insightsCalculator.calculateWatchingInsights(allMediaList, history, positions)
                val healthReport = automationEngine.generateHealthReport(
                    allMediaList,
                    cacheSizeBytes = mediaCache.calculateCacheSizeBytes(),
                )

                roots.forEach(::observeScan)
                GenerationCache(
                    generation = currentGen,
                    roots = roots,
                    playlists = playlists,
                    favoriteIdSet = favoriteIdSet,
                    favorites = favorites,
                    personalMedia = personalMedia,
                    history = history,
                    themeMode = themeMode,
                    libraryViewMode = libraryViewMode,
                    defaultSort = defaultSort,
                    reduceAnimations = reduceAnimations,
                    highContrast = highContrast,
                    reduceTransparency = reduceTransparency,
                    resumePlayback = resumePlayback,
                    defaultSpeed = defaultSpeed,
                    repeatBehavior = repeatBehavior,
                    shuffleBehavior = shuffleBehavior,
                    smartPlaylists = smartPlaylists,
                    workflowRules = workflowRules,
                    journalEntries = journalEntries,
                    personalizationEnabled = personalizationEnabled,
                    recommendations = recommendations,
                    listeningInsights = listeningInsights,
                    watchingInsights = watchingInsights,
                    healthReport = healthReport,
                ).also { generationCache = it }
            }

            val favoriteIdSet = cache.favoriteIdSet
            mutableState.value = mutableState.value.copy(
                roots = cache.roots,
                audio = repository.media(MediaKind.AUDIO, query).map { it.copy(isFavorite = it.id.value in favoriteIdSet) },
                video = repository.media(MediaKind.VIDEO, query).map { it.copy(isFavorite = it.id.value in favoriteIdSet) },
                albums = repository.albums(query),
                artists = repository.artists(query),
                playlists = cache.playlists,
                smartPlaylists = cache.smartPlaylists,
                duplicateGroups = mutableState.value.duplicateGroups,
                recommendations = cache.recommendations,
                listeningInsights = cache.listeningInsights,
                watchingInsights = cache.watchingInsights,
                workflowRules = cache.workflowRules,
                journalEntries = cache.journalEntries,
                healthReport = cache.healthReport,
                personalizationEnabled = cache.personalizationEnabled,
                personalMedia = cache.personalMedia,
                favorites = cache.favorites,
                history = cache.history.mapNotNull { entry -> cache.personalMedia[entry.mediaId]?.let { DesktopHistoryItem(it, entry) } },
                bookmarks = personalRepository.allBookmarks(),
                themeMode = cache.themeMode,
                libraryViewMode = cache.libraryViewMode,
                defaultSort = cache.defaultSort,
                reduceAnimations = cache.reduceAnimations,
                highContrast = cache.highContrast,
                reduceTransparency = cache.reduceTransparency,
                resumePlayback = cache.resumePlayback,
                defaultSpeed = cache.defaultSpeed,
                repeatBehavior = cache.repeatBehavior,
                shuffleBehavior = cache.shuffleBehavior,
                loading = false,
                error = null,
            )
        }.onFailure(::report)
    }

    fun toggleMediaSelection(mediaId: MediaId) {
        val current = mutableState.value.selectedMediaIds
        mutableState.value = mutableState.value.copy(
            selectedMediaIds = if (mediaId in current) current - mediaId else current + mediaId,
        )
    }

    fun clearSelection() {
        mutableState.value = mutableState.value.copy(selectedMediaIds = emptySet())
    }

    fun selectAll(mediaList: List<MediaItem>) {
        mutableState.value = mutableState.value.copy(selectedMediaIds = mediaList.map(MediaItem::id).toSet())
    }

    fun createSmartPlaylist(name: String, conditions: List<SmartRuleCondition>) =
        mutatePersonalData { smartPlaylistRepo.createSmartPlaylist(name, conditions) }

    fun deleteSmartPlaylist(id: String) =
        mutatePersonalData { smartPlaylistRepo.deleteSmartPlaylist(id) }

    fun evaluateSmartPlaylist(definition: SmartPlaylistDefinition): List<MediaItem> =
        smartPlaylistRepo.evaluate(definition, mutableState.value.audio + mutableState.value.video)

    fun scanDuplicates() = scope.launch(Dispatchers.IO) {
        runCatching {
            val duplicates = duplicateService.findDuplicates(mutableState.value.audio + mutableState.value.video)
            mutableState.value = mutableState.value.copy(duplicateGroups = duplicates)
        }.onFailure(::report)
    }

    fun dismissRecommendation(targetType: String, targetId: String) =
        mutatePersonalData { recommendationEngine.dismissRecommendation(targetType, targetId) }

    fun clearDismissedRecommendations() =
        mutatePersonalData { recommendationEngine.clearDismissed() }

    fun setPersonalizationEnabled(enabled: Boolean) = mutatePersonalData {
        recommendationEngine.setPersonalizationEnabled(enabled)
    }

    fun createWorkflowRule(name: String, trigger: AutomationTrigger, action: AutomationActionType) =
        mutatePersonalData { automationEngine.createRule(name, trigger, action) }

    fun deleteWorkflowRule(id: String) =
        mutatePersonalData { automationEngine.deleteRule(id) }

    fun dryRunRule(rule: WorkflowRule): DryRunResult =
        automationEngine.dryRun(rule, mutableState.value.audio + mutableState.value.video)

    fun saveMetadataOverride(
        mediaId: MediaId,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        albumArtist: String? = null,
        genre: String? = null,
        releaseYear: Int? = null,
        trackNumber: Int? = null,
        discNumber: Int? = null,
    ) = mutatePersonalData {
        metadataEditor.saveOverride(mediaId, title, artist, album, albumArtist, genre, releaseYear, trackNumber, discNumber)
        repository.invalidate()
    }

    fun clearMetadataOverride(mediaId: MediaId) = mutatePersonalData {
        metadataEditor.clearOverride(mediaId)
        repository.invalidate()
    }

    fun saveArtworkOverride(mediaId: MediaId, artworkUri: String) = mutatePersonalData {
        metadataEditor.saveArtworkOverride(mediaId, artworkUri)
        repository.invalidate()
    }

    fun clearArtworkOverride(mediaId: MediaId) = mutatePersonalData {
        metadataEditor.clearArtworkOverride(mediaId)
        repository.invalidate()
    }

    fun applyEmbeddedTagWrite(file: File, changeset: MetadataChangeset, backupDir: Path? = null): Result<Unit> {
        val res = metadataEditor.applyEmbeddedTagWrite(file, changeset, backupDir)
        if (res.isSuccess) repository.invalidate()
        return res
    }

    fun applyEmbeddedArtworkWrite(file: File, artworkFile: File, backupDir: Path? = null): Result<Unit> {
        val res = metadataEditor.applyEmbeddedArtworkWrite(file, artworkFile, backupDir)
        if (res.isSuccess) repository.invalidate()
        return res
    }

    fun evaluateRename(items: List<MediaItem>, template: String): List<RenamePlanItem> =
        renameOrganizer.evaluateRename(items, template)

    fun executeRename(plans: List<RenamePlanItem>): RenameExecutionResult {
        val res = renameOrganizer.executeRename(plans)
        repository.invalidate()
        return res
    }

    fun evaluateOrganize(items: List<MediaItem>, targetRoot: Path, template: String): List<OrganizePlanItem> =
        renameOrganizer.evaluateOrganize(items, targetRoot, template)

    fun executeOrganize(plans: List<OrganizePlanItem>, isMove: Boolean): OrganizeExecutionResult {
        val res = renameOrganizer.executeOrganize(plans, isMove)
        repository.invalidate()
        return res
    }

    fun findMissingMedia(): List<MediaItem> =
        missingMediaRelinker.findMissingMedia(mutableState.value.audio + mutableState.value.video)

    fun suggestRelinkCandidates(missing: MediaItem, searchRoots: List<File>): List<MissingMediaCandidate> =
        missingMediaRelinker.findCandidates(missing, searchRoots)

    fun relinkMedia(mediaId: MediaId, newFile: File): Result<Unit> {
        val res = missingMediaRelinker.relink(mediaId, newFile)
        if (res.isSuccess) repository.invalidate()
        return res
    }

    fun exportMetadataCsv(items: List<MediaItem>, targetFile: File, includePaths: Boolean = false): Result<Unit> =
        csvExporterImporter.exportCsv(items, targetFile, includePaths)

    fun previewImportCsv(csvFile: File, existingItems: List<MediaItem>): Result<CsvImportPlan> =
        csvExporterImporter.previewImportCsv(csvFile, existingItems)

    fun applyImportCsv(
        plan: CsvImportPlan,
        mode: MetadataWriteMode,
        backupDir: Path? = null,
    ): CsvImportExecutionResult {
        val res = csvExporterImporter.applyImport(plan, mode, backupDir)
        repository.invalidate()
        return res
    }

    private fun observeScan(root: LibraryRoot) {
        if (scanObservers[root.id]?.isActive == true) return
        scanObservers[root.id] = scope.launch {
            coordinator.state(root.id).collectLatest { scanState ->
                mutableState.value = mutableState.value.copy(
                    scanStates = mutableState.value.scanStates + (root.id to scanState),
                )
            }
        }
    }

    private fun report(error: Throwable) {
        mutableState.value = mutableState.value.copy(loading = false, error = error.message ?: "Library operation failed")
    }

    private fun mutatePersonalData(block: () -> Unit) = scope.launch {
        withContext(Dispatchers.IO) { runCatching(block).onFailure(::report) }
            .onSuccess {
                generationCache = null
                refresh()
            }
    }

    override fun close() {
        scope.cancel()
        database.close()
    }

    companion object {
        private const val PLAYBACK_SAVE_INTERVAL_MILLIS = 10_000L

        internal fun resumePositionMillis(media: MediaItem, saved: ResumePosition?): Long {
            if (saved == null || saved.completed || saved.positionMillis < 10_000) return 0
            val duration = saved.durationMillis ?: media.durationMillis
            if (duration != null && (saved.positionMillis >= duration - 15_000 || saved.positionMillis * 100 >= duration * 95)) return 0
            val isLongAudio = media.kind == MediaKind.AUDIO && (duration ?: 0) >= 20 * 60_000
            return if (media.kind == MediaKind.VIDEO || isLongAudio) saved.positionMillis else 0
        }
    }
}
