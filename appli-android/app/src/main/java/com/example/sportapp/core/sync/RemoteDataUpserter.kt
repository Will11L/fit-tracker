package com.example.sportapp.core.sync

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shell mince qui delegue les pushs au [SyncEngine].
 *
 * T4.2 Phase 2.3 (2026-05-07) : la classe etait ~502 lignes de dispatch
 * dupliquees (2 paths * 21 blocs : `upsertAllUnsynced` 1-by-1 et
 * `upsertAllData` bulk). La logique vit maintenant dans le SyncEngine.
 *
 * Bouton "Upsert" Settings : `upsertAllData` (bulk push tout sans filtre).
 * Phase 3.1 a supprime `upsertAllUnsynced` (jamais appele en pratique,
 * redondant avec `SyncManager.syncAllToServer` qui fait deja ce path).
 */
@Singleton
class RemoteDataUpserter @Inject constructor(
    private val syncEngine: SyncEngine,
) {
    /**
     * Push bulk de TOUTES les rows (sans filtre `synced`). Outil dev utilise
     * par le bouton "Upsert" de l'ecran Settings : force push tout pour
     * tester la chaine montante. Pas de markAsSynced (comportement legacy).
     */
    suspend fun upsertAllData(log: Boolean = false): Boolean =
        syncEngine.bulkPushAll(log = log)
}
