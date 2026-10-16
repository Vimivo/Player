@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "LongMethod",
    "MaximumLineLength", "MaxLineLength",
)

package dev.vimivo.core.feedback

import dev.vimivo.core.diagnostics.DiagnosticReport
import dev.vimivo.core.util.SafeLogger

object FeedbackReportFormatter {

    fun formatMarkdown(
        draft: FeedbackDraft,
        diagnosticReport: DiagnosticReport? = null,
    ): String {
        val sb = StringBuilder()
        val title = SafeLogger.sanitize(draft.title.trim())
        val description = SafeLogger.sanitize(draft.description.trim())

        sb.appendLine("# [${draft.type.displayName}]: $title")
        sb.appendLine()
        sb.appendLine("## Metadata")
        sb.appendLine("- **Report Type**: `${draft.type.displayName}`")
        sb.appendLine("- **Category**: `${draft.category.displayName}`")
        sb.appendLine("- **Application Version**: `${draft.appVersion}`")
        if (draft.platform.isNotBlank()) sb.appendLine("- **Platform Target**: `${draft.platform}`")
        if (draft.osVersion.isNotBlank()) sb.appendLine("- **Operating System**: `${draft.osVersion}`")
        if (draft.architecture.isNotBlank()) sb.appendLine("- **Architecture**: `${draft.architecture}`")
        sb.appendLine("- **Frequency**: `${draft.frequency.displayName}`")
        sb.appendLine()

        when (draft.type) {
            FeedbackType.BUG_REPORT, FeedbackType.ACCESSIBILITY_ISSUE -> {
                sb.appendLine("## Problem Description")
                sb.appendLine(description)
                sb.appendLine()

                if (draft.stepsToReproduce.isNotBlank()) {
                    sb.appendLine("## Steps to Reproduce")
                    sb.appendLine(SafeLogger.sanitize(draft.stepsToReproduce.trim()))
                    sb.appendLine()
                }

                if (draft.expectedBehavior.isNotBlank()) {
                    sb.appendLine("## Expected Behavior")
                    sb.appendLine(SafeLogger.sanitize(draft.expectedBehavior.trim()))
                    sb.appendLine()
                }

                if (draft.actualBehavior.isNotBlank()) {
                    sb.appendLine("## Actual Behavior")
                    sb.appendLine(SafeLogger.sanitize(draft.actualBehavior.trim()))
                    sb.appendLine()
                }
            }
            FeedbackType.FEATURE_REQUEST -> {
                sb.appendLine("## User Problem & Goal")
                sb.appendLine(description)
                sb.appendLine()

                if (draft.proposedSolution.isNotBlank()) {
                    sb.appendLine("## Proposed Solution")
                    sb.appendLine(SafeLogger.sanitize(draft.proposedSolution.trim()))
                    sb.appendLine()
                }

                if (draft.workaround.isNotBlank()) {
                    sb.appendLine("## Current Workaround")
                    sb.appendLine(SafeLogger.sanitize(draft.workaround.trim()))
                    sb.appendLine()
                }
            }
            FeedbackType.PLAYBACK_ISSUE -> {
                sb.appendLine("## Playback Failure Description")
                sb.appendLine(description)
                sb.appendLine()

                val mc = draft.mediaContext
                if (mc != null) {
                    sb.appendLine("## Media & Decoder Details")
                    if (mc.container != null) sb.appendLine("- **Container**: `${mc.container}`")
                    if (mc.audioCodec != null) sb.appendLine("- **Audio Codec**: `${mc.audioCodec}`")
                    if (mc.videoCodec != null) sb.appendLine("- **Video Codec**: `${mc.videoCodec}`")
                    if (mc.resolution != null) sb.appendLine("- **Resolution**: `${mc.resolution}`")
                    if (mc.frameRate != null) sb.appendLine("- **Frame Rate**: `${mc.frameRate}`")
                    if (mc.subtitleFormat != null) sb.appendLine("- **Subtitles**: `${mc.subtitleFormat}`")
                    if (mc.hardwareAcceleration != null) sb.appendLine("- **Hardware Decoding**: `${mc.hardwareAcceleration}`")
                    if (mc.playbackEngine != null) sb.appendLine("- **Playback Engine**: `${mc.playbackEngine}`")
                    if (mc.errorMessage != null) sb.appendLine("- **Error Code**: `${SafeLogger.sanitize(mc.errorMessage)}`")
                    sb.appendLine()
                }

                if (draft.stepsToReproduce.isNotBlank()) {
                    sb.appendLine("## Steps to Reproduce")
                    sb.appendLine(SafeLogger.sanitize(draft.stepsToReproduce.trim()))
                    sb.appendLine()
                }
            }
        }

        if (draft.includeDiagnostics && diagnosticReport != null) {
            sb.appendLine("## Diagnostics Excerpt (Privacy-Safe)")
            sb.appendLine("- **Playback Engine**: `${diagnosticReport.playbackBackend}`")
            sb.appendLine("- **Build Commit**: `${diagnosticReport.buildCommit}`")
            if (diagnosticReport.errorSummaries.isNotEmpty()) {
                sb.appendLine("- **Recent Errors** (${diagnosticReport.errorSummaries.size}):")
                diagnosticReport.errorSummaries.take(3).forEach { err ->
                    sb.appendLine("  - `[${err.category}]` ${err.exceptionType}: ${SafeLogger.sanitize(err.message)}")
                }
            }
            sb.appendLine()
        }

        sb.appendLine("---")
        sb.appendLine("> *Note: This feedback report was prepared locally by Vimivo.*")
        sb.appendLine("> *Zero credentials, passwords, private keys, or personal media files are included.*")

        return sb.toString().trimEnd()
    }

    fun generateGitHubIssueUrl(
        draft: FeedbackDraft,
        diagnosticReport: DiagnosticReport? = null,
        repoUrl: String = "https://github.com/Vimivo/Player",
    ): String {
        val template = when (draft.type) {
            FeedbackType.BUG_REPORT -> "bug_report.yml"
            FeedbackType.FEATURE_REQUEST -> "feature_request.yml"
            FeedbackType.PLAYBACK_ISSUE -> "playback_issue.yml"
            FeedbackType.ACCESSIBILITY_ISSUE -> "accessibility_issue.yml"
        }

        val prefix = when (draft.type) {
            FeedbackType.BUG_REPORT -> "[Bug]: "
            FeedbackType.FEATURE_REQUEST -> "[Feature]: "
            FeedbackType.PLAYBACK_ISSUE -> "[Playback]: "
            FeedbackType.ACCESSIBILITY_ISSUE -> "[A11y]: "
        }

        val fullTitle = prefix + draft.title.trim()
        val markdownBody = formatMarkdown(draft, diagnosticReport)

        val encodedTitle = urlEncode(fullTitle)
        val encodedBody = urlEncode(markdownBody)
        val encodedTemplate = urlEncode(template)

        return "$repoUrl/issues/new?template=$encodedTemplate&title=$encodedTitle&body=$encodedBody"
    }

    fun urlEncode(value: String): String {
        val allowed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~"
        val sb = StringBuilder()
        val bytes = value.encodeToByteArray()
        for (b in bytes) {
            val ch = (b.toInt() and 0xFF).toChar()
            if (ch in allowed) {
                sb.append(ch)
            } else {
                val hex = (b.toInt() and 0xFF).toString(16).uppercase()
                sb.append('%')
                if (hex.length == 1) sb.append('0')
                sb.append(hex)
            }
        }
        return sb.toString()
    }
}
