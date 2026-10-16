@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "TooGenericExceptionCaught")

package dev.vimivo.desktop.feedback

import dev.vimivo.core.diagnostics.DiagnosticReport
import dev.vimivo.core.feedback.FeedbackDraft
import dev.vimivo.core.feedback.FeedbackDraftRepository
import dev.vimivo.core.feedback.FeedbackReportFormatter
import dev.vimivo.desktop.platform.DesktopDirectories
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DesktopFeedbackDraftRepository(
    private val draftsDirectory: Path = defaultDraftsDirectory(),
) : FeedbackDraftRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    override suspend fun saveDraft(draft: FeedbackDraft): Result<Unit> = runCatching {
        Files.createDirectories(draftsDirectory)
        val file = draftsDirectory.resolve("${sanitizeId(draft.id)}.json")
        val content = json.encodeToString(FeedbackDraft.serializer(), draft)
        file.writeText(content)
    }

    override suspend fun getDraft(id: String): FeedbackDraft? = runCatching {
        val file = draftsDirectory.resolve("${sanitizeId(id)}.json")
        if (!file.exists()) return null
        json.decodeFromString(FeedbackDraft.serializer(), file.readText())
    }.getOrNull()

    override suspend fun listDrafts(): List<FeedbackDraft> = runCatching {
        if (!draftsDirectory.exists()) return emptyList()
        Files.list(draftsDirectory).use { stream ->
            stream.filter { it.toString().endsWith(".json") }
                .map { path ->
                    runCatching {
                        json.decodeFromString(FeedbackDraft.serializer(), path.readText())
                    }.getOrNull()
                }
                .filter { it != null }
                .map { it!! }
                .toList()
                .sortedByDescending { it.updatedAtEpochMs }
        }
    }.getOrDefault(emptyList())

    override suspend fun deleteDraft(id: String): Result<Unit> = runCatching {
        val file = draftsDirectory.resolve("${sanitizeId(id)}.json")
        file.deleteIfExists()
        Unit
    }

    override suspend fun clearDrafts(): Result<Unit> = runCatching {
        if (draftsDirectory.exists()) {
            Files.list(draftsDirectory).use { stream ->
                stream.filter { it.toString().endsWith(".json") }
                    .forEach { it.deleteIfExists() }
            }
        }
    }

    fun exportDraftToFile(
        draft: FeedbackDraft,
        destination: Path,
        diagnosticReport: DiagnosticReport? = null,
    ): Result<Path> = runCatching {
        destination.parent?.let { Files.createDirectories(it) }
        val markdown = FeedbackReportFormatter.formatMarkdown(draft, diagnosticReport)
        destination.writeText(markdown)
        destination
    }

    fun openGitHubInBrowser(url: String): Result<Unit> = runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI.create(url))
        } else {
            error("System browser launch is not supported in the current environment")
        }
    }

    private fun sanitizeId(id: String): String {
        return id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }

    companion object {
        fun defaultDraftsDirectory(): Path {
            return DesktopDirectories.resolve().data.resolve("feedback_drafts")
        }
    }
}
