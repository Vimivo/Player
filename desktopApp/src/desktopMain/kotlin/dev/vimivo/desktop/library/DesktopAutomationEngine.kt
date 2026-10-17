@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "Indentation", "LongMethod",
    "MaximumLineLength", "MaxLineLength", "MultiLineIfElse", "ParameterListWrapping",
    "TooGenericExceptionCaught", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.AutomationActionType
import dev.vimivo.core.model.AutomationTrigger
import dev.vimivo.core.model.JournalEntry
import dev.vimivo.core.model.LibraryHealthReport
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.OperationStage
import dev.vimivo.core.model.WorkflowRule
import java.io.File
import java.net.URI
import java.util.UUID

internal data class DryRunResult(
    val ruleId: String,
    val ruleName: String,
    val plannedAction: AutomationActionType,
    val estimatedAffectedItems: Int,
    val summary: String,
)

internal class DesktopAutomationEngine(private val database: DesktopDatabase) {

    fun createRule(
        name: String,
        trigger: AutomationTrigger,
        action: AutomationActionType,
    ): WorkflowRule {
        val id = UUID.randomUUID().toString()
        database.transaction { connection ->
            connection.prepareStatement(
                "INSERT INTO workflow_rules(id, name, trigger_type, action_type, enabled) VALUES (?, ?, ?, ?, 1)",
            ).use { statement ->
                statement.setString(1, id)
                statement.setString(2, name.trim())
                statement.setString(3, trigger.name)
                statement.setString(4, action.name)
                statement.executeUpdate()
            }
        }
        return WorkflowRule(id, name.trim(), trigger, action, true)
    }

    fun rules(): List<WorkflowRule> = database.transaction { connection ->
        connection.createStatement().executeQuery(
            "SELECT id, name, trigger_type, action_type, enabled, last_run_ms, last_status FROM workflow_rules ORDER BY name COLLATE NOCASE",
        ).use { rows ->
            buildList {
                while (rows.next()) {
                    add(
                        WorkflowRule(
                            id = rows.getString(1),
                            name = rows.getString(2),
                            trigger = AutomationTrigger.valueOf(rows.getString(3)),
                            action = AutomationActionType.valueOf(rows.getString(4)),
                            enabled = rows.getInt(5) != 0,
                            lastRunEpochMillis = rows.getLong(6).takeIf { !rows.wasNull() },
                            lastStatus = rows.getString(7),
                        ),
                    )
                }
            }
        }
    }

    fun deleteRule(id: String) = database.transaction { connection ->
        connection.prepareStatement("DELETE FROM workflow_rules WHERE id=?").use { statement ->
            statement.setString(1, id)
            statement.executeUpdate()
        }
    }

    fun updateRuleStatus(id: String, status: String, now: Long = System.currentTimeMillis()) =
        database.transaction { connection ->
            connection.prepareStatement("UPDATE workflow_rules SET last_run_ms=?, last_status=? WHERE id=?").use { statement ->
                statement.setLong(1, now)
                statement.setString(2, status)
                statement.setString(3, id)
                statement.executeUpdate()
            }
        }

    fun startJob(operationType: String, details: String, now: Long = System.currentTimeMillis()): String {
        val jobId = UUID.randomUUID().toString()
        database.transaction { connection ->
            connection.prepareStatement(
                "INSERT INTO batch_operation_journal(job_id, operation_type, stage, affected_count, details, created_at_ms, updated_at_ms) " +
                    "VALUES (?, ?, ?, 0, ?, ?, ?)",
            ).use { statement ->
                statement.setString(1, jobId)
                statement.setString(2, operationType)
                statement.setString(3, OperationStage.PLANNED.name)
                statement.setString(4, details)
                statement.setLong(5, now)
                statement.setLong(6, now)
                statement.executeUpdate()
            }
        }
        return jobId
    }

    fun updateJobStage(
        jobId: String,
        stage: OperationStage,
        affectedCount: Int,
        details: String,
        now: Long = System.currentTimeMillis(),
    ) = database.transaction { connection ->
        connection.prepareStatement(
            "UPDATE batch_operation_journal SET stage=?, affected_count=?, details=?, updated_at_ms=? WHERE job_id=?",
        ).use { statement ->
            statement.setString(1, stage.name)
            statement.setInt(2, affectedCount)
            statement.setString(3, details)
            statement.setLong(4, now)
            statement.setString(5, jobId)
            statement.executeUpdate()
        }
    }

