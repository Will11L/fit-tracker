package com.example.sportapp.core.sync

import android.util.Log
import com.example.sportapp.feature.health.data.HealthImporter
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralise les triggers de sync (login, retour reseau, action user) en
 * 3 entry points unifies + retry exponentiel sur les triggers automatiques
 * (login, network) qui peuvent echouer silencieusement.
 *
 * Consume par : NetworkMonitor, SplashScreenViewModel, AuthManager,
 * DrawerViewModel, SyncSettingsViewModel.
 *
 * Architecture :
 *
 *     [Trigger] -> SyncCoordinator
 *                       |
 *                       +-> push : SyncManager.syncAllToServer (mutex + UX)
 *                       +-> pull : RemoteDataMerger.mergeAllFromServer
 *
 * Ordre push-puis-merge (V4.4-B3) preserve : evite que le merge ecrase un
 * pendingDeletion local AVANT que celui-ci ait pu etre push au serveur.
 *
 * Cf. T4.2 Phase 4.1 (2026-05-07).
 */
@Singleton
class SyncCoordinator @Inject constructor(
    private val syncManager: SyncManager,
    private val remoteDataMerger: RemoteDataMerger,
    // Santé (2026-06-17) : import HC -> Room (synced=false) avant chaque push, pour
    // que les données Health Connect du jour partent au serveur dans le même cycle.
    // Best-effort (no-op si HC absent / permission manquante).
    private val healthImporter: HealthImporter,
) {
    /**
     * Trigger post-login : pull state serveur d'abord, puis push pending
     * locaux. Ordre INVERSE de [onNetworkAvailable] : au login l'utilisateur
     * peut venir d'un autre device et on veut afficher le state serveur sans
     * delai. Les modifs locales pending (rares post-login) sont push apres.
     *
     * Retry exponentiel (3 tentatives) car la sync post-login conditionne
     * l'etat initial vu par l'utilisateur.
     */
    suspend fun onLogin() {
        Log.d(TAG, "Sync trigger: login")
        runWithBackoff(label = "onLogin") {
            healthImporter.importRecentToRoom()
            remoteDataMerger.mergeAllFromServer(log = false)
            syncManager.syncAllToServer()
        }
    }

    /**
     * Trigger au retour reseau : push pending + pull merge. Retry
     * exponentiel (3 tentatives) car le retour reseau peut etre flaky.
     */
    suspend fun onNetworkAvailable() {
        Log.d(TAG, "Sync trigger: network reconnect")
        runWithBackoff(label = "onNetworkAvailable") {
            healthImporter.importRecentToRoom()
            syncManager.syncAllToServer()
            remoteDataMerger.mergeAllFromServer(log = false)
        }
    }

    /**
     * Trigger user-initiated (bouton drawer "Sync Now" ou Settings "Sync All").
     * Pas de retry : l'utilisateur attend un retour immediat. S'il echoue,
     * une snackbar erreur est affichee par SyncManager.syncAllToServer.
     */
    suspend fun onUserAction() {
        Log.d(TAG, "Sync trigger: user action")
        healthImporter.importRecentToRoom()
        syncManager.syncAllToServer()
    }

    /**
     * Backoff exponentiel : 1s -> 2s -> 4s. Apres 3 echecs, on log et on
     * laisse la prochaine occasion (ex: prochain retour reseau) reessayer.
     */
    private suspend fun runWithBackoff(
        label: String,
        maxAttempts: Int = 3,
        action: suspend () -> Unit,
    ) {
        var attempt = 1
        while (attempt <= maxAttempts) {
            try {
                action()
                if (attempt > 1) Log.d(TAG, "[$label] succeeded on attempt $attempt")
                return
            } catch (e: Exception) {
                if (attempt == maxAttempts) {
                    Log.e(TAG, "[$label] failed after $maxAttempts attempts", e)
                    return  // pas de throw : on swallow pour permettre la prochaine trigger
                }
                val delayMs = 1000L * (1L shl (attempt - 1))
                Log.w(TAG, "[$label] attempt $attempt failed, retry in ${delayMs}ms: ${e.message}")
                delay(delayMs)
                attempt++
            }
        }
    }

    private companion object {
        const val TAG = "SyncCoordinator"
    }
}
