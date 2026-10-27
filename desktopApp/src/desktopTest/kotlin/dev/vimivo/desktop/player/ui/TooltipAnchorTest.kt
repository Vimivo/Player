package dev.vimivo.desktop.player.ui

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class TooltipAnchorTest {
    @Test
    fun centresOnThePointerJustAboveTheTimeline() {
        // Pointer at screen (700, 800), 10 px (5 pt at 2×) below the timeline's top edge: the edge is at y = 795.
        val anchor = TooltipAnchor(screenX = 700, screenY = 800, pointerYInTimelinePx = 10f)
        val position = anchor.tooltipPosition(DpSize(176.dp, 52.dp), density = 2f, gap = 6.dp)
        assertEquals(DpOffset(612.dp, 737.dp), position)
    }
}
