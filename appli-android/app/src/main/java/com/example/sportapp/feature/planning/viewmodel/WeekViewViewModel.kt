package com.example.sportapp.feature.planning.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.local.ActualWorkoutDao
import com.example.sportapp.core.data.local.PlannedWorkoutDao
import com.example.sportapp.core.data.model.ActualWorkout
import com.example.sportapp.core.data.model.PlannedWorkout
import com.example.sportapp.app.SnackbarAction
import com.example.sportapp.app.SnackbarController
import com.example.sportapp.core.data.local.ActualWorkoutExerciseDao
import com.example.sportapp.core.data.local.ActualWorkoutSetDao
import com.example.sportapp.core.data.local.PlannedWorkoutExerciseDao
import com.example.sportapp.core.data.model.ActualWorkoutExercise
import com.example.sportapp.core.data.model.ActualWorkoutSet
import com.example.sportapp.core.data.model.PlannedWorkoutExercise
import com.example.sportapp.core.network.CurrentUserManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.sportapp.core.sync.SyncManager
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.core.utils.CustomDateUtils.getDayOfWeekFromDate
import com.example.sportapp.core.utils.CustomDateUtils.getEndOfCurrentWeek
import com.example.sportapp.core.utils.CustomDateUtils.getStartOfCurrentWeek
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.util.UUID
import kotlinx.coroutines.flow.combine



