package com.example.sportapp.feature.muscles.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.local.MuscleDao
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.sync.SyncManager
import com.example.sportapp.core.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MuscleListScreenViewModel @Inject constructor(
    private val muscleDao: MuscleDao,
    private val syncEngine: SyncEngine,
    private val syncManager: SyncManager
) : ViewModel() {

    // Si CurrentUserManager expose un Flow → parfait
    val userId: StateFlow<Int?> =
        MutableStateFlow(CurrentUserManager.userId) // statique au démarrage
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Flow DB -> UI
    val allMuscles: StateFlow<List<Muscle>> =
        muscleDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteMuscle(muscle: Muscle) = viewModelScope.launch {
        muscleDao.markAsPendingDeletion(muscle.uuid)
        muscleDao.markAsUnsynced(muscle.uuid)
        syncEngine.pushEntityClass(Muscle::class)
    }

    fun toggleFavorite(muscle: Muscle) = viewModelScope.launch {
        muscleDao.updateFavorite(muscle.uuid, !muscle.isFavorite)
        muscleDao.markAsUnsynced(muscle.uuid)
        syncEngine.pushEntityClass(Muscle::class)
    }

    fun addMuscle(newMuscle: Muscle) = viewModelScope.launch {
        muscleDao.insert(newMuscle)
        syncEngine.pushEntityClass(Muscle::class)
    }

    fun updateMuscle(updatedMuscle: Muscle) = viewModelScope.launch {
        muscleDao.updateMuscle(updatedMuscle)
        muscleDao.markAsUnsynced(updatedMuscle.uuid)
        syncEngine.pushEntityClass(Muscle::class)
    }

    fun syncMuscles() = viewModelScope.launch {
        syncEngine.pushEntityClass(Muscle::class)
    }
}