    fun journalEntries(limit: Int = 50): List<JournalEntry> = database.transaction { connection ->
        connection.prepareStatement(
            "SELECT job_id, operation_type, stage, affected_count, details, created_at_ms, updated_at_ms " +
                "FROM batch_operation_journal ORDER BY created_at_ms DESC LIMIT ?",
        ).use { statement ->
            statement.setInt(1, limit)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            JournalEntry(
                                jobId = rows.getString(1),
                                operationType = rows.getString(2),
                                stage = OperationStage.valueOf(rows.getString(3)),
                                affectedCount = rows.getInt(4),
                                details = rows.getString(5),
                                createdAtEpochMillis = rows.getLong(6),
                                updatedAtEpochMillis = rows.getLong(7),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun dryRun(rule: WorkflowRule, allMedia: List<MediaItem>): DryRunResult = when (rule.action) {
        AutomationActionType.REFRESH_SMART_COLLECTIONS -> {
            DryRunResult(rule.id, rule.name, rule.action, allMedia.size, "Would evaluate and refresh smart playlists against ${allMedia.size} items.")
        }
        AutomationActionType.IDENTIFY_MISSING_MEDIA -> {
            val missing = allMedia.count {
                val f = runCatching { File(URI(it.uri)) }.getOrNull()
                f == null || !f.exists()
            }
            DryRunResult(rule.id, rule.name, rule.action, missing, "Found $missing media items whose physical files are missing or unreadable.")
        }
        AutomationActionType.IDENTIFY_DUPLICATES -> {
            val bySize = allMedia.filter { (it.sizeBytes ?: 0) > 0 }.groupBy { it.sizeBytes }
            val candidateCount = bySize.values.count { it.size > 1 }
            DryRunResult(rule.id, rule.name, rule.action, candidateCount, "Found $candidateCount size-identical duplicate candidate clusters.")
        }
        AutomationActionType.CLEAN_METADATA_WHITESPACE -> {
            val candidateCount = allMedia.count {
                val titleUntrimmed = it.title != it.title.trim()
                val artistUntrimmed = it.artist != null && it.artist != it.artist!!.trim()
                titleUntrimmed || artistUntrimmed
            }
            DryRunResult(rule.id, rule.name, rule.action, candidateCount, "Found $candidateCount items with leading/trailing whitespace in metadata.")
        }
        AutomationActionType.NORMALIZE_ALBUM_ARTISTS -> {
            val candidateCount = allMedia.count {
                it.albumArtist == null && it.artist != null && !it.album.isNullOrBlank()
            }
            DryRunResult(rule.id, rule.name, rule.action, candidateCount, "Found $candidateCount tracks with album tag but missing album artist.")
        }
        AutomationActionType.HEALTH_CHECK -> {
            DryRunResult(rule.id, rule.name, rule.action, allMedia.size, "Would execute full library health diagnostics across ${allMedia.size} items.")
        }
    }

    fun generateHealthReport(
        allMedia: List<MediaItem>,
        cacheSizeBytes: Long = 0L,
        failedScanCount: Int = 0,
    ): LibraryHealthReport {
        val total = allMedia.size
        var available = 0
        var missingMedia = 0
        var inaccessibleMedia = 0
        var missingMetadata = 0
        var missingArtwork = 0

        allMedia.forEach { item ->
            val file = runCatching { File(URI(item.uri)) }.getOrNull()
            if (file != null && file.exists()) {
                if (file.canRead()) {
                    available++
                } else {
                    inaccessibleMedia++
                }
            } else {
                missingMedia++
            }
            if (item.artist.isNullOrBlank() || item.album.isNullOrBlank()) {
                missingMetadata++
            }
            if (item.artworkUri.isNullOrBlank()) {
                missingArtwork++
            }
        }

        val bySize = allMedia.filter { (it.sizeBytes ?: 0) > 0 }.groupBy { it.sizeBytes }
        val exactDupGroups = bySize.values.count { it.size > 1 }

        val score = if (total == 0) 100 else {
            val availScore = (available * 50) / total
            val metaScore = ((total - missingMetadata).coerceAtLeast(0) * 30) / total
            val artScore = ((total - missingArtwork).coerceAtLeast(0) * 20) / total
            (availScore + metaScore + artScore).coerceIn(0, 100)
        }

        return LibraryHealthReport(
            totalMediaCount = total,
            availableMediaCount = available,
            missingMediaCount = missingMedia,
            inaccessibleMediaCount = inaccessibleMedia,
            missingMetadataCount = missingMetadata,
            missingArtworkCount = missingArtwork,
            exactDuplicateGroupCount = exactDupGroups,
            possibleDuplicateGroupCount = 0,
            cacheSizeBytes = cacheSizeBytes,
            failedScanCount = failedScanCount,
            healthScorePercent = score,
        )
    }
}
