package com.example.sportapp.core.sync.base

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Snapshot des compteurs pour une entite syncable. Utilise par l'UI Settings
 * pour afficher en temps reel l'etat de sync (cf. T4.2 Phase 4.2).
 */
data class EntityStats(
    val total: Int,
    val unsynced: Int,
    val pendingDeletion: Int,
) {
    /** True si toutes les rows sont synced=true ET aucune pendingDeletion. */
    val allSynced: Boolean get() = unsynced == 0 && pendingDeletion == 0

    companion object {
        val EMPTY = EntityStats(0, 0, 0)
    }
}

/**
 * Flow des compteurs derives de `observeAll()`. Re-emet a chaque changement
 * Room. Utilise par l'UI Settings (1 carte par entite avec compteurs animes).
 */
@Suppress("UNCHECKED_CAST")
fun SyncableEntity<*>.observeStats(): Flow<EntityStats> {
    val typed = this as SyncableEntity<Any>
    return typed.observeAll().map { items ->
        EntityStats(
            total = items.size,
            unsynced = items.count { !typed.syncedOf(it) },
            pendingDeletion = items.count { typed.pendingDeletionOf(it) },
        )
    }
}
