package com.example.sportapp.core.sync.base

import com.example.sportapp.core.utils.CustomDateUtils

/**
 * Opérations génériques de pull/merge sur une `SyncableEntity<*>`. Extraites
 * de `SyncEngine` pour permettre des tests unitaires isolés (le SyncEngine
 * itère sur le SyncRegistry et délègue ici).
 *
 * Cf. T4.2 Phase 1.3 (2026-05-07).
 */

/**
 * Pull serveur + 3-way merge avec la table locale + prune des locaux orphelins.
 *
 * - Insert/update les rows remote plus récentes (`isRemoteNewer` sur `updatedAt`).
 * - Préserve les locaux `synced=false` (création en attente de push).
 * - Supprime les locaux `synced=true` absents du remote (= deleted server-side
 *   par un autre client).
 *
 * Migré depuis `RemoteDataMerger.mergeAllFromServer()` (V3.2 dates +
 * V4.4-différé pruneStaleLocals).
 */
@Suppress("UNCHECKED_CAST")
suspend fun mergeFromRemote(syncable: SyncableEntity<*>) {
    val typed = syncable as SyncableEntity<Any>
    val remote = typed.getRemote()
    val local = typed.getAllOnce().associateBy(typed::keyOf)

    for (r in remote) {
        val l = local[typed.keyOf(r)]
        if (l == null || isRemoteNewer(typed.updatedAtOf(l), typed.updatedAtOf(r))) {
            typed.insertFromServer(r)
        }
    }

    val remoteKeys = remote.map(typed::keyOf).toSet()
    local.values
        .filter { typed.syncedOf(it) && typed.keyOf(it) !in remoteKeys }
        .forEach { typed.deleteLocal(it) }
}

/**
 * Pull serveur + clear local + bulk insert. Outil dev (bouton "Get All").
 *
 * @param syncedAfter `true` : insère avec `synced=true` (état propre post-pull,
 *   forcé par `bulkInsertFromServer` qui pose `synced=true` + `pendingDeletion=false`).
 *   `false` : insère puis flippe `synced=false` row-par-row pour tester
 *   un re-push immédiat (dev tool).
 */
@Suppress("UNCHECKED_CAST")
suspend fun pullThenReplace(syncable: SyncableEntity<*>, syncedAfter: Boolean = true) {
    val typed = syncable as SyncableEntity<Any>
    val remote = typed.getRemote()
    typed.clearLocal()

    if (syncedAfter) {
        typed.bulkInsertFromServer(remote)
    } else {
        remote.forEach { item ->
            typed.insertFromServer(item)
            typed.markAsUnsynced(item)
        }
    }
}

/**
 * Push bulk de toutes les rows locales (sans filtre `synced`) vers le serveur.
 * Outil dev (bouton "Upsert"). Pas de markAsSynced (comportement legacy
 * `RemoteDataUpserter.upsertAllData`).
 */
@Suppress("UNCHECKED_CAST")
suspend fun bulkPush(syncable: SyncableEntity<*>, items: List<*>) {
    (syncable as SyncableEntity<Any>).upsertBulk(items as List<Any>)
}

/**
 * Compare 2 timestamps en parsant en `Instant` absolu (UTC).
 *
 * Convention NULL :
 * - `remote = null` → pas plus récent (rien à mettre à jour)
 * - `local  = null` → remote plus récent (toute valeur > rien)
 * - parse fail → pas plus récent (politique safe)
 *
 * Migré depuis `RemoteDataMerger.isRemoteNewer` (V3.2 datage).
 */
fun isRemoteNewer(localUpdated: String?, remoteUpdated: String?): Boolean {
    if (remoteUpdated == null) return false
    if (localUpdated == null) return true
    val r = CustomDateUtils.parseInstantSafe(remoteUpdated) ?: return false
    val l = CustomDateUtils.parseInstantSafe(localUpdated) ?: return true
    return r.isAfter(l)
}
