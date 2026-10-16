package dev.vimivo.platform.android.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class VimivoPlaybackWidgetTest {
    @Test
    fun progressIsBoundedAndHandlesUnknownDuration() {
        assertEquals(0, widgetProgress(1_000, 0))
        assertEquals(0, widgetProgress(-1, 10_000))
        assertEquals(500, widgetProgress(5_000, 10_000))
        assertEquals(1_000, widgetProgress(12_000, 10_000))
    }
}