@HiltViewModel
class WeekViewViewModel @Inject constructor(
    private val actualWorkoutDao: ActualWorkoutDao,
    private val actualWorkoutExerciseDao: ActualWorkoutExerciseDao,
    private val actualWorkoutSetDao: ActualWorkoutSetDao,
    private val plannedWorkoutDao: PlannedWorkoutDao,
    private val plannedWorkoutExerciseDao: PlannedWorkoutExerciseDao,
    private val syncEngine: SyncEngine,
    private val syncManager: SyncManager
) : ViewModel() {


    val userId: StateFlow<Int?> =
        MutableStateFlow(CurrentUserManager.userId) // statique au démarrage
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val actualWorkoutsForThisWeek: StateFlow<List<ActualWorkout>> =
        actualWorkoutDao.getActualWorkoutsForWeekFlow(
            getStartOfCurrentWeek(),
            getEndOfCurrentWeek()
        ).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val plannedWorkouts: StateFlow<List<PlannedWorkout>> =
        plannedWorkoutDao.observeAll()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val plannedWorkoutExercisesAll: StateFlow<List<PlannedWorkoutExercise>> =
        plannedWorkoutExerciseDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val actualWorkoutExercisesForThisWeek: StateFlow<List<ActualWorkoutExercise>> =
        actualWorkoutExerciseDao.observeActualWorkoutExercisesForWeek(
            getStartOfCurrentWeek(),
            getEndOfCurrentWeek()
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val actualWorkoutSetsForThisWeek: StateFlow<List<ActualWorkoutSet>> =
        actualWorkoutExercisesForThisWeek.flatMapLatest { exList ->
            if (exList.isEmpty()) flowOf(emptyList())
            else actualWorkoutSetDao.observeSetsForExercises(exList.map { it.uuid })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekProgress: StateFlow<Float> =
        combine(
            plannedWorkouts,
            actualWorkoutsForThisWeek,
            plannedWorkoutExercisesAll,
            actualWorkoutExercisesForThisWeek,
            actualWorkoutSetsForThisWeek
        ) { plannedList, actualList, plannedExAll, actualExAll, setsAll ->

            // On ne compte pas les Rest Day
            val plannedSessions = plannedList.filter { !it.name.equals("Rest Day", ignoreCase = true) }

            var totalPlannedSets = 0
            var totalCappedDone = 0

            plannedSessions.forEach { planned ->
                // trouver l'actual correspondant (comme dans calcDayProgressResult)
                val actual = actualList.firstOrNull { aw ->
                    aw.name == planned.name &&
                            getDayOfWeekFromDate(aw.date).equals(planned.dayOfWeek, ignoreCase = true) &&
                            !aw.pendingDeletion
                } ?: run {
                    // pas d'actual => 0 done, mais on compte quand même le planned dans le total
                    val plannedExercises = plannedExAll
                        .filter { it.plannedWorkoutUUID == planned.uuid && !it.pendingDeletion && !it.ignored }
                    totalPlannedSets += plannedExercises.sumOf { it.sets }.coerceAtLeast(0)
                    return@forEach
                }

                val plannedExercises = plannedExAll
                    .filter { it.plannedWorkoutUUID == planned.uuid && !it.pendingDeletion && !it.ignored }

                val plannedTotalSets = plannedExercises.sumOf { it.sets }.coerceAtLeast(0)
                if (plannedTotalSets <= 0) return@forEach

                totalPlannedSets += plannedTotalSets

                // exerciseUUID -> sets prévus (si doublons, last)
                val plannedSetsByExercise =
                    plannedExercises
                        .groupBy { it.exerciseUUID.trim() }
                        .mapValues { (_, items) -> items.last().sets }

                val actualExercisesForWorkout = actualExAll
                    .filter { it.actualWorkoutUUID == actual.uuid }
                    .filter { !it.pendingDeletion }

                plannedSetsByExercise.forEach { (plannedExUUID, plannedSetsForEx) ->
                    if (plannedSetsForEx <= 0) return@forEach

                    val matchingAweUuids = actualExercisesForWorkout
                        .filter { it.exerciseUUID.trim() == plannedExUUID }
                        .map { it.uuid }
                        .toSet()

                    val doneSetsForEx = setsAll.count { s ->
                        s.actualWorkoutExerciseUUID in matchingAweUuids &&
                                !s.pendingDeletion &&
                                s.status.trim().equals("DONE", ignoreCase = true)
                    }

                    // ✅ cap par exercice : jamais plus que prévu
                    totalCappedDone += minOf(doneSetsForEx, plannedSetsForEx)
                }
            }

            if (totalPlannedSets == 0) 0f
            else (totalCappedDone.toFloat() / totalPlannedSets.toFloat()).coerceIn(0f, 1f)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // Methods
    fun insertPlannedWorkout(plannedWorkout: PlannedWorkout) {
        viewModelScope.launch {
            plannedWorkoutDao.insert(plannedWorkout)
            syncEngine.pushEntityClass(PlannedWorkout::class)
        }
    }

    fun deletePlannedWorkout(plannedWorkout: PlannedWorkout) {
        viewModelScope.launch {
            plannedWorkoutDao.markAsPendingDeletion(plannedWorkout.uuid)
            syncEngine.pushEntityClass(PlannedWorkout::class)
        }
    }

    fun markAllActualWorkoutsAsDone() {
        viewModelScope.launch {
            actualWorkoutsForThisWeek.value.forEach { workout ->
                if (!workout.isDone) {
                    actualWorkoutDao.updateActualWorkout(workout.copy(isDone = true))
                }
            }
        }
    }

    fun markAllActualWorkoutsAsUndone() {
        viewModelScope.launch {
            actualWorkoutsForThisWeek.value.forEach { workout ->
                if (workout.isDone) {
                    actualWorkoutDao.updateActualWorkout(workout.copy(isDone = false))
                }
            }
        }
    }

    fun renamePlannedWorkout(uuid: String, newName: String) {
        viewModelScope.launch {
            plannedWorkoutDao.getPlannedWorkoutByUUID(uuid)?.let {
                plannedWorkoutDao.updatePlannedWorkout(it.copy(name = newName))
                syncEngine.pushEntityClass(PlannedWorkout::class)
            }
        }
    }

    fun toggleDoneForPlannedWorkout(planned: PlannedWorkout) {
        viewModelScope.launch {
            val match = actualWorkoutsForThisWeek.value.firstOrNull { actual ->
                !actual.pendingDeletion &&
                        actual.name.trim().equals(planned.name.trim(), ignoreCase = false) &&
                        getDayOfWeekFromDate(actual.date).equals(planned.dayOfWeek, ignoreCase = true)
            }
            if (match != null) {
                actualWorkoutDao.updateActualWorkout(match.copy(isDone = !match.isDone))
            }
        }
    }

    fun copyPlannedWorkoutToDay(source: PlannedWorkout, targetDay: String) {
        viewModelScope.launch {
            // Option A: remplacer si déjà existant sur targetDay
            val existing = plannedWorkoutDao.getPlannedWorkoutByUserAndDay(source.userId, targetDay)
            if (existing != null) {
                // si tu veux remplacer: delete existing (ou update)
                plannedWorkoutDao.delete(existing)
            }

            val copy = source.copy(
                uuid = UUID.randomUUID().toString(),
                dayOfWeek = targetDay,
                name = "${source.name} (Copy)",
                synced = false
            )

            plannedWorkoutDao.insert(copy)
            syncEngine.pushEntityClass(PlannedWorkout::class)
        }
    }

    fun syncAllPlannedWorkouts() {
        viewModelScope.launch {
            syncEngine.pushEntityClass(PlannedWorkout::class)
        }
    }
}

