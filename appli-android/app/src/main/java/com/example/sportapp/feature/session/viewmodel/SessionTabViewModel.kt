package com.example.sportapp.feature.session.viewmodel

import com.example.sportapp.core.data.model.ActualWorkout
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.local.ActualWorkoutDao
import com.example.sportapp.core.data.local.ActualWorkoutExerciseDao
import com.example.sportapp.core.data.local.ActualWorkoutSetDao
import com.example.sportapp.core.data.local.EquipmentDao
import com.example.sportapp.core.data.local.ExerciseDao
import com.example.sportapp.core.data.local.ExerciseEquipmentDao
import com.example.sportapp.core.data.local.PlannedWorkoutDao
import com.example.sportapp.core.data.local.PlannedWorkoutExerciseDao
import com.example.sportapp.core.data.model.ActualWorkoutExercise
import com.example.sportapp.core.data.model.ActualWorkoutSet
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.data.model.PlannedWorkout
import com.example.sportapp.core.data.model.PlannedWorkoutExercise
import com.example.sportapp.core.sync.SyncManager
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.core.utils.CustomDateUtils
import com.example.sportapp.core.utils.CustomDateUtils.getDayOfWeekFromDate
import com.example.sportapp.core.utils.CustomDateUtils.getTodayDayOfWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID


@HiltViewModel
class SessionTabViewModel @Inject constructor(
    private val actualWorkoutDao: ActualWorkoutDao,
    private val actualWorkoutExerciseDao: ActualWorkoutExerciseDao,
    private val actualWorkoutSetDao: ActualWorkoutSetDao,
    private val plannedWorkoutDao: PlannedWorkoutDao,
    private val plannedWorkoutExerciseDao: PlannedWorkoutExerciseDao,
    private val exerciseDao: ExerciseDao,
    private val equipmentDao: EquipmentDao,
    private val exerciseEquipmentDao: ExerciseEquipmentDao,
    private val syncEngine: SyncEngine,
    private val syncManager: SyncManager,
) : ViewModel() {

    val sessionUUID = MutableStateFlow<String?>(null)

    fun setSessionUUID(uuid: String) {
        sessionUUID.value = uuid
    }

    // 🔥 Flows directs
    val actualWorkout = sessionUUID.filterNotNull().flatMapLatest { uuid ->
        actualWorkoutDao.observeByUUID(uuid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allExercises = exerciseDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allEquipments = equipmentDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExerciseEquipments = exerciseEquipmentDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allActualWorkoutExercises = sessionUUID.filterNotNull().flatMapLatest { uuid ->
        actualWorkoutExerciseDao.observeByWorkout(uuid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // sets dépend des exercices → combine
    val allActualWorkoutSets: StateFlow<List<ActualWorkoutSet>> =
        allActualWorkoutExercises.flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptyList())
            else actualWorkoutSetDao.observeSetsForExercises(list.map { it.uuid })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Planned workout du jour
    val plannedWorkout: StateFlow<PlannedWorkout?> =
        plannedWorkoutDao.observeWorkoutForToday(getTodayDayOfWeek()) // ton DAO doit exposer ça en Flow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val plannedWorkoutExercises: StateFlow<List<PlannedWorkoutExercise>> =
        plannedWorkout.filterNotNull().flatMapLatest { pw ->
            plannedWorkoutExerciseDao.observeByPlannedWorkoutUUID(pw.uuid)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val plannedWarmUp = plannedWorkoutExercises.map { it.filter { ex -> ex.phase == "WARMUP" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val plannedTraining = plannedWorkoutExercises.map { it.filter { ex -> ex.phase == "TRAINING" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val plannedPostTraining = plannedWorkoutExercises.map { it.filter { ex -> ex.phase == "POST_TRAINING" || ex.phase == "FINISHER" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Convenience : titre + sync
    val actualWorkoutTitle = actualWorkout.map { it?.name ?: "Unknown workout" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Unknown workout")

    val actualWorkoutSynced = actualWorkout.map { it?.synced == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val actualWorkoutIsDone = actualWorkout.map { it?.isDone == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun markActualWorkoutExerciseForDeletion(actualWorkoutExercise: ActualWorkoutExercise) {
        viewModelScope.launch {
            actualWorkoutSetDao.markSetsAsPendingDeletionWithExerciseUUID(actualWorkoutExercise.uuid)
            actualWorkoutExerciseDao.markAsPendingDeletion(actualWorkoutExercise.uuid)

            actualWorkoutSetDao.markAsUnsynced(actualWorkoutExercise.uuid)
            actualWorkoutExerciseDao.markAsUnsynced(actualWorkoutExercise.uuid)

            reorderWorkoutExercises(actualWorkoutExercise.actualWorkoutUUID)

            tryIgnorePlannedExercise(actualWorkoutExercise)         // ❓ Essai de retrouver le planned pour marquer comme ignoré

            val plannedWorkout = plannedWorkoutDao.getPlannedWorkoutByUserAndDay(
                actualWorkoutDao.getActualWorkoutByUUID(actualWorkoutExercise.actualWorkoutUUID)?.userId ?: -1,
                getDayOfWeekFromDate(
                    actualWorkoutDao.getActualWorkoutByUUID(actualWorkoutExercise.actualWorkoutUUID)?.date ?: ""
                )
            )

            val plannedBefore = plannedWorkoutExerciseDao.getPlannedWorkoutExercisesByPlannedWorkoutUUID(
                plannedWorkout?.uuid ?: ""
            )

            //trouver l'exercice planifié correspondant
            val plannedExercise = plannedWorkoutExerciseDao
                .getPlannedWorkoutExerciseByExerciseAndWorkout(
                    exerciseUUID = actualWorkoutExercise.exerciseUUID,
                    plannedWorkoutUUID = plannedWorkout?.uuid ?: ""
                )
            if (plannedExercise != null) {
                plannedWorkoutExerciseDao.markAsIgnored(plannedExercise.uuid)
            }

            syncEngine.pushEntityClasses(ActualWorkoutExercise::class, PlannedWorkoutExercise::class, ActualWorkoutSet::class)

            updateActualWorkoutSyncState(actualWorkoutExercise.actualWorkoutUUID)
        }
    }

    fun markActualWorkoutForDeletion(workoutUUID: String, onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val aw = actualWorkoutDao.getActualWorkoutByUUID(workoutUUID) ?: run {
                    onDone?.invoke()
                    return@launch
                }

                // 1) Récupérer tous les exercices du workout
                val exercises = actualWorkoutExerciseDao
                    .getExercisesForWorkoutOnce(workoutUUID)
                    .filter { !it.pendingDeletion }

                // 2) Pour chaque exercice -> sets pending deletion + exercice pending deletion + unsynced
                exercises.forEach { ex ->
                    actualWorkoutSetDao.markSetsAsPendingDeletionWithExerciseUUID(ex.uuid)
                    actualWorkoutSetDao.markAsUnsynced(ex.uuid)

                    actualWorkoutExerciseDao.markAsPendingDeletion(ex.uuid)
                    actualWorkoutExerciseDao.markAsUnsynced(ex.uuid)
                }

                // 3) Workout pending deletion + unsynced
                actualWorkoutDao.updateActualWorkout(
                    aw.copy(
                        pendingDeletion = true,
                        synced = false,
                        updatedAt = CustomDateUtils.getNowISO8601()
                    )
                )

                // 4) Sync (workout + exercises + sets)
                syncEngine.pushEntityClass(ActualWorkout::class)
                syncEngine.pushEntityClasses(ActualWorkoutExercise::class, PlannedWorkoutExercise::class, ActualWorkoutSet::class)

            } catch (e: Exception) {
                Log.e("SessionTabViewModel", "markActualWorkoutForDeletion failed: ${e.message}", e)
            } finally {
                onDone?.invoke()
            }
        }
    }


    private suspend fun tryIgnorePlannedExercise(actualWorkoutExercise: ActualWorkoutExercise) {
        val actualWorkout = actualWorkoutDao.getActualWorkoutByUUID(actualWorkoutExercise.actualWorkoutUUID) ?: return
        val dayOfWeek = getDayOfWeekFromDate(actualWorkout.date)
        val plannedWorkout = plannedWorkoutDao.getPlannedWorkoutByUserAndDay(
            userId = actualWorkout.userId,
            dayOfWeek = dayOfWeek
        ) ?: return

        val plannedExercise = plannedWorkoutExerciseDao
            .getPlannedWorkoutExerciseByExerciseAndWorkout(
                exerciseUUID = actualWorkoutExercise.exerciseUUID,
                plannedWorkoutUUID = plannedWorkout.uuid
            )

        if (plannedExercise != null) {
            plannedWorkoutExerciseDao.markAsIgnored(plannedExercise.uuid)
            plannedWorkoutExerciseDao.markAsUnsynced(plannedExercise.uuid)
        }
    }

    private suspend fun tryUnignorePlannedExercise(
        actualWorkoutUUID: String,
        exerciseUUID: String
    ) {
        val actualWorkout = actualWorkoutDao.getActualWorkoutByUUID(actualWorkoutUUID) ?: return
        val dayOfWeek = getDayOfWeekFromDate(actualWorkout.date)

        val plannedWorkout = plannedWorkoutDao.getPlannedWorkoutByUserAndDay(
            userId = actualWorkout.userId,
            dayOfWeek = dayOfWeek
        ) ?: return

        val plannedExercise = plannedWorkoutExerciseDao
            .getPlannedWorkoutExerciseByExerciseAndWorkout(
                exerciseUUID = exerciseUUID,
                plannedWorkoutUUID = plannedWorkout.uuid
            )

        if (plannedExercise != null && plannedExercise.ignored) {
            plannedWorkoutExerciseDao.markAsNotIgnored(
                uuid = plannedExercise.uuid
            )
        }
    }


    suspend fun reorderWorkoutExercises(actualWorkoutUUID: String) {
        val exercises = actualWorkoutExerciseDao
            .getExercisesForWorkoutOnce(actualWorkoutUUID)
            .filter { !it.pendingDeletion }
            .sortedBy { it.order }

        exercises.forEachIndexed { index, exercise ->
            val updated = exercise.copy(order = index + 1)
            actualWorkoutExerciseDao.update(updated)
        }
    }

    fun addExerciseToPhase(
        exercise: Exercise,
        phase: String,
        workoutUUID: String
    ) {
        viewModelScope.launch {
            val alreadyExists = actualWorkoutExerciseDao
                .getExercisesForWorkoutOnce(workoutUUID)
                .any { it.exerciseUUID == exercise.uuid && !it.pendingDeletion }

            if (alreadyExists) return@launch

            val sets = exercise.recommendedSets ?: 3
            val reps = exercise.recommendedReps
                ?.split("-")
                ?.firstOrNull()
                ?.toIntOrNull()
                ?: 10

            val existing = actualWorkoutExerciseDao.getExercisesForWorkoutOnce(workoutUUID)

            // 1. Trouver le dernier ordre de la phase ciblée
            val maxOrderInPhase = existing
                .filter { it.phase.equals(phase, ignoreCase = true) }
                .maxOfOrNull { it.order } ?: 0

            val insertOrder = maxOrderInPhase + 1

            // 2. Décaler tous ceux dont l'ordre est >= insertOrder
            val toShift = existing
                .filter { it.actualWorkoutUUID == workoutUUID && it.order >= insertOrder }
            toShift.forEach {
                val updated = it.copy(order = it.order + 1)
                actualWorkoutExerciseDao.update(updated)
            }

            // 3. Insérer le nouveau
            val newActualWorkoutExerciseUUID = UUID.randomUUID().toString()
            val newActualWorkoutExercise = ActualWorkoutExercise(
                uuid = newActualWorkoutExerciseUUID,
                actualWorkoutUUID = workoutUUID,
                exerciseUUID = exercise.uuid,
                phase = phase,
                status = "NOT_STARTED",
                order = insertOrder,
                addedManually = true,
                synced = false,
                sets = exercise.recommendedSets?: 3,
                reps = reps.toString(),
                pendingDeletion = false
            )
            val insertedId = actualWorkoutExerciseDao.insert(newActualWorkoutExercise)

            // ✅ si l'exo était "ignored" côté planned => on le réactive
            tryUnignorePlannedExercise(
                actualWorkoutUUID = workoutUUID,
                exerciseUUID = exercise.uuid
            )

            // 4. Sets associés
            val setsToInsert = (1..sets).map { index ->
                ActualWorkoutSet(
                    uuid = UUID.randomUUID().toString(),
                    actualWorkoutExerciseUUID = newActualWorkoutExerciseUUID,
                    reps = 0,
                    weight = 0f,
                    setOrder = index, // ⚡ ici on commence à 1
                    status = "NOT_STARTED",
                    synced = false,
                    pendingDeletion = false
                )
            }
            actualWorkoutSetDao.insertAll(setsToInsert)

            try {
                syncEngine.pushEntityClasses(ActualWorkoutExercise::class, PlannedWorkoutExercise::class, ActualWorkoutSet::class)
            } catch (e: Exception) {
                Log.e("SessionTabViewModel", "Sync failed: ${e.message}")
            }

            // 5. Update sync
            updateActualWorkoutSyncState(workoutUUID)
        }
    }

    fun toggleActualWorkoutDone() {
        viewModelScope.launch {
            val aw = actualWorkout.value ?: return@launch
            actualWorkoutDao.updateActualWorkout(aw.copy(isDone = !aw.isDone))
            syncEngine.pushEntityClass(ActualWorkout::class)
        }
    }

    fun syncAll() {
        viewModelScope.launch {
            try {
                syncEngine.pushEntityClass(ActualWorkout::class)
                syncEngine.pushEntityClasses(ActualWorkoutExercise::class, PlannedWorkoutExercise::class, ActualWorkoutSet::class)
            } catch (e: Exception) {
                Log.e("SessionTabViewModel", "Sync all failed: ${e.message}")
            }
        }
    }

    private suspend fun updateActualWorkoutSyncState(workoutUUID: String) {
        val exercises = actualWorkoutExerciseDao.getExercisesForWorkoutOnce(workoutUUID)
        val hasUnsynced = exercises.any { !it.synced }
        actualWorkoutDao.setSyncedStatus(workoutUUID, !hasUnsynced)
    }

    fun getTodaySessionUUID(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val today = CustomDateUtils.getTodayIsoDay()
                val session = actualWorkoutDao.getActualWorkoutByDay(today)
                onResult(session?.uuid)
            } catch (e: Exception) {
                //Log.e("SessionTab", "❌ Error in getTodaySessionUUID", e)
                onResult(null)
            }
        }
    }

    fun renameActualWorkout(workoutUUID: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            try {
                val current = actualWorkoutDao.getActualWorkoutByUUID(workoutUUID)
                if (current == null) {
                    Log.e("SessionTabViewModel", "renameActualWorkout: workout not found uuid=$workoutUUID")
                    return@launch
                }

                // ✅ met synced=false + updatedAt automatiquement via updateActualWorkout()
                actualWorkoutDao.updateActualWorkout(
                    current.copy(name = trimmed)
                )

                // Optionnel : sync si tu veux pousser tout de suite
                syncEngine.pushEntityClass(ActualWorkout::class)

            } catch (e: Exception) {
                Log.e("SessionTabViewModel", "renameActualWorkout failed: ${e.message}", e)
            }
        }
    }



}
