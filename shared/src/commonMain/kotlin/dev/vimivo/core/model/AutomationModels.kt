package dev.vimivo.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AutomationTrigger {
    MANUAL,
    AFTER_SCAN,
    ON_METADATA_CHANGE,
    SCHEDULED_MAINTENANCE,
}

@Serializable
enum class AutomationActionType {
    REFRESH_SMART_COLLECTIONS,
    IDENTIFY_MISSING_MEDIA,
    IDENTIFY_DUPLICATES,
    CLEAN_METADATA_WHITESPACE,
    NORMALIZE_ALBUM_ARTISTS,
    HEALTH_CHECK,
}

@Serializable
data class WorkflowRule(
    val id: String,
    val name: String,
    val trigger: AutomationTrigger,
    val action: AutomationActionType,
    val enabled: Boolean = true,
    val lastRunEpochMillis: Long? = null,
    val lastStatus: String? = null,
)

enum class OperationStage {
    PLANNED,
    PREFLIGHTED,
    AWAITING_CONFIRMATION,
    RUNNING,
    COMMITTED,
    VERIFIED,
    FAILED,
    CANCELLED,
    ROLLED_BACK,
}

data class JournalEntry(
    val jobId: String,
    val operationType: String,
    val stage: OperationStage,
    val affectedCount: Int,
    val details: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

data class LibraryHealthReport(
    val totalMediaCount: Int,
    val availableMediaCount: Int,
    val missingMediaCount: Int,
    val missingMetadataCount: Int,
    val missingArtworkCount: Int,
    val exactDuplicateGroupCount: Int,
    val possibleDuplicateGroupCount: Int,
    val healthScorePercent: Int,
)
