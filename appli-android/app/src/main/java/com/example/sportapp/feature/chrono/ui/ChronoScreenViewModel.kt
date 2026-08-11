package com.example.sportapp.feature.chrono.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.feature.chrono.data.ChronoSettings
import com.example.sportapp.feature.chrono.data.ChronoSettingsRepository
import com.example.sportapp.feature.chrono.data.ChronoTab
import com.example.sportapp.feature.chrono.domain.Clock
import com.example.sportapp.feature.chrono.domain.Lap
import com.example.sportapp.feature.chrono.domain.StopwatchStateMachine
import com.example.sportapp.feature.chrono.domain.TimerStateMachine
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.feature.notifications.domain.NotificationCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * VM du ChronoScreen + des MiniOverlays. Hoisté à la racine de MainActivity
 * (cf. politique B.4a Phase 1) → singleton Activity-scope, état partagé
 * entre l'écran principal et les overlays flottants.
 *
 * Logique pure dans `chrono/domain/` (StopwatchStateMachine + TimerStateMachine),
 * ce VM est un mince binding UI + side effects (notif, persistance settings).
 */
@HiltViewModel
class ChronoScreenViewModel @Inject constructor(
    private val notificationCenter: NotificationCenter,
    private val settingsRepo: ChronoSettingsRepository,
    clock: Clock,
) : ViewModel() {

    private val stopwatch = StopwatchStateMachine(viewModelScope, clock)
    private val timer = TimerStateMachine(viewModelScope, clock)

    // ===== UI bindings (noms préservés de l'ancien VM pour migration smooth) =====
    val stopwatchState: StateFlow<StopwatchStateMachine.State> = stopwatch.state
    val elapsedMillis: StateFlow<Long> = stopwatch.elapsedMillis
    val laps: StateFlow<List<Lap>> = stopwatch.laps

    val timerState: StateFlow<TimerStateMachine.State> = timer.state
    val timerName: StateFlow<String> = timer.name
    val timerDurationMillis: StateFlow<Long> = timer.durationMillis
    val remainingMillis: StateFlow<Long> = timer.remainingMillis

    val settings: StateFlow<ChronoSettings> = settingsRepo.settings

    init {
        // Restore dernier timer (nom + durée) au démarrage
        viewModelScope.launch {
            val initial = settingsRepo.settings.first()
            timer.setDuration(initial.lastTimerName, initial.lastTimerDurationMillis)
        }
        // Déclenche la notif système quand le timer atteint 0
        viewModelScope.launch {
            timer.state
                .filter { it == TimerStateMachine.State.FINISHED }
                .collect { onTimerFinished() }
        }
    }

    // ===== Stopwatch — dispatch des boutons UI =====
    fun onRightButton() {
        when (stopwatch.state.value) {
            StopwatchStateMachine.State.IDLE -> stopwatch.start()
            StopwatchStateMachine.State.RUNNING -> stopwatch.pause()
            StopwatchStateMachine.State.PAUSED -> stopwatch.resume()
        }
    }

    fun onLeftButton() {
        when (stopwatch.state.value) {
            StopwatchStateMachine.State.IDLE -> Unit
            StopwatchStateMachine.State.RUNNING -> stopwatch.lap()
            StopwatchStateMachine.State.PAUSED -> stopwatch.reset()
        }
    }

    // ===== Timer — dispatch des boutons UI =====
    fun onTimerRightButton() {
        when (timer.state.value) {
            TimerStateMachine.State.IDLE -> timer.start()
            TimerStateMachine.State.RUNNING -> timer.pause()
            TimerStateMachine.State.PAUSED -> timer.resume()
            TimerStateMachine.State.FINISHED -> timer.restart()
        }
    }

    fun onTimerLeftButton() {
        when (timer.state.value) {
            TimerStateMachine.State.IDLE -> Unit
            else -> timer.reset()
        }
    }

    /**
     * Définit nom + durée du timer. Le nom (label preset ou string générée
     * pour le custom dialog) sera utilisé dans la notif "Timer finished"
     * (fix du bug hardcode "Rest 90s" — cf. TODO_FIXES §5).
     * Persisté via DataStore pour reload au prochain démarrage.
     */
    fun setTimerDuration(name: String, millis: Long) {
        timer.setDuration(name, millis)
        viewModelScope.launch { settingsRepo.setLastTimer(name, millis) }
    }

    fun setActiveTab(tab: ChronoTab) {
        viewModelScope.launch { settingsRepo.setLastActiveTab(tab) }
    }

    // ===== Side effect : notif fin de timer =====
    private fun onTimerFinished() {
        viewModelScope.launch {
            val userId = CurrentUserManager.userId ?: return@launch
            val name = timer.name.value.ifBlank { "Timer" }
            val durationSec = (timer.durationMillis.value / 1000L).toInt()
            notificationCenter.notifyTimerDone(userId, name, durationSec)
        }
    }
}
