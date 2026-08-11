package com.example.sportapp.feature.session.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.local.ActualWorkoutDao
import com.example.sportapp.core.data.local.ActualWorkoutExerciseDao
import com.example.sportapp.core.data.local.ActualWorkoutSetDao
import com.example.sportapp.core.data.local.ExerciseDao
import com.example.sportapp.core.data.model.ActualWorkout
import com.example.sportapp.core.data.model.ActualWorkoutExercise
import com.example.sportapp.core.data.model.ActualWorkoutSet
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.data.repository.MuscleGoalsManager
import com.example.sportapp.core.sync.SyncManager
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.core.utils.CustomDateUtils.getCurrentWeekISO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SessionExerciseViewModel @Inject constructor(
    private val actualWorkoutDao: ActualWorkoutDao,
    private val actualWorkoutExerciseDao: ActualWorkoutExerciseDao,
    private val actualWorkoutSetDao: ActualWorkoutSetDao,
    private val exerciseDao: ExerciseDao,
    private val muscleGoalsManager: MuscleGoalsManager,
    private val syncEngine: SyncEngine,
    private val syncManager: SyncManager,
    private val onboardingRepo: com.example.sportapp.feature.onboarding.data.OnboardingRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Unité poids choisie par l'user. Affecte le SetRow display + l'edit dialog. */
    val weightUnit: StateFlow<com.example.sportapp.feature.onboarding.data.WeightUnit> =
        onboardingRepo.preferences
            .map { it.weightUnit }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                com.example.sportapp.feature.onboarding.data.WeightUnit.KG
            )

    private val currentWeekISO: String = getCurrentWeekISO()

    // 👉 récupère l’UUID direct depuis la navigation
    private val exerciseUUID: String = savedStateHandle["actualWorkoutExerciseUUID"]
        ?: error("actualWorkoutExerciseUUID is required")

    val actualWorkoutExercise: StateFlow<ActualWorkoutExercise?> =
        actualWorkoutExerciseDao.observeByUUID(exerciseUUID)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val actualWorkoutSets: StateFlow<List<ActualWorkoutSet>> =
        actualWorkoutSetDao.observeByExerciseUUID(exerciseUUID)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exercise: StateFlow<Exercise?> =
        actualWorkoutExercise.filterNotNull().flatMapLatest { awe ->
            exerciseDao.observeByUUID(awe.exerciseUUID)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val actualWorkout: StateFlow<ActualWorkout?> =
        actualWorkoutExercise.filterNotNull().flatMapLatest { awe ->
            actualWorkoutDao.observeByUUID(awe.actualWorkoutUUID)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateExerciseDescription(uuid: String, newDescription: String) {
        viewModelScope.launch {
            exerciseDao.updateDescription(uuid, newDescription)
            exerciseDao.markAsUnsynced(uuid)
            syncEngine.pushEntityClass(Exercise::class)
        }
    }

    fun updateReps(uuid: String, newReps: Int) {
        viewModelScope.launch {
            actualWorkoutSetDao.updateReps(uuid, newReps)

            val minTarget = minTargetFromRecommended(actualWorkoutExercise.value?.reps)
            val newStatus = when {
                newReps <= 0 -> "NOT_STARTED"
                newReps >= minTarget -> "DONE"
                else -> "IN_PROGRESS"
            }
            val current = actualWorkoutSetDao.getByUUID(uuid)
            if (current != null && !current.status.equals(newStatus, ignoreCase = true)) {
                actualWorkoutSetDao.updateStatus(uuid, newStatus)
            }

            actualWorkoutSetDao.markAsUnsynced(uuid)
            actualWorkoutExercise.value?.uuid?.let { actualWorkoutExerciseDao.markAsUnsynced(it) }
            actualWorkout.value?.uuid?.let { actualWorkoutDao.markAsUnsynced(it) }

            syncEngine.pushEntityClasses(ActualWorkoutSet::class, ActualWorkoutExercise::class, ActualWorkout::class)
        }
    }

    fun updateWeight(uuid: String, newWeight: Float) {
        viewModelScope.launch {
            actualWorkoutSetDao.updateWeight(uuid, newWeight)
            actualWorkoutSetDao.markAsUnsynced(uuid)

            actualWorkoutExercise.value?.uuid?.let { actualWorkoutExerciseDao.markAsUnsynced(it) }
            actualWorkout.value?.uuid?.let { actualWorkoutDao.markAsUnsynced(it) }

            syncEngine.pushEntityClasses(ActualWorkoutSet::class, ActualWorkoutExercise::class, ActualWorkout::class)
        }
    }

    fun updateNotes(uuid: String, newNote: String) {
        viewModelScope.launch {
            actualWorkoutSetDao.updateNotes(uuid, newNote)
            actualWorkoutSetDao.markAsUnsynced(uuid)

            actualWorkoutExercise.value?.uuid?.let { actualWorkoutExerciseDao.markAsUnsynced(it) }
            actualWorkout.value?.uuid?.let { actualWorkoutDao.markAsUnsynced(it) }

            syncEngine.pushEntityClasses(ActualWorkoutSet::class, ActualWorkoutExercise::class, ActualWorkout::class)
        }
    }

    fun updateActualWorkoutSetStatus(uuid: String, newStatus: String) {
        viewModelScope.launch {
            actualWorkoutSetDao.updateStatus(uuid, newStatus)
            actualWorkoutSetDao.markAsUnsynced(uuid)

            actualWorkoutExercise.value?.uuid?.let { actualWorkoutExerciseDao.markAsUnsynced(it) }
            actualWorkout.value?.uuid?.let { actualWorkoutDao.markAsUnsynced(it) }

            syncEngine.pushEntityClasses(ActualWorkoutSet::class, ActualWorkoutExercise::class, ActualWorkout::class)
            muscleGoalsManager.updateMuscleGoalsForWeek(currentWeekISO)
        }
    }


    fun insertNewSetAtEnd() {
        viewModelScope.launch {
            val exerciseUUID = actualWorkoutExercise.value?.uuid ?: return@launch

            val existingSets = actualWorkoutSetDao.getByActualWorkoutExerciseUUID(exerciseUUID)
            val lastOrder = existingSets.maxOfOrNull { it.setOrder } ?: -1

            val newSet = ActualWorkoutSet(
                uuid = UUID.randomUUID().toString(),
                actualWorkoutExerciseUUID = exerciseUUID,
                reps = 0,
                weight = 0f,
                status = "NOT_STARTED",
                isDropset = false,
                setOrder = lastOrder + 1,
                notes = "",
                recommendation = null,
                synced = false,
                pendingDeletion = false
            )

            actualWorkoutSetDao.insert(newSet)
            actualWorkoutExercise.value?.uuid?.let { actualWorkoutExerciseDao.markAsUnsynced(it) }
            actualWorkout.value?.uuid?.let { actualWorkoutDao.markAsUnsynced(it) }
            syncEngine.pushEntityClasses(ActualWorkoutSet::class, ActualWorkoutExercise::class, ActualWorkout::class)
        }
    }

    fun insertDropSet(afterOrder: Int, baseSet: ActualWorkoutSet) {
        viewModelScope.launch {
            val exerciseUUID = actualWorkoutExercise.value?.uuid ?: return@launch

            val sets = actualWorkoutSetDao.getByActualWorkoutExerciseUUID(exerciseUUID)
                .filter { !it.pendingDeletion }
                .sortedBy { it.setOrder }

            // Trouver l’index du baseSet
            val startIndex = sets.indexOfFirst { it.setOrder == afterOrder }
            if (startIndex == -1) return@launch

            // Cherche le dernier dropset consécutif après baseSet
            var insertAfterOrder = sets[startIndex].setOrder
            for (i in (startIndex + 1) until sets.size) {
                if (sets[i].isDropset) {
                    insertAfterOrder = sets[i].setOrder
                } else {
                    break
                }
            }

            // Décale les sets suivants
            sets.filter { it.setOrder > insertAfterOrder }.forEach {
                actualWorkoutSetDao.updateOrder(it.uuid, it.setOrder + 1)
            }

            // Crée le nouveau dropset
            val newSet = baseSet.copy(
                uuid = UUID.randomUUID().toString(),
                reps = 0,
                weight = 0f,
                status = "NOT_STARTED",
                isDropset = true,
                setOrder = insertAfterOrder + 1,
                notes = "",
                recommendation = null,
                synced = false,
                pendingDeletion = false
            )

            actualWorkoutSetDao.insert(newSet)
            reindexAllSets(exerciseUUID)
            actualWorkoutExercise.value?.uuid?.let { actualWorkoutExerciseDao.markAsUnsynced(it) }
            actualWorkout.value?.uuid?.let { actualWorkoutDao.markAsUnsynced(it) }
            syncEngine.pushEntityClasses(ActualWorkoutSet::class, ActualWorkoutExercise::class, ActualWorkout::class)
        }
    }

    fun insertBonusSet(baseSet: ActualWorkoutSet) {
        viewModelScope.launch {
            val exerciseUUID = actualWorkoutExercise.value?.uuid ?: return@launch

            val sets = actualWorkoutSetDao.getByActualWorkoutExerciseUUID(exerciseUUID)
                .filter { !it.pendingDeletion }
                .sortedBy { it.setOrder }

            // 👉 Trouve le bloc visuel complet : set de base + ses dropsets
            val startIndex = sets.indexOfFirst { it.uuid == baseSet.uuid }
            if (startIndex == -1) return@launch

            var insertIndex = startIndex
            for (i in (startIndex + 1) until sets.size) {
                if (sets[i].isDropset) {
                    insertIndex = i
                } else {
                    break
                }
            }

            val insertAfterOrder = sets[insertIndex].setOrder

            // Décale les suivants
            sets.filter { it.setOrder >= insertAfterOrder + 1 }.forEach {
                actualWorkoutSetDao.updateOrder(it.uuid, it.setOrder + 1)
            }

            val newSet = baseSet.copy(
                uuid = UUID.randomUUID().toString(),
                reps = 0,
                weight = 0f,
                status = "NOT_STARTED",
                isDropset = false,
                setOrder = insertAfterOrder + 1,
                notes = "",
                recommendation = null,
                synced = false,
                pendingDeletion = false
            )

            actualWorkoutSetDao.insert(newSet)
            reindexAllSets(exerciseUUID)

            actualWorkoutExercise.value?.uuid?.let { actualWorkoutExerciseDao.markAsUnsynced(it) }
            actualWorkout.value?.uuid?.let { actualWorkoutDao.markAsUnsynced(it) }

            syncEngine.pushEntityClasses(ActualWorkoutSet::class, ActualWorkoutExercise::class, ActualWorkout::class)
        }
    }

    private suspend fun reindexAllSets(actualWorkoutExerciseUUID: String) {
        val sets = actualWorkoutSetDao.getByActualWorkoutExerciseUUID(actualWorkoutExerciseUUID)
            .sortedBy { it.setOrder }

        sets.forEachIndexed { index, set ->
            if (set.setOrder != index + 1) {
                actualWorkoutSetDao.updateOrder(set.uuid, index + 1)
            }
        }
    }

    fun markSetAsPendingDeletion(uuid: String) {
        viewModelScope.launch {
            val set = actualWorkoutSetDao.getByUUID(uuid) ?: return@launch
            val exerciseUUID = set.actualWorkoutExerciseUUID

            val allSets = actualWorkoutSetDao
                .getByActualWorkoutExerciseUUID(exerciseUUID)
                .sortedBy { it.setOrder }

            val toDelete = mutableListOf<String>()
            toDelete.add(uuid)

            if (!set.isDropset) {
                val baseOrder = set.setOrder
                for (following in allSets) {
                    if (following.setOrder > baseOrder && following.isDropset && !following.pendingDeletion) {
                        toDelete.add(following.uuid)
                    } else if (following.setOrder > baseOrder && !following.isDropset) {
                        break
                    }
                }
            }

            toDelete.forEach { id ->
                actualWorkoutSetDao.markAsPendingDeletion(id)
                actualWorkoutSetDao.markAsUnsynced(id)
            }
            actualWorkoutExercise.value?.uuid?.let { actualWorkoutExerciseDao.markAsUnsynced(it) }
            actualWorkout.value?.uuid?.let { actualWorkoutDao.markAsUnsynced(it) }

            syncEngine.pushEntityClasses(ActualWorkoutSet::class, ActualWorkoutExercise::class, ActualWorkout::class)

            reindexAllSets(exerciseUUID)

            muscleGoalsManager.updateMuscleGoalsForWeek(currentWeekISO)

            syncEngine.pushEntityClass(ActualWorkoutSet::class)
        }
    }

    fun syncActualWorkoutSets(onSynced: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            syncEngine.pushEntityClasses(ActualWorkoutSet::class, ActualWorkoutExercise::class, ActualWorkout::class)

            val exerciseUUID = actualWorkoutExercise.value?.uuid

            val allSynced = exerciseUUID?.let {
                val sets = actualWorkoutSetDao.getByActualWorkoutExerciseUUID(it)
                sets.all { it.synced }
            } == true

            onSynced(allSynced)
        }
    }

    fun syncActualWorkoutExercise() {
        viewModelScope.launch {
            syncEngine.pushEntityClass(ActualWorkoutExercise::class)
        }
    }

    // Useful functions
    private fun minTargetFromRecommended(recommended: String?): Int {
        if (recommended.isNullOrBlank()) return 0
        val s = recommended.trim()

        return when {
            s.endsWith("+") -> s.removeSuffix("+").trim().toIntOrNull() ?: 0
            s.contains("-") -> s.split("-").firstOrNull()?.trim()?.toIntOrNull() ?: 0
            else -> s.toIntOrNull() ?: 0
        }
    }


}
