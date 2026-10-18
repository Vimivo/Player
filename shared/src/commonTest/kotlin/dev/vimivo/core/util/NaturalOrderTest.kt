package dev.vimivo.core.util

import kotlin.test.Test
import kotlin.test.assertEquals

class NaturalOrderTest {
    @Test
    fun ordersNumbersByValue() {
        val names = listOf("Show ep10.mkv", "Show ep2.mkv", "Show ep1.mkv", "show ep3.mkv")
        val expected = listOf("Show ep1.mkv", "Show ep2.mkv", "show ep3.mkv", "Show ep10.mkv")
        assertEquals(expected, names.sortedWith(NaturalOrder))
    }

    @Test
    fun handlesLeadingZerosHugeNumbersAndPrefixes() {
        val names = listOf("track 010", "track 9", "track 0009", "track 99999999999999999999", "track", "Track 1a")
        assertEquals(
            listOf("track", "Track 1a", "track 9", "track 0009", "track 010", "track 99999999999999999999"),
            names.sortedWith(NaturalOrder),
        )
    }
}
