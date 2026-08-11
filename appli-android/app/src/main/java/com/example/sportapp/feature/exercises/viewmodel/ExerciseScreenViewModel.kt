package com.example.sportapp.feature.exercises.viewmodel

import com.example.sportapp.core.data.model.ExerciseEquipment
import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.app.SnackbarAction
import com.example.sportapp.app.SnackbarController
import android.content.Context
import com.example.sportapp.R
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.sportapp.core.data.local.ActualWorkoutExerciseDao
import com.example.sportapp.core.data.local.EquipmentDao
import com.example.sportapp.core.data.local.ExerciseDao
import com.example.sportapp.core.data.local.ExerciseEquipmentDao
import com.example.sportapp.core.data.local.ExerciseMuscleDao
import com.example.sportapp.core.data.local.MuscleDao
import com.example.sportapp.core.data.model.projections.ActualWorkoutExerciseWithWorkoutDateAndSets
import com.example.sportapp.core.data.model.Equipment
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.data.model.ExerciseMuscle
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.core.sync.SyncManager
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject


@HiltViewModel
class ExerciseScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exerciseDao: ExerciseDao,
    private val muscleDao: MuscleDao,
    private val equipmentDao: EquipmentDao,
    private val actualWorkoutExerciseDao: ActualWorkoutExerciseDao,

    private val exerciseMuscleDao: ExerciseMuscleDao,
    private val exerciseEquipmentDao: ExerciseEquipmentDao,

    private val syncEngine: SyncEngine,
    private val syncManager: SyncManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    internal var currentSnackbarId: String? = null

    private var lastLoadedExerciseName: String? = null

    // Exercice sélectionné
    val exerciseUUID: String = checkNotNull(savedStateHandle["exerciseUUID"])
    val exercise: StateFlow<Exercise?> =
        exerciseDao.observeByUUID(exerciseUUID)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)


    // Tous pour duplication check
    val allExercises: StateFlow<List<Exercise>> =
        exerciseDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMuscles: StateFlow<List<Muscle>> =
        muscleDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allEquipments: StateFlow<List<Equipment>> =
        equipmentDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Relations dynamiques
    fun musclesByExercise(exerciseUUID: String): StateFlow<List<Muscle>> =
        exerciseMuscleDao.observeByExerciseUUID(exerciseUUID)
            .map { relations -> relations.mapNotNull { muscleDao.getMuscleByUUID(it.muscleUUID) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun equipmentsByExercise(exerciseUUID: String): StateFlow<List<Equipment>> =
        exerciseEquipmentDao.observeByExerciseUUID(exerciseUUID)
            .map { relations -> relations.mapNotNull { equipmentDao.getEquipmentByUUID(it.equipmentUUID) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun lastSessions(exerciseUUID: String): StateFlow<List<ActualWorkoutExerciseWithWorkoutDateAndSets>> =
        actualWorkoutExerciseDao.observeLast3SessionsForExercise(exerciseUUID)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateExercise(updatedExercise: Exercise, muscles: List<Muscle>, equipments: List<Equipment>) {
        viewModelScope.launch {
            try {
                exerciseDao.updateExercise(updatedExercise)
                exerciseDao.markAsUnsynced(updatedExercise.uuid)

                // 🔄 Relations Muscle
                val existingMuscleRelations = exerciseMuscleDao.getExerciseMusclesByExerciseUUID(updatedExercise.uuid)
                val existingMuscleUUIDs = existingMuscleRelations.map { it.muscleUUID }.toSet()
                val newMuscleUUIDs = muscles.map { it.uuid }.toSet()

                existingMuscleRelations
                    .filter { it.muscleUUID !in newMuscleUUIDs }
                    .forEach {
                        exerciseMuscleDao.markAsPendingDeletion(it.uuid)
                        exerciseMuscleDao.markAsUnsynced(it.uuid)
                    }

                val toAddMuscles = newMuscleUUIDs - existingMuscleUUIDs
                val newMuscleRelations = muscles.filter { it.uuid in toAddMuscles }.map {
                    ExerciseMuscle(
                        uuid = UUID.randomUUID().toString(),
                        exerciseUUID = updatedExercise.uuid,
                        muscleUUID = it.uuid,
                        coefficient = 1.0f,
                        synced = false,
                        pendingDeletion = false
                    )
                }
                exerciseMuscleDao.insertAll(newMuscleRelations)

                // 🔄 Relations Equipment
                val existingEquipmentRelations = exerciseEquipmentDao.getEquipmentsByExerciseUUID(updatedExercise.uuid)
                val existingEquipmentUUIDs = existingEquipmentRelations.map { it.equipmentUUID }.toSet()
                val newEquipmentUUIDs = equipments.map { it.uuid }.toSet()

                existingEquipmentRelations
                    .filter { it.equipmentUUID !in newEquipmentUUIDs }
                    .forEach {
                        exerciseEquipmentDao.markAsPendingDeletion(it.uuid)
                        exerciseEquipmentDao.markAsUnsynced(it.uuid)
                    }

                val toAddEquipments = newEquipmentUUIDs - existingEquipmentUUIDs
                val newEquipmentRelations = equipments.filter { it.uuid in toAddEquipments }.map {
                    com.example.sportapp.core.data.model.ExerciseEquipment(
                        uuid = UUID.randomUUID().toString(),
                        exerciseUUID = updatedExercise.uuid,
                        equipmentUUID = it.uuid,
                        synced = false,
                        pendingDeletion = false
                    )
                }
                exerciseEquipmentDao.insertAll(newEquipmentRelations)

                // ✅ juste sync, pas de reload
                syncEngine.pushEntityClass(Exercise::class)
                syncEngine.pushEntityClass(ExerciseMuscle::class)
                syncEngine.pushEntityClass(ExerciseEquipment::class)

            } catch (e: Exception) {
                Log.e("ExerciseListScreenViewModel", "❌ Error updating exercise:", e)
            }
        }
    }


    fun toggleFavorite(exercise: Exercise) {
        viewModelScope.launch {
            try {
                exerciseDao.toggleFavorite(exercise.uuid)
                exerciseDao.markAsUnsynced(exercise.uuid)
                syncEngine.pushEntityClass(Exercise::class)
            } catch (e: Exception) {
                Log.e("ExerciseViewModel", "❌ Error toggling favorite", e)
            }
        }
    }


    fun markExerciseForDeletion(exercise: Exercise) {
        viewModelScope.launch {
            try {
                exerciseDao.markAsPendingDeletion(exercise.uuid)
                exerciseDao.markAsUnsynced(exercise.uuid)
                syncEngine.pushEntityClass(Exercise::class)
            } catch (e: Exception) {
                Log.e("ExerciseViewModel", "❌ Error marking exercise as pending deletion", e)
            }
        }
    }


    fun onShowDuplicateNameSnackbar(onRetry: () -> Unit) {
        var snackbarId: String = ""
        snackbarId = showSnackbar(
            message = context.getString(R.string.vm_exercise_name_exists_detail),
            type = SnackbarType.WARNING,
            action = SnackbarAction(
                name = "Try another name",
                action = {
                    onRetry()
                    SnackbarController.dismissSnackbarById(snackbarId)
                }
            ),
            secondaryAction = SnackbarAction(
                name = "Cancel",
                action = {
                    SnackbarController.dismissSnackbarById(snackbarId)
                    showSnackbar(
                        message = "Dismissed snackbar: $snackbarId",
                        type = SnackbarType.INFO,
                        duration = SnackbarDuration.Short
                    )
                }
            ),
            duration = SnackbarDuration.Indefinite,
        )
        currentSnackbarId = snackbarId
    }


    fun syncExercises() {
        viewModelScope.launch {
            try {
                syncEngine.pushEntityClass(Exercise::class)
            } catch (e: Exception) {
                Log.e("ExerciseScreenViewModel", "Error syncing exercises", e)
            }
        }
    }

}