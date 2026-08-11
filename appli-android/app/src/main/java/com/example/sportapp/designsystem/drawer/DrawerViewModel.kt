package com.example.sportapp.designsystem.drawer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.R
import com.example.sportapp.core.data.local.ActualWorkoutDao
import com.example.sportapp.core.data.local.NotificationDao
import com.example.sportapp.core.data.local.TaskCheckDao
import com.example.sportapp.core.data.local.TaskDao
import com.example.sportapp.core.data.model.ActualWorkout
import com.example.sportapp.core.data.remote.WebSocketManager
import com.example.sportapp.core.domain.tasks.ScheduledTaskExpander
import com.example.sportapp.core.sync.SyncCoordinator
import com.example.sportapp.core.sync.SyncEvents
import com.example.sportapp.core.sync.SyncManager
import com.example.sportapp.core.sync.SyncRegistry
import com.example.sportapp.core.sync.base.observeStats
import com.example.sportapp.core.utils.CustomDateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject

/**
 * D4 (2026-05-12) : compteur de tasks visibles aujourd'hui pour la pill du
 * drawer (style SettingsSync EntityStatsBadge). Inclut DAILY + occurrences
 * non-DAILY tombant aujourd'hui.
 */
data class TasksTodayStats(val done: Int, val total: Int)

@HiltViewModel
class DrawerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val actualWorkoutDao: ActualWorkoutDao,
    private val notificationDao: NotificationDao,
    private val taskDao: TaskDao,
    private val taskCheckDao: TaskCheckDao,
    private val syncManager: SyncManager,
    private val syncCoordinator: SyncCoordinator,
    private val registry: SyncRegistry,
    private val wsManager: WebSocketManager
): ViewModel() {

    val hasUnsyncedData = syncManager.hasUnsyncedData
    val isWsConnected: StateFlow<Boolean> = wsManager.isConnected

    /** True si le device a une connexion reseau valide (NetworkMonitor). */
    val isOnline: StateFlow<Boolean> = SyncEvents.isNetworkAvailable

    /**
     * Compteur total des rows en attente de sync (unsynced + pendingDeletion)
     * cumule sur les 20 entites du SyncRegistry. Utilise pour le badge Drawer
     * "X items pending sync". T4.2 Phase 4.3.
     */
    val totalPendingCount: StateFlow<Int> =
        combine(registry.all.map { it.observeStats() }) { statsArray ->
            statsArray.sumOf { it.unsynced + it.pendingDeletion }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // Notifications
    val unreadNotificationsCount: StateFlow<Int> =
        notificationDao.observeUnreadCount()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                0
            )

    // Tasks visibles aujourd'hui (D4) : DAILY + occurrences non-DAILY (W/M/Y/NONE)
    private val todayIso: String = CustomDateUtils.getTodayIsoDay()

    val tasksTodayStats: StateFlow<TasksTodayStats> =
        combine(
            taskDao.observeAll()
                .map { it.filter { t -> !t.pendingDeletion && t.isActive } },
            taskCheckDao.observeByDate(todayIso)
                .map { it.filter { c -> !c.pendingDeletion } }
        ) { tasks, checks ->
            val today = runCatching { LocalDate.parse(todayIso) }.getOrNull()
                ?: return@combine TasksTodayStats(0, 0)
            val visible = tasks.filter { task ->
                if (task.recurrenceKind == "DAILY") true
                else ScheduledTaskExpander.occurrencesInRange(task, today, today).isNotEmpty()
            }
            val total = visible.size
            if (total == 0) return@combine TasksTodayStats(0, 0)

            val checkedSet = checks.filter { it.isChecked }.map { it.taskUUID }.toSet()
            val done = visible.count { it.uuid in checkedSet }
            TasksTodayStats(done, total)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksTodayStats(0, 0))


    // Today Session
    val todaySession: StateFlow<ActualWorkout?> =
        actualWorkoutDao.observeActualWorkoutByDay(CustomDateUtils.getTodayIsoDay())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)


    // Bottom Bar
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(1_000) // chaque minute
        }
    }

    val lastSyncText = ticker
        .onStart { emit(Unit) } // émet immédiatement
        .combine(syncManager.lastSyncTime) { _, isoString ->
            val never = context.getString(R.string.drawer_last_sync_never)
            if (isoString.isBlank()) {
                never
            } else {
                val lastSyncInstant = CustomDateUtils.parseInstantSafe(isoString)
                if (lastSyncInstant == null) return@combine never
                val nowInstant = CustomDateUtils.getNowInstant()
                val diff = java.time.Duration.between(lastSyncInstant, nowInstant)

                val seconds = diff.seconds
                val minutes = diff.toMinutes()
                val hours = diff.toHours()
                val days = diff.toDays()

                when {
                    minutes < 1 -> "$seconds s"
                    minutes == 1L -> "1 min ago"
                    minutes < 60 -> "$minutes min ago"
                    hours == 1L -> "1 hour ago"
                    hours < 24 -> "$hours hours ago"
                    days == 1L -> "Yesterday"
                    days < 7 -> "$days days ago"
                    else -> {
                        val date = lastSyncInstant
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        "on ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.dayOfMonth}"
                    }
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
            context.getString(R.string.drawer_last_sync_never)
        )


    fun syncAllAndRefresh() {
        viewModelScope.launch {
            syncCoordinator.onUserAction()
            syncManager.checkForUnsyncedData()
        }
    }

    fun checkForUnsyncedData() {
        viewModelScope.launch {
            syncManager.checkForUnsyncedData()
        }
    }

    fun restartWebSocket(token: String) {
        wsManager.start(token)
    }

}
