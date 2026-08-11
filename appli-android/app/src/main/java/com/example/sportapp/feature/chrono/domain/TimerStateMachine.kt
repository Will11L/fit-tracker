package com.example.sportapp.feature.chrono.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * State machine pure du minuteur (timer décompte). Aucune dépendance Android :
 * temps via `Clock` injecté, scheduling via `CoroutineScope` injecté.
 *
 * Cycle d'états :
 *   IDLE → start() → RUNNING (si duration > 0)
 *   RUNNING → pause() → PAUSED
 *   PAUSED → resume() → RUNNING
 *   RUNNING → (auto à 0) → FINISHED
 *   FINISHED → restart() → RUNNING (reset + start)
 *   any → reset() → IDLE
 *
 * `name` est le label du timer (ex. "1 min" pour preset, "1 min 30 s" pour custom).
 * Mis à jour via `setDuration(name, durationMillis)`. Conservé pour la notif
 * "Timer finished" (cf. fix bug `notifyTimerDone` en P2.6).
 *
 * Le passage à FINISHED est observable via `state.value == FINISHED` —
 * le VM s'y abonne pour déclencher la notif système.
 */
class TimerStateMachine(
    private val scope: CoroutineScope,
    private val clock: Clock,
) {

    enum class State { IDLE, RUNNING, PAUSED, FINISHED }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _durationMillis = MutableStateFlow(0L)
    val durationMillis: StateFlow<Long> = _durationMillis

    private val _remainingMillis = MutableStateFlow(0L)
    val remainingMillis: StateFlow<Long> = _remainingMillis

    private var tickerJob: Job? = null
    private var timerEndMillis: Long = 0L
    private var remainingOnPauseMillis: Long = 0L

    /**
     * Définit nom + durée. Si IDLE, le `remainingMillis` affiché est aussi
     * réinitialisé à la nouvelle durée. Sinon (timer en cours), seule la
     * référence pour un futur restart est mise à jour.
     */
    fun setDuration(name: String, durationMillis: Long) {
        _name.value = name
        _durationMillis.value = durationMillis.coerceAtLeast(0L)
        if (_state.value == State.IDLE) {
            _remainingMillis.value = _durationMillis.value
        }
    }

    fun start() {
        if (_state.value != State.IDLE) return
        val duration = _durationMillis.value
        if (duration <= 0L) return
        timerEndMillis = clock.nowMillis() + duration
        _state.value = State.RUNNING
        startTicker()
    }

    fun pause() {
        if (_state.value != State.RUNNING) return
        val rem = (timerEndMillis - clock.nowMillis()).coerceAtLeast(0L)
        remainingOnPauseMillis = rem
        _remainingMillis.value = rem
        _state.value = State.PAUSED
        stopTicker()
    }

    fun resume() {
        if (_state.value != State.PAUSED) return
        val rem = remainingOnPauseMillis.coerceAtLeast(0L)
        if (rem <= 0L) {
            _remainingMillis.value = 0L
            _state.value = State.FINISHED
            stopTicker()
            return
        }
        timerEndMillis = clock.nowMillis() + rem
        _state.value = State.RUNNING
        startTicker()
    }

    fun reset() {
        stopTicker()
        timerEndMillis = 0L
        remainingOnPauseMillis = 0L
        _remainingMillis.value = _durationMillis.value
        _state.value = State.IDLE
    }

    fun restart() {
        reset()
        start()
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (true) {
                val rem = (timerEndMillis - clock.nowMillis()).coerceAtLeast(0L)
                _remainingMillis.value = rem
                if (rem <= 0L) {
                    _state.value = State.FINISHED
                    stopTicker()
                    break
                }
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
