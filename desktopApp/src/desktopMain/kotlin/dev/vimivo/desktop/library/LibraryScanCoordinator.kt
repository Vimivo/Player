@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "ParameterListWrapping", "Wrapping")

package dev.vimivo.desktop.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal sealed interface LibraryScanState { data object Idle : LibraryScanState; data class Scanning(val discovered: Int) : LibraryScanState; data class Completed(val result: ScanResult) : LibraryScanState; data class Failed(val message: String) : LibraryScanState }

internal class LibraryScanCoordinator(private val repository: DesktopLibraryRepository, private val scanner: DesktopMediaScanner, private val metadata: DesktopMetadataCoordinator, private val scope: CoroutineScope) {
    private val states = mutableMapOf<String, MutableStateFlow<LibraryScanState>>()
    private val jobs = mutableMapOf<String, Job>()
    fun state(rootId: String): StateFlow<LibraryScanState> = states.getOrPut(rootId) { MutableStateFlow(LibraryScanState.Idle) }
    fun start(rootId: String) {
        if (jobs[rootId]?.isActive == true) return
        val state = states.getOrPut(rootId) { MutableStateFlow(LibraryScanState.Idle) }
        val root = repository.root(rootId) ?: run { state.value = LibraryScanState.Failed("Library folder no longer exists"); return }
        jobs[rootId] = scope.launch(Dispatchers.IO) { scanner.scan(rootId, root.path) { state.value = LibraryScanState.Scanning(it) }.onSuccess { state.value = LibraryScanState.Completed(it); repository.invalidate(); metadata.start(rootId) }.onFailure { state.value = LibraryScanState.Failed(it.message ?: "Scan failed") } }
    }
    fun cancel(rootId: String) { jobs.remove(rootId)?.cancel(); states[rootId]?.value = LibraryScanState.Idle }
}
