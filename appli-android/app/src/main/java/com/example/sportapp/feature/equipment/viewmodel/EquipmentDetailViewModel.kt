package com.example.sportapp.feature.equipment.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.local.AvailableEquipmentDao
import com.example.sportapp.core.data.local.EquipmentDao
import com.example.sportapp.core.data.local.ExerciseDao
import com.example.sportapp.core.data.local.ExerciseEquipmentDao
import com.example.sportapp.core.data.model.AvailableEquipment
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.feature.equipment.domain.EquipmentOwnership
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Détail d'un matériel sélectionné (clé = nom, possession insensible à la casse). */
data class EquipmentDetail(
    val name: String,
    /** UUID du catalogue global si présent (requis pour les exercices liés). */
    val uuid: String?,
    val inCatalog: Boolean,
    val owned: Boolean,
)

@HiltViewModel
class EquipmentDetailViewModel @Inject constructor(
    private val equipmentDao: EquipmentDao,
    private val availableEquipmentDao: AvailableEquipmentDao,
    private val exerciseEquipmentDao: ExerciseEquipmentDao,
    private val exerciseDao: ExerciseDao,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val equipmentName = MutableStateFlow<String?>(null)

    fun setEquipmentName(name: String) {
        equipmentName.value = name
    }

    val detail: StateFlow<EquipmentDetail?> =
        combine(
            equipmentName.filterNotNull(),
            equipmentDao.observeAll(),
            availableEquipmentDao.observeAll(),
        ) { name, equipments, available ->
            val catalog = equipments.firstOrNull {
                !it.pendingDeletion && it.name.equals(name, ignoreCase = true)
            }
            val owned = available.any {
                !it.pendingDeletion && it.name.equals(name, ignoreCase = true)
            }
            EquipmentDetail(
                name = catalog?.name ?: name,
                uuid = catalog?.uuid,
                inCatalog = catalog != null,
                owned = owned,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Exercices du catalogue qui utilisent ce matériel (via exercise_equipment). */
    val exercisesUsing: StateFlow<List<Exercise>> =
        combine(
            detail,
            exerciseEquipmentDao.observeAll(),
            exerciseDao.observeAll(),
        ) { d, links, exercises ->
            val uuid = d?.uuid
            if (uuid == null) {
                emptyList()
            } else {
                val exerciseUuids = links
                    .filter { !it.pendingDeletion && it.equipmentUUID == uuid }
                    .map { it.exerciseUUID }
                    .toSet()
                exercises
                    .filter { !it.pendingDeletion && it.uuid in exerciseUuids }
                    .sortedBy { it.name.lowercase() }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Bascule la possession « mon matériel » puis pousse les changements. */
    fun toggleOwned(name: String) = viewModelScope.launch {
        val uid = CurrentUserManager.userId ?: return@launch
        EquipmentOwnership.toggle(availableEquipmentDao, name, uid)
        syncEngine.pushEntityClass(AvailableEquipment::class)
    }
}
