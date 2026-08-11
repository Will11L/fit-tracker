package com.example.sportapp.core.sync

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shell mince qui delegue le pull-then-replace au [SyncEngine].
 *
 * T4.2 Phase 2.3 (2026-05-07) : la classe etait ~217 lignes de dispatch
 * dupliquees (21 blocs `clearAll + insertAll(remote)` par entite). La
 * logique vit maintenant dans `sync/base/SyncMergeOps.kt::pullThenReplace`,
 * et le SyncEngine itere sur `SyncRegistry.all` pour appliquer l'operation.
 *
 * Bouton "Get All" Settings : `getAllAsUnsynced` (synced=false, force re-push
 * au prochain syncAll). Phase 3.1 a supprime `getAll` (synced=true, code mort
 * jamais appele en pratique — son seul appelant aurait ete bloque par le bug
 * latent du DAO `insertAll` qui force synced=false).
 */
@Singleton
class RemoteDataGetter @Inject constructor(
    private val syncEngine: SyncEngine,
) {
    /**
     * Pull-then-replace en `synced=false` (force un re-push immediat au
     * prochain `syncAllToServer`). Outil dev utilise par le bouton "Get All"
     * de l'ecran Settings : permet de tester le round-trip pull -> push.
     *
     * NE PAS utiliser en code de production : provoque un double-push
     * silencieux a chaque appel (cf. doc d'origine V4.4-B3).
     */
    suspend fun getAllAsUnsynced(log: Boolean = false): Boolean =
        syncEngine.pullReplace(syncedAfter = false, log = log)
}
