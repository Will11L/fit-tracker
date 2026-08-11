package com.example.sportapp.feature.equipment.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.local.AvailableEquipmentDao
import com.example.sportapp.core.data.local.EquipmentDao
import com.example.sportapp.core.data.model.AvailableEquipment
import com.example.sportapp.core.data.model.Equipment
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.feature.equipment.domain.EquipmentOwnership
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ligne unifiée de la liste matériel : catalogue global ([Equipment]) ∪ matériel
 * perso ([AvailableEquipment]) hors catalogue. Miroir de `EquipItem` du client web.
 */
data class EquipmentItem(
    /** UUID du catalogue (Equipment) si présent — requis pour lister les exercices liés. */
    val uuid: String?,
    val name: String,
    /** Possédé = un AvailableEquipment homonyme (insensible à la casse) existe. */
    val owned: Boolean,
    /** Présent dans le catalogue global (Equipment). */
    val inCatalog: Boolean,
    /** Synchronisé = aucun changement local en attente (catalogue + possession). */
    val synced: Boolean,
)

@HiltViewModel
class EquipmentListViewModel @Inject constructor(
    private val equipmentDao: EquipmentDao,
    private val availableEquipmentDao: AvailableEquipmentDao,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    val userId: StateFlow<Int?> = CurrentUserManager.userIdFlow

    /** Liste unifiée catalogue + matériel perso hors catalogue. */
    val items: StateFlow<List<EquipmentItem>> =
        combine(
            equipmentDao.observeAll(),
            availableEquipmentDao.observeAll(),
        ) { equipments, available ->
            buildItems(equipments, available)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun buildItems(
        equipments: List<Equipment>,
        available: List<AvailableEquipment>,
    ): List<EquipmentItem> {
        val activeAvailable = available.filter { !it.pendingDeletion }
        val availByName = activeAvailable.associateBy { it.name.lowercase() }
        val activeCatalog = equipments.filter { !it.pendingDeletion }
        val catalogNames = activeCatalog.map { it.name.lowercase() }.toSet()

        val list = activeCatalog.map { e ->
            val av = availByName[e.name.lowercase()]
            EquipmentItem(
                uuid = e.uuid,
                name = e.name,
                owned = av != null,
                inCatalog = true,
                // Non synchronisé si le catalogue OU la possession a un changement local en attente.
                synced = e.synced && (av == null || av.synced),
            )
        }.toMutableList()

        // Matériel perso qui n'est pas (ou plus) dans le catalogue global.
        for (a in activeAvailable) {
            if (a.name.lowercase() !in catalogNames) {
                list += EquipmentItem(
                    uuid = null,
                    name = a.name,
                    owned = true,
                    inCatalog = false,
                    synced = a.synced,
                )
            }
        }
        return list
    }

    /** Bascule la possession « mon matériel » puis pousse les changements. */
    fun toggleOwned(name: String) = viewModelScope.launch {
        val uid = CurrentUserManager.userId ?: return@launch
        EquipmentOwnership.toggle(availableEquipmentDao, name, uid)
        syncEngine.pushEntityClass(AvailableEquipment::class)
    }

    /** Ajoute un matériel perso (bouton +) puis pousse les changements. */
    fun addPersonalEquipment(name: String) = viewModelScope.launch {
        val uid = CurrentUserManager.userId ?: return@launch
        EquipmentOwnership.addPersonal(availableEquipmentDao, name, uid)
        syncEngine.pushEntityClass(AvailableEquipment::class)
    }

    /** Pousse les changements locaux de matériel (perso + catalogue). */
    fun sync() = viewModelScope.launch {
        syncEngine.pushEntityClass(AvailableEquipment::class)
        syncEngine.pushEntityClass(Equipment::class)
    }
}
