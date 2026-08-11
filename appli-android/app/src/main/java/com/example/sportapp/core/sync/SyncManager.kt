package com.example.sportapp.core.sync

import android.content.Context
import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.core.content.edit
import com.example.sportapp.core.network.TokenManager
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestre le push global vers le serveur (mutex + snackbars + lastSyncTime).
 *
 * T4.2 Phase 3.2 step 5 (2026-05-07) : la classe etait un God-object qui
 * portait 20 methodes `sync<X>()` + 2 helpers compose + dispatch DAO direct
 * (~470 lignes). Refactor : tout le push delegue au [SyncEngine] + iteration
 * sur [SyncRegistry] pour `checkForUnsyncedData`. Volume : 470 -> ~80 lignes.
 *
 * Phase 4.1 prevue : extraction du `SyncCoordinator` qui regroupera l'ensemble
 * du lifecycle sync (login, network reconnect, foreground, retry) et reduira
 * encore SyncManager.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncEngine: SyncEngine,
    private val registry: SyncRegistry,
) {
    // V4.4 — Mutex au lieu d'un Boolean : un crash entre acquire et release
    // laissait la sync bloquee jusqu'au restart. tryLock() + unlock() en
    // finally garantit le release sur exception non-catchee.
    private val syncMutex = Mutex()

    // Horodatage de la derniere sync REUSSIE, persiste sur disque : sans ca, le
    // StateFlow en memoire repartait a "" a chaque cold start -> le drawer
    // affichait toujours "Never" meme apres des jours de sync. Charge au demarrage
    // depuis les prefs, ecrit uniquement quand pushAll aboutit (pas sur tentative).
    private val prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)

    private val _lastSyncTime = MutableStateFlow(prefs.getString(KEY_LAST_SYNC, "") ?: "")
    val lastSyncTime: StateFlow<String> get() = _lastSyncTime

    val hasUnsyncedData = MutableStateFlow(false)

    /**
     * Push complet vers le serveur. Mutex anti-concurrence + snackbars
     * UX (start/end). Delegue le travail metier au SyncEngine.
     */
    suspend fun syncAllToServer() = withContext(Dispatchers.IO) @androidx.annotation.RequiresPermission(
        android.Manifest.permission.POST_NOTIFICATIONS
    ) {
        if (TokenManager.token == null) {
            showSnackbar(
                message = context.getString(com.example.sportapp.R.string.sync_no_token),
                type = SnackbarType.WARNING,
                duration = SnackbarDuration.Short,
            )
            return@withContext
        }

        if (!syncMutex.tryLock()) {
            showSnackbar(
                message = context.getString(com.example.sportapp.R.string.sync_in_progress),
                type = SnackbarType.WARNING,
                duration = SnackbarDuration.Short,
            )
            return@withContext
        }

        showSnackbar(
            message = context.getString(com.example.sportapp.R.string.sync_starting),
            type = SnackbarType.INFO,
            duration = SnackbarDuration.Short,
        )
        try {
            syncEngine.pushAll(log = true)

            // Succes uniquement : on ne veut pas horodater une tentative echouee.
            saveLastSyncTime()
            Log.d("SyncManager", "✅ Automatic synchronization completed.")
            withContext(Dispatchers.Main) {
                showSnackbar(
                    message = context.getString(com.example.sportapp.R.string.sync_completed),
                    type = SnackbarType.INFO,
                    duration = SnackbarDuration.Short,
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("SyncManager", "❌ Error during automatic synchronization : ${e.message}")
            withContext(Dispatchers.Main) {
                showSnackbar(
                    message = context.getString(com.example.sportapp.R.string.sync_error),
                    type = SnackbarType.ERROR,
                )
            }
        } finally {
            syncMutex.unlock()
        }
    }

    /**
     * Detecte si au moins une entite syncable a des rows `synced=false`.
     * Itere sur le SyncRegistry — `hasUnsynced()` Room utilise SQL `LIMIT 1`
     * (efficace, pas de scan complet).
     */
    suspend fun checkForUnsyncedData() {
        val anyUnsynced = registry.all.any { it.hasUnsynced() }
        hasUnsyncedData.emit(anyUnsynced)
    }

    private fun saveLastSyncTime() {
        val now = getNowISO8601()
        _lastSyncTime.value = now
        prefs.edit { putString(KEY_LAST_SYNC, now) }
    }

    private companion object {
        const val PREFS_SYNC = "sync_prefs"
        const val KEY_LAST_SYNC = "last_sync_time"
    }
}
