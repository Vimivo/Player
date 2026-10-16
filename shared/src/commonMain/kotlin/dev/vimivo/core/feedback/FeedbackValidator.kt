@file:Suppress(
    "ArgumentListWrapping", "MaximumLineLength", "MaxLineLength",
)

package dev.vimivo.core.feedback

sealed class FeedbackValidationResult {
    object Valid : FeedbackValidationResult()
    data class Invalid(val errors: List<String>) : FeedbackValidationResult() {
        val errorMessage: String get() = errors.joinToString("; ")
    }

    val isValid: Boolean get() = this is Valid
}

object FeedbackValidator {
    const val MIN_TITLE_LENGTH = 5
    const val MAX_TITLE_LENGTH = 120
    const val MIN_DESCRIPTION_LENGTH = 10
    const val MAX_DESCRIPTION_LENGTH = 5000
    const val MIN_STEPS_LENGTH = 10

    fun validate(draft: FeedbackDraft): FeedbackValidationResult {
        val errors = mutableListOf<String>()

        val trimmedTitle = draft.title.trim()
        if (trimmedTitle.length < MIN_TITLE_LENGTH) {
            errors += "Title must be at least $MIN_TITLE_LENGTH characters (currently ${trimmedTitle.length})"
        } else if (trimmedTitle.length > MAX_TITLE_LENGTH) {
            errors += "Title cannot exceed $MAX_TITLE_LENGTH characters (currently ${trimmedTitle.length})"
        }

        val trimmedDesc = draft.description.trim()
        if (trimmedDesc.length < MIN_DESCRIPTION_LENGTH) {
            errors += "Description must be at least $MIN_DESCRIPTION_LENGTH characters"
        } else if (trimmedDesc.length > MAX_DESCRIPTION_LENGTH) {
            errors += "Description cannot exceed $MAX_DESCRIPTION_LENGTH characters"
        }

        when (draft.type) {
            FeedbackType.BUG_REPORT,
            FeedbackType.PLAYBACK_ISSUE,
            FeedbackType.ACCESSIBILITY_ISSUE -> {
                val hasSteps = draft.stepsToReproduce.trim().length >= MIN_STEPS_LENGTH
                val hasActual = draft.actualBehavior.trim().length >= MIN_STEPS_LENGTH
                if (!hasSteps && !hasActual) {
                    errors += "Please provide reproduction steps or describe what actually happened (at least $MIN_STEPS_LENGTH characters)"
                }
            }
            FeedbackType.FEATURE_REQUEST -> {
                // Title and description already validated
            }
        }

        return if (errors.isEmpty()) FeedbackValidationResult.Valid else FeedbackValidationResult.Invalid(errors)
    }
}
