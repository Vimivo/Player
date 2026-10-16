package dev.vimivo.desktop.feedback

import dev.vimivo.core.feedback.FeedbackCategory
import dev.vimivo.core.feedback.FeedbackDraft
import dev.vimivo.core.feedback.FeedbackType
import dev.vimivo.core.feedback.IssueFrequency
import dev.vimivo.core.feedback.MediaPlaybackContext
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopFeedbackDraftRepositoryTest {

    @Test
    fun testSaveAndRetrieveDraft() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-drafts-test")
        val repo = DesktopFeedbackDraftRepository(tempDir)

        val draft = FeedbackDraft(
            id = "draft-101",
            type = FeedbackType.PLAYBACK_ISSUE,
            category = FeedbackCategory.PLAYBACK,
            title = "Audio drops after 10 minutes",
            description = "Playing long FLAC audio cuts out silently after approximately 10 minutes.",
            stepsToReproduce = "1. Play 60min flac\n2. Wait 10min",
            frequency = IssueFrequency.OFTEN,
            mediaContext = MediaPlaybackContext(
                container = "FLAC",
                audioCodec = "flac",
                playbackEngine = "MpvPlayerEngine",
            ),
            updatedAtEpochMs = 1700000000000L,
        )

        val saveRes = repo.saveDraft(draft)
        assertTrue(saveRes.isSuccess)

        val loaded = repo.getDraft("draft-101")
        assertNotNull(loaded)
        assertEquals("draft-101", loaded.id)
        assertEquals(FeedbackType.PLAYBACK_ISSUE, loaded.type)
        assertEquals("Audio drops after 10 minutes", loaded.title)
        assertEquals("FLAC", loaded.mediaContext?.container)

        // Verify JSON file exists on disk
        val file = tempDir.resolve("draft-101.json")
        assertTrue(file.exists())
    }

    @Test
    fun testListDraftsSortedByTimestamp() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-drafts-list")
        val repo = DesktopFeedbackDraftRepository(tempDir)

        val d1 = FeedbackDraft(id = "d1", title = "Older draft", description = "Desc 1", updatedAtEpochMs = 1000L)
        val d2 = FeedbackDraft(id = "d2", title = "Newer draft", description = "Desc 2", updatedAtEpochMs = 5000L)
        val d3 = FeedbackDraft(id = "d3", title = "Middle draft", description = "Desc 3", updatedAtEpochMs = 3000L)

        repo.saveDraft(d1)
        repo.saveDraft(d2)
        repo.saveDraft(d3)

        val list = repo.listDrafts()
        assertEquals(3, list.size)
        assertEquals("d2", list[0].id, "Newest draft should be first")
        assertEquals("d3", list[1].id, "Middle draft should be second")
        assertEquals("d1", list[2].id, "Oldest draft should be third")
    }

    @Test
    fun testDeleteAndClearDrafts() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-drafts-del")
        val repo = DesktopFeedbackDraftRepository(tempDir)

        repo.saveDraft(FeedbackDraft(id = "to-delete", title = "Delete me", description = "Valid text description"))
        repo.saveDraft(FeedbackDraft(id = "keep", title = "Keep me", description = "Valid text description"))

        assertEquals(2, repo.listDrafts().size)

        repo.deleteDraft("to-delete")
        assertNull(repo.getDraft("to-delete"))
        assertEquals(1, repo.listDrafts().size)

        repo.clearDrafts()
        assertTrue(repo.listDrafts().isEmpty())
    }

    @Test
    fun testExportDraftToFile() {
        val tempDir = Files.createTempDirectory("vimivo-drafts-export")
        val repo = DesktopFeedbackDraftRepository(tempDir)

        val draft = FeedbackDraft(
            id = "export-test",
            type = FeedbackType.BUG_REPORT,
            title = "Subtitles overlapping with player controls",
            description = "When controls auto-hide, subtitle stays in normal position, " +
                "but when controls appear, subtitles are covered.",
            stepsToReproduce = "1. Play video with ASS subtitles\n2. Move mouse to trigger controls",
        )

        val exportFile = tempDir.resolve("exported_report.md")
        val result = repo.exportDraftToFile(draft, exportFile)

        assertTrue(result.isSuccess)
        assertTrue(exportFile.exists())
        val content = exportFile.readText()
        assertTrue(content.contains("# [Bug Report]: Subtitles overlapping with player controls"))
        assertTrue(content.contains("## Steps to Reproduce"))
        assertTrue(content.contains("Zero credentials, passwords, private keys"))
    }
}
