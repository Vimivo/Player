package dev.vimivo.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeinterlaceModeTest {
    /** The filters scripts/build_media/verify.py requires in every LGPL build (matrix item #13). */
    private val lgplDeinterlacers = setOf("yadif", "bwdif", "w3fdif", "estdif")
    private val gplOnly = setOf("mcdeint", "kerndeint", "pullup", "tinterlace", "nnedi", "phase")

    @Test
    fun offHasNoFilter() {
        assertNull(DeinterlaceMode.OFF.lavfiFilter)
        assertNull(DeinterlaceMode.OFF.filterName)
    }

    @Test
    fun everyActiveModeUsesAnLgplFilterThatTheBuildVerifies() {
        DeinterlaceMode.entries.filter { it != DeinterlaceMode.OFF }.forEach { mode ->
            assertTrue(mode.filterName in lgplDeinterlacers, "${mode.name} uses ${mode.filterName}")
            assertTrue(mode.filterName !in gplOnly)
        }
    }

    @Test
    fun autoOnlyTouchesFramesFlaggedInterlacedWhileTheOthersTreatEveryFrame() {
        assertTrue("deint=interlaced" in DeinterlaceMode.AUTO.lavfiFilter!!)
        listOf(DeinterlaceMode.YADIF, DeinterlaceMode.BWDIF, DeinterlaceMode.W3FDIF, DeinterlaceMode.ESTDIF)
            .forEach { assertTrue("deint=all" in it.lavfiFilter!!, it.name) }
    }

    @Test
    fun filterNameIsTheTextBeforeTheFirstOption() {
        assertEquals("yadif", DeinterlaceMode.YADIF.filterName)
        assertEquals("w3fdif", DeinterlaceMode.W3FDIF.filterName)
    }
}
