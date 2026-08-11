package com.example.sportapp.core.sync

import android.util.Log
import com.example.sportapp.core.sync.base.SyncableEntity
import com.example.sportapp.core.sync.base.bulkPush
import com.example.sportapp.core.sync.base.mergeFromRemote
import com.example.sportapp.core.sync.base.pullThenReplace
import com.example.sportapp.core.sync.base.syncEntity
import com.example.sportapp.core.sync.base.syncEntityDeletions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Moteur central du système de sync. Itère sur le `SyncRegistry` pour
 * exécuter les opérations push/pull/merge sans dispatch entité-par-entité
 * ailleurs.
 *
 * Remplace fonctionnellement les ~1300 lignes dispatch dupliquées de
 * `RemoteDataGetter` + `RemoteDataMerger` + `RemoteDataUpserter` (Phase 2.x
 * les transforme en shells minces qui délèguent ici).
 *
 * **Pas de mutex / snackbar** ici : c'est le rôle du `SyncManager` (legacy)
 * et du `SyncCoordinator` (Phase 4.1). Le `SyncEngine` est purement métier.
 *
 * La logique merge/pull/bulkPush est extraite dans `SyncMergeOps.kt` pour
 * être testable en isolation (sans passer par le `SyncRegistry`).
 *
 * Cf. T4.2 Phase 1.3 (2026-05-07).
 */
@Singleton
class SyncEngine @Inject constructor(
    private val registry: SyncRegistry,
) {
    private val tag = "SyncEngine"

    // ─── PUSH ────────────────────────────────────────────────────────────────

    /**
     * Push complet vers le serveur — équivalent fonctionnel de
     * `SyncManager.syncAllToServer()` (sans mutex / snackbar).
     *
     * Phase 1 (deletes) : ordre INVERSE pour respecter ON DELETE CASCADE
     * Postgres (enfants avant parents → évite 404 cascade).
     * Phase 2 (upserts) : ordre normal (parents avant enfants).
     *
     * Délègue à `syncEntityDeletions()` + `syncEntity()` existants pour
     * préserver la logique de retry bulk→1by1 + 401 silent (V4.5).
     */
    suspend fun pushAll(log: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        var success = true

        registry.reversed.forEach { syncable ->
            if (syncable.readOnly) return@forEach
            runCatching { syncEntityDeletions(syncable) }.onFailure {
                success = false
                Log.e(tag, "❌ pushAll deletes failed for ${syncable.entityName}", it)
            }
        }

        registry.all.forEach { syncable ->
            if (syncable.readOnly) return@forEach
            runCatching { syncEntity(syncable) }.onFailure {
                success = false
                Log.e(tag, "❌ pushAll upserts failed for ${syncable.entityName}", it)
            }
        }

        if (log) Log.d(tag, "pushAll completed (success=$success)")
        success
    }

    /**
     * Push bulk de TOUTES les rows (pas juste les `synced=false`) — équivalent
     * fonctionnel de `RemoteDataUpserter.upsertAllData()`. Outil dev (bouton
     * "Upsert" Settings) : force push tout sans filtre. Pas de markAsSynced.
     */
    suspend fun bulkPushAll(log: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        var success = true

        registry.all.forEach { syncable ->
            if (syncable.readOnly) return@forEach
            val items = syncable.observeAll().first()
            if (log) Log.d(tag, "📤 bulkPushAll ${syncable.entityName}: ${items.size}")
            runCatching { bulkPush(syncable, items) }.onFailure {
                success = false
                Log.e(tag, "❌ bulkPushAll [${syncable.entityName}] failed", it)
            }
        }

        success
    }

    /**
     * Push ciblé sur la table d'une entité (instance comme proxy pour identifier
     * la table). Push tous les `synced=false` + `pendingDeletion=true` de la
     * table. Utilisé par les ViewModels après création/édition d'un row :
     * `syncEngine.pushEntity(myMuscle)` push tous les muscles unsynced.
     */
    suspend fun pushEntity(item: Any, log: Boolean = false): Boolean =
        pushSyncable(registry.findByItem(item), label = item::class.simpleName, log = log)

    /**
     * Variante par classe Kotlin quand on n'a pas d'instance disponible :
     * `syncEngine.pushEntityClass(Muscle::class)`.
     */
    suspend fun pushEntityClass(klass: KClass<out Any>, log: Boolean = false): Boolean =
        pushSyncable(registry.findByClass(klass), label = klass.simpleName, log = log)

    /**
     * Push de plusieurs entites en sequence (ordre garanti). Utilise par les
     * ViewModels qui modifient plusieurs tables liees en une operation
     * (ex: SessionExercise sauve un set + maj exercise + maj workout).
     *
     * `syncEngine.pushEntityClasses(ActualWorkoutSet::class, ActualWorkoutExercise::class, ActualWorkout::class)`
     * remplace l'ancien `syncEngine.pushEntityClasses(ActualWorkoutSet::class, ActualWorkoutExercise::class, ActualWorkout::class)`.
     */
    suspend fun pushEntityClasses(vararg klasses: KClass<out Any>, log: Boolean = false): Boolean {
        var success = true
        klasses.forEach { klass ->
            if (!pushEntityClass(klass, log = log)) success = false
        }
        return success
    }

    private suspend fun pushSyncable(
        syncable: SyncableEntity<*>?,
        label: String?,
        log: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        if (syncable == null) {
            Log.e(tag, "pushEntity: no SyncableEntity for $label")
            return@withContext false
        }
        if (syncable.readOnly) return@withContext true  // no-op silencieux

        var success = true
        runCatching { syncEntityDeletions(syncable) }.onFailure {
            success = false
            Log.e(tag, "❌ pushEntity deletes failed for ${syncable.entityName}", it)
        }
        runCatching { syncEntity(syncable) }.onFailure {
            success = false
            Log.e(tag, "❌ pushEntity upserts failed for ${syncable.entityName}", it)
        }

        if (log) Log.d(tag, "pushEntity ${syncable.entityName} (success=$success)")
        success
    }

    // ─── PULL ────────────────────────────────────────────────────────────────

    /**
     * Pull 3-way merge — équivalent fonctionnel de
     * `RemoteDataMerger.mergeAllFromServer()`.
     */
    suspend fun pullMerge(log: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        var success = true

        registry.all.forEach { syncable ->
            runCatching {
                if (log) Log.d(tag, "📦 Merge ${syncable.entityName}")
                mergeFromRemote(syncable)
            }.onFailure {
                success = false
                Log.e(tag, "❌ pullMerge [${syncable.entityName}] failed", it)
            }
        }

        success
    }

    /**
     * Pull-then-replace — équivalent fonctionnel de
     * `RemoteDataGetter.fetchAllAndPersist()`.
     *
     * @param syncedAfter `true` (default) : insère avec `synced=true` (état
     *   propre post-pull). `false` : insère avec `synced=false` (outil dev,
     *   force re-push immédiat — utilisé par bouton "Get All" Settings).
     */
    suspend fun pullReplace(syncedAfter: Boolean = true, log: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        var success = true

        registry.all.forEach { syncable ->
            runCatching {
                if (log) Log.d(tag, "📦 Replace ${syncable.entityName}")
                pullThenReplace(syncable, syncedAfter)
            }.onFailure {
                success = false
                Log.e(tag, "❌ pullReplace [${syncable.entityName}] failed", it)
            }
        }

        success
    }
}
