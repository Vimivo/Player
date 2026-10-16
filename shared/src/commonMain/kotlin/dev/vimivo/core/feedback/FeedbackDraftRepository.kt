package dev.vimivo.core.feedback

interface FeedbackDraftRepository {
    suspend fun saveDraft(draft: FeedbackDraft): Result<Unit>
    suspend fun getDraft(id: String): FeedbackDraft?
    suspend fun listDrafts(): List<FeedbackDraft>
    suspend fun deleteDraft(id: String): Result<Unit>
    suspend fun clearDrafts(): Result<Unit>
}

class InMemoryFeedbackDraftRepository : FeedbackDraftRepository {
    private val drafts = mutableMapOf<String, FeedbackDraft>()

    override suspend fun saveDraft(draft: FeedbackDraft): Result<Unit> {
        drafts[draft.id] = draft
        return Result.success(Unit)
    }

    override suspend fun getDraft(id: String): FeedbackDraft? {
        return drafts[id]
    }

    override suspend fun listDrafts(): List<FeedbackDraft> {
        return drafts.values.sortedByDescending { it.updatedAtEpochMs }
    }

    override suspend fun deleteDraft(id: String): Result<Unit> {
        drafts.remove(id)
        return Result.success(Unit)
    }

    override suspend fun clearDrafts(): Result<Unit> {
        drafts.clear()
        return Result.success(Unit)
    }
}
