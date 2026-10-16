@file:Suppress(
    "ArgumentListWrapping", "MaximumLineLength", "MaxLineLength",
)

package dev.vimivo.core.feedback

import dev.vimivo.core.diagnostics.DiagnosticError
import dev.vimivo.core.diagnostics.DiagnosticEvent
import dev.vimivo.core.diagnostics.DiagnosticReport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedbackTest {

    @Test
    fun testValidatorRequiresMinimumTitleAndDescription() {
        val draftShortTitle = FeedbackDraft(
            id = "test-1",
            type = FeedbackType.BUG_REPORT,
            title = "Bug",
            description = "Valid description that is long enough.",
            stepsToReproduce = "Step 1: Open app. Step 2: Crash.",
        )
        val res1 = FeedbackValidator.validate(draftShortTitle)
        assertFalse(res1.isValid)
        assertTrue((res1 as FeedbackValidationResult.Invalid).errors.any { it.contains("Title must be at least") })

        val draftShortDesc = FeedbackDraft(
            id = "test-2",
            type = FeedbackType.FEATURE_REQUEST,
            title = "Add Equalizer Presets",
            description = "Short",
        )
        val res2 = FeedbackValidator.validate(draftShortDesc)
        assertFalse(res2.isValid)
        assertTrue((res2 as FeedbackValidationResult.Invalid).errors.any { it.contains("Description must be at least") })
    }

    @Test
    fun testValidatorRequiresStepsOrActualBehaviorForBugs() {
        val draftNoSteps = FeedbackDraft(
            id = "test-3",
            type = FeedbackType.BUG_REPORT,
            title = "Playback freezes on pause",
            description = "Whenever I press space, the player stops responding.",
            stepsToReproduce = "",
            actualBehavior = "",
        )
        val res = FeedbackValidator.validate(draftNoSteps)
        assertFalse(res.isValid)
        assertTrue((res as FeedbackValidationResult.Invalid).errors.any { it.contains("reproduction steps") })

        val draftWithSteps = draftNoSteps.copy(
            stepsToReproduce = "1. Play song\n2. Press pause\n3. Wait 5s",
        )
        assertTrue(FeedbackValidator.validate(draftWithSteps).isValid)
    }

    @Test
    fun testValidatorAcceptsValidFeatureRequest() {
        val draftFeature = FeedbackDraft(
            id = "test-4",
            type = FeedbackType.FEATURE_REQUEST,
            category = FeedbackCategory.USER_INTERFACE,
            title = "Add custom accent color picker",
            description = "Allow users to choose an arbitrary HSL accent color for playback sliders and buttons.",
            proposedSolution = "Provide a color picker dialog in Settings under Theme.",
        )
        val res = FeedbackValidator.validate(draftFeature)
        assertTrue(res.isValid)
    }

    @Test
    fun testReportFormatterRedactsUserPathsAndTokens() {
        val draft = FeedbackDraft(
            id = "test-5",
            type = FeedbackType.BUG_REPORT,
            category = FeedbackCategory.PLAYBACK,
            title = "Error playing /Users/alice/SecretMusic/song.mp3",
            description = "Failed to load token https://stream.com/live?token=secret_xyz123 on startup.",
            stepsToReproduce = "Open file in /home/bob/Downloads/track.flac",
            appVersion = "1.0.0-rc.1",
            platform = "macOS",
        )

        val markdown = FeedbackReportFormatter.formatMarkdown(draft)
        assertFalse(markdown.contains("/Users/alice"), "User home directory must be redacted")
        assertFalse(markdown.contains("/home/bob"), "Linux user directory must be redacted")
        assertFalse(markdown.contains("secret_xyz123"), "URL tokens must be redacted")
        assertTrue(markdown.contains("[Bug Report]"))
        assertTrue(markdown.contains("1.0.0-rc.1"))
    }

    @Test
    fun testReportFormatterIncludesMediaContext() {
        val draft = FeedbackDraft(
            id = "test-6",
            type = FeedbackType.PLAYBACK_ISSUE,
            category = FeedbackCategory.PLAYBACK,
            title = "Black screen on 4K HEVC video",
            description = "Audio plays smoothly but video surface remains black.",
            mediaContext = MediaPlaybackContext(
                container = "Matroska (MKV)",
                videoCodec = "hevc (Main 10)",
                audioCodec = "flac",
                resolution = "3840x2160",
                hardwareAcceleration = "videotoolbox",
                playbackEngine = "MpvPlayerEngine",
                errorMessage = "UNSUPPORTED_PROFILE",
            ),
            stepsToReproduce = "Play 4K HEVC 10-bit file",
        )

        val markdown = FeedbackReportFormatter.formatMarkdown(draft)
        assertTrue(markdown.contains("Matroska (MKV)"))
        assertTrue(markdown.contains("hevc (Main 10)"))
        assertTrue(markdown.contains("videotoolbox"))
        assertTrue(markdown.contains("UNSUPPORTED_PROFILE"))
    }

    @Test
    fun testReportFormatterAppendsSanitizedDiagnosticsWhenRequested() {
        val draft = FeedbackDraft(
            id = "test-7",
            type = FeedbackType.BUG_REPORT,
            title = "Playback crash on seek",
            description = "App crashed when seeking to the end of a song.",
            stepsToReproduce = "Seek to 99% position",
            includeDiagnostics = true,
        )

        val diag = DiagnosticReport(
            appVersion = "1.0.0-rc.1",
            buildCommit = "3924266",
            platform = "macOS",
            osVersion = "macOS 15.0",
            architecture = "aarch64",
            generatedTimestampEpochMs = 1700000000000L,
            playbackBackend = "libmpv 2.0.0",
            recentEvents = listOf(DiagnosticEvent(1700000000000L, "PLAYBACK", "Seek initiated")),
            errorSummaries = listOf(
                DiagnosticError(
                    1700000000000L,
                    "ENGINE",
                    "NativePlaybackException",
                    "Invalid EOF seek from /Users/alice/music.mp3",
                    listOf("at dev.vimivo.desktop.playback.MpvPlayerEngine.seekTo"),
                ),
            ),
        )

        val markdown = FeedbackReportFormatter.formatMarkdown(draft, diag)
        assertTrue(markdown.contains("Diagnostics Excerpt (Privacy-Safe)"))
        assertTrue(markdown.contains("NativePlaybackException"))
        assertFalse(markdown.contains("/Users/alice"), "Username in error message must be redacted")
    }

    @Test
    fun testGitHubUrlGeneratorConstructsValidUrl() {
        val draft = FeedbackDraft(
            id = "test-8",
            type = FeedbackType.BUG_REPORT,
            title = "Playback freeze",
            description = "Player freezes on track end.",
            stepsToReproduce = "1. Play track to completion",
        )

        val url = FeedbackReportFormatter.generateGitHubIssueUrl(draft)
        assertTrue(url.startsWith("https://github.com/Vimivo/Player/issues/new?"))
        assertTrue(url.contains("template=bug_report.yml"))
        assertTrue(url.contains("title=%5BBug%5D%3A%20Playback%20freeze"))
        assertTrue(url.contains("body="))
    }

    @Test
    fun testUnicodeAndBengaliPreservation() {
        val draft = FeedbackDraft(
            id = "test-9",
            type = FeedbackType.FEATURE_REQUEST,
            title = "বাংলা গানের লিরিক্স সাপোর্ট (Bengali Lyrics Support)",
            description = "রবীন্দ্রসংগীত এবং নজরুলগীতির সঠিক ইউনিকোড লিরিক্স সমর্থন নিশ্চিত করুন।",
            proposedSolution = "LRC ফাইলে বাংলা যুক্তাক্ষর এবং ইউনিকোড র‍্যান্ডারিং উন্নত করা।",
        )

        val validation = FeedbackValidator.validate(draft)
        assertTrue(validation.isValid, "Bengali text must pass validation")

        val markdown = FeedbackReportFormatter.formatMarkdown(draft)
        assertTrue(markdown.contains("রবীন্দ্রসংগীত"), "Bengali text must be preserved in markdown output")
    }

    @Test
    fun testInMemoryDraftRepositoryLifecycle() = runTest {
        val repo = InMemoryFeedbackDraftRepository()

        val draft1 = FeedbackDraft(
            id = "d-1",
            type = FeedbackType.BUG_REPORT,
            title = "First draft",
            description = "Some description",
            updatedAtEpochMs = 1000L,
        )
        val draft2 = FeedbackDraft(
            id = "d-2",
            type = FeedbackType.FEATURE_REQUEST,
            title = "Second draft",
            description = "Another description",
            updatedAtEpochMs = 2000L,
        )

        repo.saveDraft(draft1)
        repo.saveDraft(draft2)

        val list = repo.listDrafts()
        assertEquals(2, list.size)
        assertEquals("d-2", list.first().id, "Latest updated draft should be listed first")

        val retrieved = repo.getDraft("d-1")
        assertNotNull(retrieved)
        assertEquals("First draft", retrieved.title)

        repo.deleteDraft("d-1")
        assertNull(repo.getDraft("d-1"))
        assertEquals(1, repo.listDrafts().size)

        repo.clearDrafts()
        assertTrue(repo.listDrafts().isEmpty())
    }
}
