package com.example.sportapp.feature.settings.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.local.UserDao
import com.example.sportapp.core.data.remote.WebSocketManager
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.RemoteDataGetter
import com.example.sportapp.core.sync.RemoteDataMerger
import com.example.sportapp.core.sync.RemoteDataUpserter
import com.example.sportapp.core.sync.SyncCoordinator
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.core.sync.SyncManager
import com.example.sportapp.core.sync.SyncRegistry
import com.example.sportapp.core.sync.base.EntityStats
import com.example.sportapp.core.sync.base.observeStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject


data class TableConfig(
    val entitiesFlow: Flow<List<Any>>,
    val tableName: String,
    val iconRes: Int,
    /** Flow des compteurs (total / unsynced / pendingDeletion). Null pour User (read-only). */
    val statsFlow: Flow<EntityStats>? = null,
)

/**
 * ViewModel de l'ecran Settings (outils de test sync + log DB).
 *
 * T4.2 Phase 2.4 (2026-05-07) : refactor majeur. Les 5 methodes qui faisaient
 * 19+ cas `when` ou 21 lignes dispatch DAO (loadLocalTables, logLocalDatabase,
 * clearAllLocalData, syncEntity, deleteEntity) iterent maintenant sur le
 * SyncRegistry. Volume : 470 -> ~150 lignes. Ajouter une 21e entite syncable
 * = 0 changement ici (auto-pickup via registry).
 *
 * User (read-only client) reste un cas special hors registry : son TableConfig
 * + clearAll sont ajoutes manuellement.
 */
@HiltViewModel
class SyncSettingsViewModel @Inject constructor(
    private val userDao: UserDao,
    private val registry: SyncRegistry,
    private val syncEngine: SyncEngine,

    // Conserves pour les boutons Settings publics (semantique inchangee) :
    private val remoteDataGetter: RemoteDataGetter,
    private val remoteDataUpserter: RemoteDataUpserter,
    private val remoteDataMerger: RemoteDataMerger,
    private val syncManager: SyncManager,
    private val syncCoordinator: SyncCoordinator,

    private val wsManager: WebSocketManager,
) : ViewModel() {

    val isWsConnected: StateFlow<Boolean> = wsManager.isConnected
    val isTokenValid = RetrofitInstance.isTokenValid
    val hasUnsyncedData: StateFlow<Boolean> = syncManager.hasUnsyncedData

    private val _tables = MutableStateFlow<Map<String, TableConfig>>(emptyMap())
    val tables: StateFlow<Map<String, TableConfig>> = _tables

    /**
     * Genere les TableConfig a partir du SyncRegistry. Une 21e entite syncable
     * apparait automatiquement ici sans modif. User est exclu : read-only, 1 row,
     * aucun intérêt debug dans la data grid (cf. T-sync-grid 2026-05-26).
     */
    fun loadLocalTables() {
        _tables.value = registry.all.associate { syncable ->
            syncable.entityName to TableConfig(
                entitiesFlow = syncable.observeAll().map { it as List<Any> },
                tableName = syncable.displayName,
                iconRes = syncable.iconRes,
                statsFlow = syncable.observeStats(),
            )
        }
    }

    fun checkUnsynced() {
        viewModelScope.launch {
            syncManager.checkForUnsyncedData()
        }
    }

    fun syncAllData() {
        viewModelScope.launch {
            try {
                syncCoordinator.onUserAction()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getAllTablesFromServer(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = remoteDataGetter.getAllAsUnsynced(log = true)
            onResult(success)
        }
    }

    fun upsertAllTablesToServer(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = remoteDataUpserter.upsertAllData(log = false)
            onResult(success)
        }
    }

    /**
     * Dump structure de toutes les tables locales dans Logcat (outil dev).
     * Itere sur registry.all + ajoute User en suffixe.
     */
    fun logLocalDatabase() {
        viewModelScope.launch {
            val tag = "LocalDB"
            Log.d(tag, "--- Base locale ---")
            registry.all.forEach { syncable ->
                Log.d(tag, "${syncable.entityName}: ${syncable.observeAll().first()}")
            }
            Log.d(tag, "Users: ${userDao.observeAll().first()}")
            Log.d(tag, "Fin du log.")
        }
    }

    /**
     * Truncate de toutes les tables locales (outil dev "Clear DB"). Ordre
     * inverse FK (registry.reversed) + User en dernier (read-only mais Room
     * a une FK depuis user_id sur les tables sync donc User en dernier OK).
     */
    fun clearAllLocalData(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                registry.reversed.forEach { it.clearLocal() }
                userDao.clearAll()
                onResult(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    fun mergeAllFromServer(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val success = remoteDataMerger.mergeAllFromServer()
                onResult(success)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    fun verifyToken(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = RetrofitInstance.verifyToken()
            onResult(ok)
        }
    }

    fun restartWebSocket(token: String) {
        wsManager.start(token)
    }
}
