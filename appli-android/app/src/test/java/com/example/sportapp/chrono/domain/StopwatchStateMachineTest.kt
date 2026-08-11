package com.example.sportapp.feature.chrono.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StopwatchStateMachineTest {

    private fun newMachine(): Pair<StopwatchStateMachine, FakeClock> {
        val clock = FakeClock(0L)
        val dispatcher = UnconfinedTestDispatcher()
        val scope = TestScope(dispatcher)
        return StopwatchStateMachine(scope, clock) to clock
    }

    @Test
    fun `initial state is IDLE with elapsed=0 and no laps`() {
        val (sm, _) = newMachine()
        assertEquals(StopwatchStateMachine.State.IDLE, sm.state.value)
        assertEquals(0L, sm.elapsedMillis.value)
        assertEquals(emptyList<Lap>(), sm.laps.value)
    }

    @Test
    fun `start transitions IDLE to RUNNING`() {
        val (sm, _) = newMachine()
        sm.start()
        assertEquals(StopwatchStateMachine.State.RUNNING, sm.state.value)
    }

    @Test
    fun `pause from RUNNING captures accumulated millis`() = runTest {
        val (sm, clock) = newMachine()
        sm.start()
        clock.advance(2_500L)
        sm.pause()
        assertEquals(StopwatchStateMachine.State.PAUSED, sm.state.value)
        assertEquals(2_500L, sm.elapsedMillis.value)
    }

    @Test
    fun `resume continues accumulation from pause`() = runTest {
        val (sm, clock) = newMachine()
        sm.start()
        clock.advance(1_000L)
        sm.pause()
        // simulate gap (paused time should not count)
        clock.advance(5_000L)
        sm.resume()
        clock.advance(500L)
        sm.pause()
        assertEquals(1_500L, sm.elapsedMillis.value)
    }

    @Test
    fun `reset returns to IDLE and clears laps`() = runTest {
        val (sm, clock) = newMachine()
        sm.start()
        clock.advance(1_000L)
        sm.lap()
        sm.pause()
        sm.reset()
        assertEquals(StopwatchStateMachine.State.IDLE, sm.state.value)
        assertEquals(0L, sm.elapsedMillis.value)
        assertEquals(emptyList<Lap>(), sm.laps.value)
    }

    @Test
    fun `lap captures snapshot and computes delta vs previous lap`() = runTest {
        val (sm, clock) = newMachine()
        sm.start()
        clock.advance(1_000L)
        sm.lap()
        clock.advance(1_500L)
        sm.lap()
        val laps = sm.laps.value
        assertEquals(2, laps.size)
        assertEquals(1, laps[0].index)
        assertEquals(1_000L, laps[0].lapMillis)
        assertEquals(1_000L, laps[0].totalMillis)
        assertEquals(2, laps[1].index)
        assertEquals(1_500L, laps[1].lapMillis)
        assertEquals(2_500L, laps[1].totalMillis)
    }

    @Test
    fun `lap is no-op when not RUNNING`() {
        val (sm, _) = newMachine()
        sm.lap() // IDLE
        assertTrue(sm.laps.value.isEmpty())
    }

    @Test
    fun `ticker updates elapsedMillis while RUNNING`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val clock = FakeClock(0L)
        val scope = TestScope(dispatcher)
        val sm = StopwatchStateMachine(scope, clock)

        sm.start()
        // Advance both virtual scheduler (so delay(50) can complete)
        // AND the FakeClock so the ticker reads a non-zero elapsed.
        repeat(4) {
            clock.advance(50L)
            advanceTimeBy(50L)
        }
        assertTrue("elapsedMillis should be > 0 after ticker iterations", sm.elapsedMillis.value > 0L)
        sm.pause()
    }
}
