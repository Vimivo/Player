@file:Suppress(
    "ArgumentListWrapping", "FunctionNaming", "LongMethod", "MaximumLineLength",
    "MaxLineLength", "TooGenericExceptionCaught",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.AutomationActionType
import dev.vimivo.core.model.AutomationTrigger
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.OperationStage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopAutomationJournalTest {

    @Test
    fun managesWorkflowRulesAndPersistsStateAcrossQueries() {
        val tempDir = Files.createTempDirectory("vimivo-rules-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val engine = DesktopAutomationEngine(database)

            val initialRules = engine.rules()
            assertTrue(initialRules.isEmpty())

            val rule = engine.createRule(
                name = "Auto Clean Whitespace",
                trigger = AutomationTrigger.AFTER_SCAN,
                action = AutomationActionType.CLEAN_METADATA_WHITESPACE,
            )
            assertEquals("Auto Clean Whitespace", rule.name)
            assertEquals(AutomationTrigger.AFTER_SCAN, rule.trigger)
            assertEquals(AutomationActionType.CLEAN_METADATA_WHITESPACE, rule.action)
            assertTrue(rule.enabled)

            val loadedRules = engine.rules()
            assertEquals(1, loadedRules.size)
            assertEquals(rule.id, loadedRules[0].id)

            engine.updateRuleStatus(rule.id, "SUCCESS")
            val updated = engine.rules()[0]
            assertEquals("SUCCESS", updated.lastStatus)
            assertNotNull(updated.lastRunEpochMillis)

            engine.deleteRule(rule.id)
            assertTrue(engine.rules().isEmpty())
        }
    }

    @Test
    fun tracksJobLifecycleThroughBatchOperationJournal() {
        val tempDir = Files.createTempDirectory("vimivo-journal-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val engine = DesktopAutomationEngine(database)

            val jobId = engine.startJob("BATCH_RENAME", "Planned rename of 10 tracks")
            assertNotNull(jobId)

            val entriesAfterStart = engine.journalEntries()
            assertEquals(1, entriesAfterStart.size)
            assertEquals(jobId, entriesAfterStart[0].jobId)
            assertEquals(OperationStage.PLANNED, entriesAfterStart[0].stage)

            engine.updateJobStage(jobId, OperationStage.RUNNING, 10, "Processing items")
            val entriesRunning = engine.journalEntries()
            assertEquals(OperationStage.RUNNING, entriesRunning[0].stage)
            assertEquals(10, entriesRunning[0].affectedCount)

            engine.updateJobStage(jobId, OperationStage.VERIFIED, 10, "Completed successfully")
            val entriesVerified = engine.journalEntries()
            assertEquals(OperationStage.VERIFIED, entriesVerified[0].stage)
            assertEquals("Completed successfully", entriesVerified[0].details)
        }
    }

    @Test
    fun runsNonDestructiveDryRunsWithoutModifyingMedia() {
        val tempDir = Files.createTempDirectory("vimivo-dryrun-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val engine = DesktopAutomationEngine(database)

            val file1 = tempDir.resolve("song1.mp3").toFile()
            file1.writeText("song 1")

            val media = listOf(
                MediaItem(
                    id = MediaId("song-whitespace-1"),
                    uri = file1.toURI().toString(),
                    title = "  Leading and Trailing Spaces  ",
                    artist = "Untrimmed Artist ",
                    kind = MediaKind.AUDIO,
                ),
                MediaItem(
                    id = MediaId("song-clean-2"),
                    uri = file1.toURI().toString(),
                    title = "Clean Title",
                    artist = "Clean Artist",
                    kind = MediaKind.AUDIO,
                ),
                MediaItem(
                    id = MediaId("song-missing-3"),
                    uri = "file:///non/existent/path.mp3",
                    title = "Missing",
                    kind = MediaKind.AUDIO,
                ),
            )

            val ruleWhitespace = engine.createRule(
                "Clean Whitespace",
                AutomationTrigger.MANUAL,
                AutomationActionType.CLEAN_METADATA_WHITESPACE,
            )
            val dryRunWhitespace = engine.dryRun(ruleWhitespace, media)
            assertEquals(1, dryRunWhitespace.estimatedAffectedItems)
            assertTrue(dryRunWhitespace.summary.contains("Found 1 items with leading/trailing whitespace"))

            val ruleMissing = engine.createRule(
                "Find Missing",
                AutomationTrigger.MANUAL,
                AutomationActionType.IDENTIFY_MISSING_MEDIA,
            )
            val dryRunMissing = engine.dryRun(ruleMissing, media)
            assertEquals(1, dryRunMissing.estimatedAffectedItems)

            // Verify file content is unchanged
            assertEquals("song 1", file1.readText())
        }
    }

    @Test
    fun generatesAccurateLibraryHealthReport() {
        val tempDir = Files.createTempDirectory("vimivo-health-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val engine = DesktopAutomationEngine(database)

            val existingFile = tempDir.resolve("track.mp3").toFile()
            existingFile.writeText("audio")

            val items = listOf(
                MediaItem(
                    id = MediaId("item-1"),
                    uri = existingFile.toURI().toString(),
                    title = "Complete Track",
                    artist = "Known Artist",
                    album = "Known Album",
                    genre = "Rock",
                    kind = MediaKind.AUDIO,
                ),
                MediaItem(
                    id = MediaId("item-2"),
                    uri = existingFile.toURI().toString(),
                    title = "Incomplete Track",
                    artist = null,
                    album = null,
                    genre = null,
                    kind = MediaKind.AUDIO,
                ),
                MediaItem(
                    id = MediaId("item-3"),
                    uri = "file:///missing/video.mp4",
                    title = "Missing Video",
                    kind = MediaKind.VIDEO,
                ),
            )

            val report = engine.generateHealthReport(items)
            assertEquals(3, report.totalMediaCount)
            assertEquals(2, report.availableMediaCount)
            assertEquals(1, report.missingMediaCount)
            assertEquals(2, report.missingMetadataCount)
            assertTrue(report.healthScorePercent in 0..100)
        }
    }
}
