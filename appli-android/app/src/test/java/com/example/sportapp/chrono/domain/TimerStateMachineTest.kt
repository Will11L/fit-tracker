package com.example.sportapp.feature.chrono.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerStateMachineTest {

    private fun newMachine(): Pair<TimerStateMachine, FakeClock> {
        val clock = FakeClock(0L)
        val dispatcher = UnconfinedTestDispatcher()
        val scope = TestScope(dispatcher)
        return TimerStateMachine(scope, clock) to clock
    }

    @Test
    fun `initial state is IDLE with empty name and zero duration`() {
        val (sm, _) = newMachine()
        assertEquals(TimerStateMachine.State.IDLE, sm.state.value)
        assertEquals("", sm.name.value)
        assertEquals(0L, sm.durationMillis.value)
        assertEquals(0L, sm.remainingMillis.value)
    }

    @Test
    fun `setDuration when IDLE updates display remaining`() {
        val (sm, _) = newMachine()
        sm.setDuration("1 min", 60_000L)
        assertEquals("1 min", sm.name.value)
        assertEquals(60_000L, sm.durationMillis.value)
        assertEquals(60_000L, sm.remainingMillis.value)
    }

    @Test
    fun `setDuration coerces negative to zero`() {
        val (sm, _) = newMachine()
        sm.setDuration("Bad", -100L)
        assertEquals(0L, sm.durationMillis.value)
    }

    @Test
    fun `start refuses to fire when duration is zero`() {
        val (sm, _) = newMachine()
        sm.start()
        assertEquals(TimerStateMachine.State.IDLE, sm.state.value)
    }

    @Test
    fun `start transitions IDLE to RUNNING when duration set`() {
        val (sm, _) = newMachine()
        sm.setDuration("30s", 30_000L)
        sm.start()
        assertEquals(TimerStateMachine.State.RUNNING, sm.state.value)
    }

    @Test
    fun `pause captures correct remaining`() = runTest {
        val (sm, clock) = newMachine()
        sm.setDuration("1 min", 60_000L)
        sm.start()
        clock.advance(20_000L)
        sm.pause()
        assertEquals(TimerStateMachine.State.PAUSED, sm.state.value)
        assertEquals(40_000L, sm.remainingMillis.value)
    }

    @Test
    fun `resume picks up where paused regardless of clock drift`() = runTest {
        val (sm, clock) = newMachine()
        sm.setDuration("1 min", 60_000L)
        sm.start()
        clock.advance(15_000L)
        sm.pause()
        // simulate paused gap (must NOT count down)
        clock.advance(60_000L)
        sm.resume()
        clock.advance(10_000L)
        sm.pause()
        assertEquals(35_000L, sm.remainingMillis.value)
    }

    @Test
    fun `reset returns to IDLE with full duration restored`() = runTest {
        val (sm, clock) = newMachine()
        sm.setDuration("1 min", 60_000L)
        sm.start()
        clock.advance(20_000L)
        sm.reset()
        assertEquals(TimerStateMachine.State.IDLE, sm.state.value)
        assertEquals(60_000L, sm.remainingMillis.value)
    }

    @Test
    fun `restart resets and starts in one shot`() {
        val (sm, _) = newMachine()
        sm.setDuration("30s", 30_000L)
        sm.start()
        sm.restart()
        assertEquals(TimerStateMachine.State.RUNNING, sm.state.value)
    }

    @Test
    fun `ticker reaches FINISHED when remaining hits zero`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val clock = FakeClock(0L)
        val scope = TestScope(dispatcher)
        val sm = TimerStateMachine(scope, clock)

        sm.setDuration("100ms", 100L)
        sm.start()

        // Advance clock past duration, then advance scheduler so the ticker reads it.
        clock.advance(150L)
        advanceTimeBy(100L)

        assertEquals(TimerStateMachine.State.FINISHED, sm.state.value)
        assertEquals(0L, sm.remainingMillis.value)
    }

    @Test
    fun `name persists across pause-resume cycle`() = runTest {
        val (sm, clock) = newMachine()
        sm.setDuration("Échauffement", 90_000L)
        sm.start()
        clock.advance(10_000L)
        sm.pause()
        sm.resume()
        assertEquals("Échauffement", sm.name.value)
    }
}
