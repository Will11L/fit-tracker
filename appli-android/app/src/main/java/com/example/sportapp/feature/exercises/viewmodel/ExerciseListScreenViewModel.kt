package com.example.sportapp.feature.exercises.viewmodel

import com.example.sportapp.core.data.model.ExerciseEquipment
import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.app.SnackbarAction
import com.example.sportapp.app.SnackbarController
import android.content.Context
import com.example.sportapp.R
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.sportapp.core.data.local.EquipmentDao
import com.example.sportapp.core.data.local.ExerciseDao
import com.example.sportapp.core.data.local.ExerciseEquipmentDao
import com.example.sportapp.core.data.local.ExerciseMuscleDao
import com.example.sportapp.core.data.local.MuscleDao
import com.example.sportapp.core.data.model.Equipment
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.data.model.ExerciseMuscle
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.sync.SyncManager
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map


@HiltViewModel
class ExerciseListScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exerciseDao: ExerciseDao,
    private val muscleDao: MuscleDao,
    private val equipmentDao: EquipmentDao,
    private val exerciseMuscleDao: ExerciseMuscleDao,
    private val exerciseEquipmentDao: ExerciseEquipmentDao,
    private val syncEngine: SyncEngine,
    private val syncManager: SyncManager
) : ViewModel() {

    internal var currentSnackbarId: String? = null

    private val _userId = MutableStateFlow<Int?>(CurrentUserManager.userId)
    val userId: StateFlow<Int?> = _userId

    // ✅ Flows réactifs
    val allExercises: StateFlow<List<Exercise>> =
        exerciseDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMuscles: StateFlow<List<Muscle>> =
        muscleDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allEquipments: StateFlow<List<Equipment>> =
        equipmentDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ✅ Mapping dynamique via combine()
    val musclesByExercise: StateFlow<Map<String, List<Muscle>>> =
        allExercises.flatMapLatest { exercises ->
            combine(
                exercises.map { ex ->
                    exerciseMuscleDao.getMusclesByExerciseUUIDFlow(ex.uuid)
                        .map { rels -> ex.uuid to rels.mapNotNull { muscleDao.getMuscleByUUID(it.muscleUUID) } }
                }
            ) { it.toMap() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val equipmentsByExercise: StateFlow<Map<String, List<Equipment>>> =
        allExercises.flatMapLatest { exercises ->
            combine(
                exercises.map { ex ->
                    exerciseEquipmentDao.getEquipmentsByExerciseUUIDFlow(ex.uuid)
                        .map { rels -> ex.uuid to rels.mapNotNull { equipmentDao.getEquipmentByUUID(it.equipmentUUID) } }
                }
            ) { it.toMap() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ✅ Exemple de mutation : pas besoin de reload
    fun toggleFavorite(exercise: Exercise) {
        viewModelScope.launch {
            exerciseDao.toggleFavorite(exercise.uuid)
            exerciseDao.markAsUnsynced(exercise.uuid)
            syncEngine.pushEntityClass(Exercise::class)
        }
    }

    fun deleteExercise(exercise: Exercise) {
        viewModelScope.launch {
            exerciseDao.markAsPendingDeletion(exercise.uuid)
            exerciseDao.markAsUnsynced(exercise.uuid)

            val exerciseMuscles = exerciseMuscleDao.getExerciseMusclesByExerciseUUID(exercise.uuid)
            exerciseMuscles.forEach {
                exerciseMuscleDao.markAsPendingDeletion(it.uuid)
                exerciseMuscleDao.markAsUnsynced(it.uuid)
            }

            val exerciseEquipments = exerciseEquipmentDao.getEquipmentsByExerciseUUID(exercise.uuid)
            exerciseEquipments.forEach {
                exerciseEquipmentDao.markAsPendingDeletion(it.uuid)
                exerciseEquipmentDao.markAsUnsynced(it.uuid)
            }

            syncEngine.pushEntityClass(Exercise::class)
            syncEngine.pushEntityClass(ExerciseMuscle::class)
            syncEngine.pushEntityClass(ExerciseEquipment::class)
        }
    }

    fun addExerciseManually(newExercise: Exercise, selectedMuscles: List<Muscle>, selectedEquipments: List<Equipment>) {
        viewModelScope.launch {
            exerciseDao.insert(newExercise)

            val muscleRelations = selectedMuscles.map {
                ExerciseMuscle(
                    uuid = UUID.randomUUID().toString(),
                    exerciseUUID = newExercise.uuid,
                    muscleUUID = it.uuid,
                    coefficient = 1.0f,
                    synced = false,
                    pendingDeletion = false
                )
            }
            exerciseMuscleDao.insertAll(muscleRelations)

            val equipmentRelations = selectedEquipments.map {
                com.example.sportapp.core.data.model.ExerciseEquipment(
                    uuid = UUID.randomUUID().toString(),
                    exerciseUUID = newExercise.uuid,
                    equipmentUUID = it.uuid,
                    synced = false,
                    pendingDeletion = false
                )
            }
            exerciseEquipmentDao.insertAll(equipmentRelations)

            syncEngine.pushEntityClass(Exercise::class)
            syncEngine.pushEntityClass(ExerciseMuscle::class)
            syncEngine.pushEntityClass(ExerciseEquipment::class)
        }
    }

    fun refreshUserId() {
        _userId.value = CurrentUserManager.userId
    }

    fun updateExercise(updatedExercise: Exercise, muscles: List<Muscle>, equipments: List<Equipment>) {
        viewModelScope.launch {
            try {
                // 1. Met à jour l'exercice
                exerciseDao.updateExercise(updatedExercise)
                exerciseDao.markAsUnsynced(updatedExercise.uuid)

                // 2. 🔄 Relations Muscle
                val existingMuscleRelations = exerciseMuscleDao.getExerciseMusclesByExerciseUUID(updatedExercise.uuid)
                val existingMuscleUUIDs = existingMuscleRelations.map { it.muscleUUID }.toSet()
                val newMuscleUUIDs = muscles.map { it.uuid }.toSet()

                // Supprimer celles qui ne sont plus là
                val toDeleteMuscle = existingMuscleRelations.filter { it.muscleUUID !in newMuscleUUIDs }
                toDeleteMuscle.forEach {
                    exerciseMuscleDao.markAsPendingDeletion(it.uuid)
                    exerciseMuscleDao.markAsUnsynced(it.uuid)
                }

                // Ajouter les nouvelles
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

                // 3. 🔄 Relations Equipment
                val existingEquipmentRelations = exerciseEquipmentDao.getEquipmentsByExerciseUUID(updatedExercise.uuid)
                val existingEquipmentUUIDs = existingEquipmentRelations.map { it.equipmentUUID }.toSet()
                val newEquipmentUUIDs = equipments.map { it.uuid }.toSet()

                val toDeleteEquipments = existingEquipmentRelations.filter { it.equipmentUUID !in newEquipmentUUIDs }
                toDeleteEquipments.forEach {
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

                // 4. Sync & reload
                syncEngine.pushEntityClass(Exercise::class)
                syncEngine.pushEntityClass(ExerciseMuscle::class)
                syncEngine.pushEntityClass(ExerciseEquipment::class)

            } catch (e: Exception) {
                Log.e("ExerciseListScreenViewModel", "❌ Error updating exercise:", e)
            }
        }
    }

    fun syncExercises() {
        viewModelScope.launch {
            syncEngine.pushEntityClass(Exercise::class)
        }
    }

    fun clearAllExercises() {
        viewModelScope.launch {
            exerciseDao.clearAll()
        }
    }

    fun onShowDuplicateExerciseNameSnackbar(onRetry: () -> Unit) {
        var snackbarId = ""
        snackbarId = showSnackbar(
            message = context.getString(R.string.vm_exercise_name_exists),
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


}
