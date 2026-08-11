package com.example.sportapp.core.sync

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shell mince qui delegue le pull 3-way merge au [SyncEngine].
 *
 * T4.2 Phase 2.2 (2026-05-07) : la classe etait ~575 lignes de dispatch
 * dupliquees (21 blocs `clearAll + insertFromServer + pruneStaleLocals` par
 * entite). La logique 3-way merge + prune vit maintenant dans
 * `sync/base/SyncMergeOps.kt::mergeFromRemote`, le SyncEngine itere sur
 * `SyncRegistry.all` et applique l'operation a chaque entite.
 *
 * L'API publique reste inchangee pour ne pas casser les callsites existants
 * (NetworkMonitor, AuthManager, SplashScreenViewModel, SyncSettingsViewModel
 * bouton "Merge").
 */
@Singleton
class RemoteDataMerger @Inject constructor(
    private val syncEngine: SyncEngine,
) {
    suspend fun mergeAllFromServer(log: Boolean = true): Boolean =
        syncEngine.pullMerge(log = log)
}
