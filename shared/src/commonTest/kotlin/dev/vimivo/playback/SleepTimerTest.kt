@file:Suppress("MaximumLineLength", "MaxLineLength")

package dev.vimivo.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {

    @Test
    fun testScheduleAndCancel() = runTest {
        var triggered = false
        var simulatedNow = 1_000_000L
        val timer = SleepTimer(
            scope = backgroundScope,
            currentTimeProvider = { simulatedNow },
            onTrigger = { triggered = true },
        )

        timer.schedule(durationMinutes = 15, fadeOutSeconds = 5)
        val state = timer.state.value
        assertNotNull(state.deadlineEpochMillis)
        assertEquals(15 * 60_000L, state.remainingMillis)
        assertEquals(5, state.fadeOutSeconds)
        assertFalse(state.stopAfterCurrent)

        timer.cancel()
        assertNull(timer.state.value.deadlineEpochMillis)
        assertNull(timer.state.value.remainingMillis)
        assertFalse(triggered)
    }

    @Test
    fun testAfterCurrentAndConsume() = runTest {
        var triggered = false
        val timer = SleepTimer(
            scope = backgroundScope,
            onTrigger = { triggered = true },
        )

        assertFalse(timer.consumeStopAfterCurrent())
        timer.afterCurrent(fadeOutSeconds = 10)
        assertTrue(timer.state.value.stopAfterCurrent)
        assertEquals(10, timer.state.value.fadeOutSeconds)

        assertTrue(timer.consumeStopAfterCurrent())
        assertFalse(timer.state.value.stopAfterCurrent)
        assertFalse(timer.consumeStopAfterCurrent(), "Second consumption must return false")
    }

    @Test
    fun testExpirationTriggersAction() = runTest {
        var triggered = false
        var simulatedNow = 100_000L
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val customScope = TestScope(testDispatcher)

        val timer = SleepTimer(
            scope = customScope,
            currentTimeProvider = { simulatedNow },
            onTrigger = { triggered = true },
        )

        timer.schedule(durationMinutes = 1)
        simulatedNow += 65_000L
        customScope.advanceTimeBy(1_000L)
        customScope.runCurrent()

        assertTrue(triggered, "Timer should have triggered callback on expiration")
        assertNull(timer.state.value.deadlineEpochMillis)
    }
}
