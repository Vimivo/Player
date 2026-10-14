@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "Wrapping")

package dev.vimivo.desktop.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Runs independent local inspections with bounded concurrency after a successful scan. */
internal class DesktopMetadataCoordinator(
    private val repository: DesktopMetadataRepository,
    private val extractor: DesktopMediaMetadataExtractor,
    private val scope: CoroutineScope,
) {
    fun start(rootId: String) {
        scope.async {
            val candidates = repository.candidates(rootId)
            val permits = Semaphore(MAX_CONCURRENT_EXTRACTIONS)
            coroutineScope {
                candidates.map { media ->
                    async {
                        permits.withPermit {
                            extractor.extract(media).onSuccess { repository.save(media, it) }
                                .onFailure { repository.save(media, dev.vimivo.core.model.ExtractedMediaMetadata(), it.message ?: "Unsupported metadata") }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private companion object {
        const val MAX_CONCURRENT_EXTRACTIONS = 2
    }
}
