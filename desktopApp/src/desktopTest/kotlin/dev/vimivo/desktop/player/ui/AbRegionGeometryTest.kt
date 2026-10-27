package dev.vimivo.desktop.player.ui

import dev.vimivo.playback.ABRepeatEnd
import dev.vimivo.playback.ABRepeatRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AbRegionGeometryTest {
    private val region = ABRepeatRange(pointA = 20_000, pointB = 40_000, enabled = true)
    private val duration = 100_000L
    private val width = 1_000f

    @Test
    fun theRegionSpansItsTwoPointsOnTheTrack() {
        val bounds = AbRegionGeometry.bounds(region, { it / duration.toFloat() * width }, minWidth = 2f)
        assertEquals(AbRegionGeometry.Bounds(200f, 400f), bounds)
    }

    @Test
    fun aLoneAIsAThinMarkerAndNothingIsDrawnWithoutA() {
        val marker = AbRegionGeometry.bounds(ABRepeatRange(pointA = 50_000), { it / duration.toFloat() * width }, 2f)
        assertEquals(AbRegionGeometry.Bounds(500f, 502f), marker)
        assertNull(AbRegionGeometry.bounds(ABRepeatRange(), { it.toFloat() }, 2f))
    }

    @Test
    fun aPointerNearAnEndGrabsThatEnd() {
        assertEquals(ABRepeatEnd.A, AbRegionGeometry.endAt(205f, region, width, duration, 8f))
        assertEquals(ABRepeatEnd.B, AbRegionGeometry.endAt(395f, region, width, duration, 8f))
        assertNull(AbRegionGeometry.endAt(300f, region, width, duration, 8f))
    }

    @Test
    fun onlyACompleteEnabledRegionCanBeGrabbed() {
        assertNull(AbRegionGeometry.endAt(200f, ABRepeatRange(pointA = 20_000), width, duration, 8f))
        assertNull(AbRegionGeometry.endAt(200f, region.copy(enabled = false), width, duration, 8f))
    }

    @Test
    fun theTextDescribesTheRegion() {
        assertEquals(", repeating 00:20 to 00:40", AbRegionGeometry.describe(region))
        assertEquals(", repeat start 00:20", AbRegionGeometry.describe(ABRepeatRange(pointA = 20_000)))
        assertEquals("", AbRegionGeometry.describe(ABRepeatRange()))
    }
}
