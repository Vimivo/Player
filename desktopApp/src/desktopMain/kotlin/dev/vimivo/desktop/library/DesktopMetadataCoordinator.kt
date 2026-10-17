@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "Wrapping")

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaMetadataExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Runs independent local inspections with bounded concurrency after a successful scan. */
internal class DesktopMetadataCoordinator(
    private val repository: DesktopMetadataRepository,
    private val extractor: MediaMetadataExtractor = DesktopMediaMetadataExtractor(),
    private val scope: CoroutineScope,
    private val onMetadataUpdated: () -> Unit = {},
) {
    private val jobs = mutableMapOf<String, Job>()

    fun start(rootId: String, onBatchComplete: () -> Unit = {}) {
        jobs[rootId]?.cancel()
        jobs[rootId] = scope.launch(Dispatchers.IO) {
            val permits = Semaphore(MAX_CONCURRENT_EXTRACTIONS)
            while (isActive) {
                val candidates = repository.candidates(rootId)
                if (candidates.isEmpty()) break
                coroutineScope {
                    candidates.map { media ->
                        async {
                            permits.withPermit {
                                if (!isActive) return@withPermit
                                extractor.extract(media).onSuccess { repository.save(media, it) }
                                    .onFailure {
                                        repository.save(
                                            media,
                                            dev.vimivo.core.model.ExtractedMediaMetadata(),
                                            it.message ?: "Unsupported metadata",
                                        )
                                    }
                            }
                        }
                    }.awaitAll()
                }
                onMetadataUpdated()
                onBatchComplete()
            }
        }
    }

    fun cancel(rootId: String) {
        jobs.remove(rootId)?.cancel()
    }

    private companion object {
        const val MAX_CONCURRENT_EXTRACTIONS = 2
    }
}
