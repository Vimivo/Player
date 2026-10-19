package dev.vimivo.desktop.playback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regression (Linux black video): libmpv's software-renderer warning switches playback to the fast profile. */
class MpvLogEventsTest {
    @Test
    fun recognisesLibmpvSoftwareRendererWarning() {
        // Exact line from mpv 0.37 on Mesa llvmpipe (docs/evidence/V1/linux-black-video).
        val warning = MpvLogMessage("vo/gpu/opengl", "warn", "Suspected software renderer or indirect context.")
        assertTrue(MpvLogEvents.isSoftwareRenderer(warning))
    }

    @Test
    fun ignoresOtherWarnings() {
        assertFalse(MpvLogEvents.isSoftwareRenderer(MpvLogMessage("ao/alsa", "error", "Playback open error")))
        assertFalse(MpvLogEvents.isSoftwareRenderer(MpvLogMessage("cplayer", "warn", "software renderer")))
    }

    @Test
    fun switchesToTheFastProfileOnlyOnce() {
        val watch = MpvRendererWatch()
        val warning = listOf(MpvLogMessage("vo/gpu/opengl", "warn", "Suspected software renderer or indirect context."))
        assertFalse(watch.firstSoftwareRendererReport(emptyList()))
        assertTrue(watch.firstSoftwareRendererReport(warning))
        assertFalse(watch.firstSoftwareRendererReport(warning))
        assertTrue(watch.softwareRenderer)
    }
}
