package dev.vimivo.playback

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueueNavigationTest {
    @Test
    fun listOrderContinuesAndStopsOrLoopsAtTheEnd() {
        assertEquals(2, QueueNavigation.next(1, 3, QueueRepeatMode.OFF, null))
        assertNull(QueueNavigation.next(2, 3, QueueRepeatMode.OFF, null))
        assertEquals(0, QueueNavigation.next(2, 3, QueueRepeatMode.ALL, null))
        assertEquals(1, QueueNavigation.next(1, 3, QueueRepeatMode.ONE, null))
    }

    @Test
    fun failuresNeverRetryUnderRepeatOne() {
        assertEquals(2, QueueNavigation.next(1, 3, QueueRepeatMode.ONE, null, afterFailure = true))
    }

    /** Regression (Android): shuffle picked random items with replacement and never ended with repeat off. */
    @Test
    fun shuffleVisitsEveryItemOnceThenStops() {
        val order = QueueNavigation.shuffledOrder(size = 5, current = 2, random = Random(1))
        assertEquals(2, order.first())
        assertEquals((0 until 5).toSet(), order.toSet())

        val visited = generateSequence(order.first()) { index ->
            QueueNavigation.next(index, 5, QueueRepeatMode.OFF, order)
        }.toList()
        assertEquals(order, visited)
    }
}
