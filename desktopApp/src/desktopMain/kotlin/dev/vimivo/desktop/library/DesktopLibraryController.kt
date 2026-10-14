@file:Suppress("MaximumLineLength", "MaxLineLength", "TooGenericExceptionCaught")

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

internal data class DesktopLibraryUiState(
    val roots: List<LibraryRoot> = emptyList(),
    val audio: List<MediaItem> = emptyList(),
    val video: List<MediaItem> = emptyList(),
    val scanStates: Map<String, LibraryScanState> = emptyMap(),
    val search: String = "",
    val loading: Boolean = true,
    val error: String? = null,
)

internal class DesktopLibraryController(
    private val database: DesktopDatabase = DesktopDatabase.open(),
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository = DesktopLibraryRepository(database)
    private val coordinator = LibraryScanCoordinator(
        repository,
        DesktopMediaScanner(database),
        DesktopMetadataCoordinator(DesktopMetadataRepository(database), DesktopMediaMetadataExtractor(), scope),
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

    suspend fun preparePlayback(media: MediaItem): Result<MediaItem> = withContext(Dispatchers.IO) {
        runCatching {
            require(media.uri.startsWith("file:")) { "Only indexed local files can be opened from the library" }
            require(File(URI(media.uri)).isFile) { "The indexed media file is unavailable" }
            media
        }.onFailure(::report)
    }

    private suspend fun refresh() = withContext(Dispatchers.IO) {
        runCatching {
            val query = mutableState.value.search.trim()
            val roots = repository.roots()
            roots.forEach(::observeScan)
            mutableState.value = mutableState.value.copy(
                roots = roots,
                audio = repository.media(MediaKind.AUDIO, query),
                video = repository.media(MediaKind.VIDEO, query),
                loading = false,
                error = null,
            )
        }.onFailure(::report)
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

    override fun close() {
        scope.cancel()
        database.close()
    }
}
