package com.example.sportapp.feature.planning.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.local.EquipmentDao
import com.example.sportapp.core.data.local.PlannedWorkoutDao
import com.example.sportapp.core.data.local.PlannedWorkoutExerciseDao
import com.example.sportapp.core.data.local.ExerciseDao
import com.example.sportapp.core.data.local.ExerciseEquipmentDao
import com.example.sportapp.core.data.model.Equipment
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.data.model.ExerciseEquipment
import com.example.sportapp.core.data.model.PlannedWorkout
import com.example.sportapp.core.data.model.PlannedWorkoutExercise
import com.example.sportapp.core.sync.SyncManager
import com.example.sportapp.core.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.collections.filter
import kotlin.collections.maxOfOrNull

@HiltViewModel
class PlannedWorkoutViewModel @Inject constructor(
    private val plannedWorkoutDao: PlannedWorkoutDao,
    private val plannedWorkoutExerciseDao: PlannedWorkoutExerciseDao,
    private val exerciseDao: ExerciseDao,
    private val equipmentDao: EquipmentDao,
    private val exerciseEquipmentDao: ExerciseEquipmentDao,

    private val syncEngine: SyncEngine,
    private val syncManager: SyncManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val plannedWorkoutUUID: String =
        checkNotNull(savedStateHandle["plannedWorkoutUUID"]) {
            "plannedWorkoutUUID is required in SavedStateHandle"
        }

    // Flows exposés directement
    val plannedWorkout: StateFlow<PlannedWorkout?> =
        plannedWorkoutDao.observeByUUID(plannedWorkoutUUID)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allPlannedWorkoutExercises: StateFlow<List<PlannedWorkoutExercise>> =
        plannedWorkoutExerciseDao.observeByPlannedWorkoutUUID(plannedWorkoutUUID)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExercises: StateFlow<List<Exercise>> =
        exerciseDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allEquipments: StateFlow<List<Equipment>> =
        equipmentDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExerciseEquipments: StateFlow<List<ExerciseEquipment>> =
        exerciseEquipmentDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updatePlannedWorkoutExerciseStatus(plannedWorkoutExerciseUUID: String, newStatus: String) {
        viewModelScope.launch {
            try {
                plannedWorkoutExerciseDao.updateStatus(plannedWorkoutExerciseUUID, newStatus)
                syncEngine.pushEntityClass(PlannedWorkoutExercise::class)
            } catch (e: Exception) {
                Log.e("PlannedWorkoutViewModel", "Error updating planned workout exercise status", e)
            }
        }
    }

    fun addPlannedExerciseToPhase(
        exercise: Exercise,
        phase: String,
    ) {
        viewModelScope.launch {
            val existingExercises = allPlannedWorkoutExercises.value

            val maxOrderInPhase = existingExercises
                .filter { it.phase.equals(phase, ignoreCase = true) }
                .maxOfOrNull { it.order } ?: 0

            val newOrder = maxOrderInPhase + 1

            val plannedWorkoutExercise = PlannedWorkoutExercise(
                uuid = UUID.randomUUID().toString(),
                plannedWorkoutUUID = plannedWorkoutUUID,
                exerciseUUID = exercise.uuid,
                phase = phase,
                status = "PLANNED",     // "PLANNED", "DONE", "NOT_STARTED", "SKIPPED"
                order = newOrder,
                sets = exercise.recommendedSets ?: 3,
                reps = (exercise.recommendedReps?.split("-")?.firstOrNull()?.toIntOrNull()
                    ?: 10).toString(),
                pendingDeletion = false,
                ignored = false
            )

            plannedWorkoutExerciseDao.insert(plannedWorkoutExercise)
            syncEngine.pushEntityClass(PlannedWorkoutExercise::class)
        }
    }

    fun markPlannedWorkoutExerciseForDeletion(
        plannedWorkoutExercise: PlannedWorkoutExercise,
    ) {
        viewModelScope.launch {
            plannedWorkoutExerciseDao.markAsPendingDeletion(plannedWorkoutExercise.uuid)
            plannedWorkoutExerciseDao.markAsUnsynced(plannedWorkoutExercise.uuid)

            syncEngine.pushEntityClass(PlannedWorkoutExercise::class)
        }
    }

    fun syncPlannedWorkoutExerciseAndPlannedWorkoutExercise(
    ) {
        viewModelScope.launch {
            syncEngine.pushEntityClass(PlannedWorkoutExercise::class)
        }
    }
}
