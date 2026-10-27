package dev.vimivo.desktop.platform

import dev.vimivo.core.model.MediaKind
import dev.vimivo.desktop.player.LaunchPlanner
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopDiscMediaTest {

    @Test
    fun identifiesAndResolvesBlurayFolderStructure() {
        val tempDir = Files.createTempDirectory("vimivo-bluray-test").toFile().apply { deleteOnExit() }
        val bdmv = File(tempDir, "BDMV").apply { mkdirs() }
        val stream = File(bdmv, "STREAM").apply { mkdirs() }
        File(bdmv, "index.bdmv").writeBytes(byteArrayOf(1, 2, 3))
        File(stream, "00001.m2ts").writeBytes(ByteArray(1024))
        File(stream, "00002.m2ts").writeBytes(ByteArray(8192))

        assertTrue(DesktopDiscMedia.isDiscFolder(tempDir))
        val media = DesktopDiscMedia.resolveDiscMedia(tempDir)
        assertNotNull(media)
        assertEquals(MediaKind.VIDEO, media.kind)
        assertEquals("00002.m2ts", media.fileName)
        assertTrue(media.id.value.startsWith("bluray://"))
    }

    @Test
    fun identifiesAndResolvesDvdFolderStructure() {
        val tempDir = Files.createTempDirectory("vimivo-dvd-test").toFile().apply { deleteOnExit() }
        val videoTs = File(tempDir, "VIDEO_TS").apply { mkdirs() }
        File(videoTs, "VIDEO_TS.IFO").writeBytes(byteArrayOf(1, 2, 3))
        File(videoTs, "VTS_01_0.VOB").writeBytes(ByteArray(512))
        File(videoTs, "VTS_01_1.VOB").writeBytes(ByteArray(4096))

        assertTrue(DesktopDiscMedia.isDiscFolder(tempDir))
        val media = DesktopDiscMedia.resolveDiscMedia(tempDir)
        assertNotNull(media)
        assertEquals(MediaKind.VIDEO, media.kind)
        assertEquals("VTS_01_1.VOB", media.fileName)
        assertTrue(media.id.value.startsWith("dvd://"))
    }

    @Test
    fun nonDiscFolderReturnsFalse() {
        val tempDir = Files.createTempDirectory("vimivo-plain-test").toFile().apply { deleteOnExit() }
        File(tempDir, "song.mp3").writeBytes(ByteArray(100))
        assertFalse(DesktopDiscMedia.isDiscFolder(tempDir))
    }

    @Test
    fun launchPlannerExpandsDiscFolderDirectly() {
        val tempDir = Files.createTempDirectory("vimivo-launch-disc").toFile().apply { deleteOnExit() }
        val bdmv = File(tempDir, "BDMV").apply { mkdirs() }
        val stream = File(bdmv, "STREAM").apply { mkdirs() }
        File(stream, "00000.m2ts").writeBytes(ByteArray(2048))

        val plan = LaunchPlanner.plan(listOf(tempDir), includeFolderSiblings = false)
        assertNotNull(plan)
        assertEquals(1, plan.items.size)
        assertEquals("00000.m2ts", plan.items.first().fileName)
    }

    @Test
    fun launchPlannerHandlesIsoImageFile() {
        val isoFile = File("../testmedia/pack/bluray.iso").absoluteFile
        if (isoFile.isFile) {
            val plan = LaunchPlanner.plan(listOf(isoFile), includeFolderSiblings = false)
            assertNotNull(plan)
            assertEquals(1, plan.items.size)
            assertEquals(MediaKind.VIDEO, plan.items.first().kind)
        }
    }
}
