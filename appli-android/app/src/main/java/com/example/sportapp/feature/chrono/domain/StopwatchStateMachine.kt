package com.example.sportapp.feature.chrono.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * State machine pure du chrono (stopwatch). Aucune dépendance Android :
 * le temps vient d'un `Clock` injecté, le scheduling vient d'un
 * `CoroutineScope` injecté → testable JVM via TestScope + FakeClock.
 *
 * Cycle d'états :
 *   IDLE → start() → RUNNING
 *   RUNNING → pause() → PAUSED
 *   PAUSED → resume() → RUNNING
 *   any → reset() → IDLE (laps vidés)
 *   RUNNING → lap() (snapshot du temps courant)
 *
 * Le ticker s'auto-cancel à chaque pause/reset et au cancel du scope parent
 * (= viewModelScope.onCleared côté VM).
 */
class StopwatchStateMachine(
    private val scope: CoroutineScope,
    private val clock: Clock,
) {

    enum class State { IDLE, RUNNING, PAUSED }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state

    private val _elapsedMillis = MutableStateFlow(0L)
    val elapsedMillis: StateFlow<Long> = _elapsedMillis

    private val _laps = MutableStateFlow<List<Lap>>(emptyList())
    val laps: StateFlow<List<Lap>> = _laps

    private var tickerJob: Job? = null
    private var accumulatedMillis: Long = 0L
    private var runningStartMillis: Long = 0L
    private var lastLapTotalMillis: Long = 0L

    fun start() {
        if (_state.value == State.RUNNING) return
        accumulatedMillis = 0L
        runningStartMillis = clock.nowMillis()
        _state.value = State.RUNNING
        startTicker()
    }

    fun pause() {
        if (_state.value != State.RUNNING) return
        accumulatedMillis += (clock.nowMillis() - runningStartMillis)
        _elapsedMillis.value = accumulatedMillis
        _state.value = State.PAUSED
        stopTicker()
    }

    fun resume() {
        if (_state.value != State.PAUSED) return
        runningStartMillis = clock.nowMillis()
        _state.value = State.RUNNING
        startTicker()
    }

    fun reset() {
        stopTicker()
        accumulatedMillis = 0L
        runningStartMillis = 0L
        lastLapTotalMillis = 0L
        _elapsedMillis.value = 0L
        _laps.value = emptyList()
        _state.value = State.IDLE
    }

    fun lap() {
        if (_state.value != State.RUNNING) return
        val total = accumulatedMillis + (clock.nowMillis() - runningStartMillis)
        val lapMillis = total - lastLapTotalMillis
        lastLapTotalMillis = total
        val nextIndex = _laps.value.size + 1
        _laps.value = _laps.value + Lap(nextIndex, lapMillis, total)
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (true) {
                _elapsedMillis.value = accumulatedMillis + (clock.nowMillis() - runningStartMillis)
                delay(TICK_INTERVAL_MILLIS)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    companion object {
        private const val TICK_INTERVAL_MILLIS = 50L
    }
}
