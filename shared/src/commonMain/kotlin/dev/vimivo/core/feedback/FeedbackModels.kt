package dev.vimivo.core.feedback

import kotlinx.serialization.Serializable

@Serializable
enum class FeedbackType(val displayName: String, val description: String) {
    BUG_REPORT("Bug Report", "Report an unexpected error, crash, or broken feature"),
    FEATURE_REQUEST("Feature Request", "Suggest a new feature, capability, or usability improvement"),
    PLAYBACK_ISSUE("Playback / Codec Issue", "Report an audio/video playback failure, codec error, or sync defect"),
    ACCESSIBILITY_ISSUE("Accessibility Issue", "Report a screen reader, keyboard navigation, or contrast barrier"),
}

@Serializable
enum class FeedbackCategory(val displayName: String) {
    PLAYBACK("Playback & Codecs"),
    USER_INTERFACE("User Interface & Theme"),
    LIBRARY_MANAGEMENT("Media Library & Tagging"),
    PLAYLISTS_AND_QUEUE("Playlists & Queue"),
    PERFORMANCE("Performance & Resource Usage"),
    CRASH_OR_FREEZE("Crash, Freeze, or Error"),
    ACCESSIBILITY("Accessibility & Navigation"),
    INSTALLATION_OR_UPDATE("Installation, Update, or Packaging"),
    DATA_INTEGRITY("Database & Data Recovery"),
    OTHER("Other Feedback"),
}

@Serializable
enum class IssueFrequency(val displayName: String) {
    ALWAYS("Every time (100% reproducible)"),
    OFTEN("Often (frequent but not every time)"),
    SOMETIMES("Intermittent (hard to reproduce)"),
    ONCE("Happened once"),
}

@Serializable
data class MediaPlaybackContext(
    val container: String? = null,
    val audioCodec: String? = null,
    val videoCodec: String? = null,
    val resolution: String? = null,
    val frameRate: String? = null,
    val subtitleFormat: String? = null,
    val hardwareAcceleration: String? = null,
    val playbackEngine: String? = null,
    val errorMessage: String? = null,
)

@Serializable
data class FeedbackDraft(
    val id: String,
    val type: FeedbackType = FeedbackType.BUG_REPORT,
    val category: FeedbackCategory = FeedbackCategory.PLAYBACK,
    val title: String = "",
    val description: String = "",
    val stepsToReproduce: String = "",
    val expectedBehavior: String = "",
    val actualBehavior: String = "",
    val proposedSolution: String = "",
    val workaround: String = "",
    val frequency: IssueFrequency = IssueFrequency.ALWAYS,
    val mediaContext: MediaPlaybackContext? = null,
    val includeDiagnostics: Boolean = true,
    val appVersion: String = "1.0.0-rc.1",
    val platform: String = "",
    val osVersion: String = "",
    val architecture: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
)

@Serializable
data class FeedbackReport(
    val draft: FeedbackDraft,
    val sanitizedMarkdown: String,
    val gitHubIssueUrl: String,
    val rawSummary: String,
)
