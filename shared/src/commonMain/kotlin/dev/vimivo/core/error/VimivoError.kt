@file:Suppress("MaximumLineLength", "MaxLineLength")

package dev.vimivo.core.error

import dev.vimivo.core.util.SafeLogger

/**
 * Standardized error categories defined in Milestone 31 for cross-platform reliability.
 */
enum class VimivoErrorCategory {
    APPLICATION_STARTUP,
    MEDIA_NOT_FOUND,
    MEDIA_UNSUPPORTED,
    MEDIA_CORRUPT,
    PLAYBACK_INITIALIZATION,
    AUDIO_OUTPUT,
    VIDEO_RENDERING,
    PLAYBACK_INTERRUPTED,
    DATABASE,
    DATABASE_MIGRATION,
    QUEUE_PERSISTENCE,
    FILE_PERMISSION,
    NETWORK_TIMEOUT,
    NETWORK_AUTHENTICATION,
    NATIVE_DEPENDENCY,
    UPDATE_CHECK,
    UPDATE_VERIFICATION,
    UPDATE_INSTALLATION,
    BACKUP_RESTORE,
    UNKNOWN,
}

/**
 * Platform-neutral structured error representation.
 * Sanitizes technical diagnostics and paths to prevent leaking credentials or personal user paths.
 */
data class VimivoError(
    val category: VimivoErrorCategory,
    val code: String,
    val component: String,
    val isRecoverable: Boolean,
    val userMessage: String,
    val technicalDetail: String? = null,
    override val cause: Throwable? = null,
) : Throwable(userMessage, cause) {

    val sanitizedTechnicalDetail: String? = technicalDetail?.let { SafeLogger.sanitize(it) }

    fun toDiagnosticString(): String {
        val detailPart = sanitizedTechnicalDetail?.let { " | Detail: $it" } ?: ""
        return "[$code] ($category) in $component: $userMessage (recoverable=$isRecoverable)$detailPart"
    }

    companion object {
        fun mediaNotFound(path: String, component: String = "MediaLibrary"): VimivoError {
            val safePath = SafeLogger.redactPath(path)
            return VimivoError(
                category = VimivoErrorCategory.MEDIA_NOT_FOUND,
                code = "ERR_MEDIA_NOT_FOUND",
                component = component,
                isRecoverable = true,
                userMessage = "Media file could not be found or has been moved.",
                technicalDetail = "Path: $safePath",
            )
        }

        fun mediaUnsupported(format: String, component: String = "PlaybackEngine"): VimivoError {
            return VimivoError(
                category = VimivoErrorCategory.MEDIA_UNSUPPORTED,
                code = "ERR_MEDIA_UNSUPPORTED",
                component = component,
                isRecoverable = true,
                userMessage = "Unsupported audio or video codec format ($format).",
                technicalDetail = "Format: $format",
            )
        }

        fun mediaCorrupt(path: String, reason: String, component: String = "PlaybackEngine"): VimivoError {
            val safePath = SafeLogger.redactPath(path)
            return VimivoError(
                category = VimivoErrorCategory.MEDIA_CORRUPT,
                code = "ERR_MEDIA_CORRUPT",
                component = component,
                isRecoverable = true,
                userMessage = "Media file appears corrupted or unreadable.",
                technicalDetail = "Path: $safePath, Reason: ${SafeLogger.sanitize(reason)}",
            )
        }

        fun playbackInitializationFailed(
            reason: String,
            cause: Throwable? = null,
            component: String = "PlaybackEngine",
        ): VimivoError {
            return VimivoError(
                category = VimivoErrorCategory.PLAYBACK_INITIALIZATION,
                code = "ERR_PLAYBACK_INIT_FAILED",
                component = component,
                isRecoverable = true,
                userMessage = "Unable to initialize playback engine.",
                technicalDetail = SafeLogger.sanitize(reason),
                cause = cause,
            )
        }

        fun nativeDependencyMissing(
            libraryName: String,
            component: String = "NativeAdapter",
        ): VimivoError {
            return VimivoError(
                category = VimivoErrorCategory.NATIVE_DEPENDENCY,
                code = "ERR_NATIVE_DEP_MISSING",
                component = component,
                isRecoverable = false,
                userMessage = "Required native multimedia library ($libraryName) is missing.",
                technicalDetail = "Missing dependency: $libraryName",
            )
        }

        fun databaseMigrationFailed(
            fromVersion: Int,
            toVersion: Int,
            cause: Throwable? = null,
            component: String = "Database",
        ): VimivoError {
            return VimivoError(
                category = VimivoErrorCategory.DATABASE_MIGRATION,
                code = "ERR_DB_MIGRATION_FAILED",
                component = component,
                isRecoverable = false,
                userMessage = "Database upgrade failed. Your media records have been preserved safely.",
                technicalDetail = "Migration failed from schema v$fromVersion to v$toVersion: ${cause?.message}",
                cause = cause,
            )
        }

        fun filePermissionDenied(path: String, component: String = "FileSystem"): VimivoError {
            val safePath = SafeLogger.redactPath(path)
            return VimivoError(
                category = VimivoErrorCategory.FILE_PERMISSION,
                code = "ERR_FILE_PERMISSION_DENIED",
                component = component,
                isRecoverable = true,
                userMessage = "Permission denied while accessing media file or directory.",
                technicalDetail = "Path: $safePath",
            )
        }

        fun networkTimeout(url: String, component: String = "NetworkClient"): VimivoError {
            val safeUrl = SafeLogger.redactUrl(url)
            return VimivoError(
                category = VimivoErrorCategory.NETWORK_TIMEOUT,
                code = "ERR_NETWORK_TIMEOUT",
                component = component,
                isRecoverable = true,
                userMessage = "Network connection timed out.",
                technicalDetail = "Target URL: $safeUrl",
            )
        }

        fun networkAuthFailed(url: String, component: String = "NetworkClient"): VimivoError {
            val safeUrl = SafeLogger.redactUrl(url)
            return VimivoError(
                category = VimivoErrorCategory.NETWORK_AUTHENTICATION,
                code = "ERR_NETWORK_AUTH_FAILED",
                component = component,
                isRecoverable = true,
                userMessage = "Authentication failed for network stream or server.",
                technicalDetail = "Target URL: $safeUrl",
            )
        }

        fun updateVerificationFailed(reason: String, component: String = "UpdateChecker"): VimivoError {
            return VimivoError(
                category = VimivoErrorCategory.UPDATE_VERIFICATION,
                code = "ERR_UPDATE_VERIFY_FAILED",
                component = component,
                isRecoverable = true,
                userMessage = "Software update verification failed. Update was safely aborted.",
                technicalDetail = SafeLogger.sanitize(reason),
            )
        }
    }
}
