// MuscleScreenViewModel.kt
package com.example.sportapp.feature.muscles.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.local.MuscleDao
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.core.sync.SyncManager
import com.example.sportapp.core.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MuscleScreenViewModel @Inject constructor(
    private val muscleDao: MuscleDao,
    private val syncEngine: SyncEngine,
    private val syncManager: SyncManager
) : ViewModel() {

    private val muscleUuid = MutableStateFlow<String?>(null)

    val muscle: StateFlow<Muscle?> =
        muscleUuid
            .filterNotNull()
            .distinctUntilChanged()
            .flatMapLatest { uuid -> muscleDao.observeMuscleByUUID(uuid) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMuscleUuid(uuid: String) {
        muscleUuid.value = uuid
    }

    fun toggleFavorite() {
        val current = muscle.value ?: return
        viewModelScope.launch {
            muscleDao.updateFavorite(current.uuid, !current.isFavorite)
            syncMuscle()
        }
    }

    /** Édition zone + groupe musculaire (valeurs canoniques Zones.ALL / MuscleGroups.ALL). */
    fun updateZoneAndGroup(zone: String, muscleGroup: String) {
        val current = muscle.value ?: return
        viewModelScope.launch {
            muscleDao.updateMuscle(current.copy(zone = zone, muscleGroup = muscleGroup))
            syncEngine.pushEntityClass(Muscle::class)
        }
    }

    fun syncMuscle() {
        viewModelScope.launch {
            syncEngine.pushEntityClass(Muscle::class)
        }
    }

    fun markMuscleForDeletion(muscle: Muscle) {
        viewModelScope.launch {
            try {
                muscleDao.markAsPendingDeletion(muscle.uuid)
                muscleDao.markAsUnsynced(muscle.uuid)
                syncEngine.pushEntityClass(Muscle::class)
            } catch (e: Exception) {
                Log.e("MuscleScreenViewModel", "markAsPendingDeletion failed", e)
            }
        }
    }
}
