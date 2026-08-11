package com.example.sportapp.feature.chrono.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChronoSettingsRepository @Inject constructor(
    private val store: ChronoSettingsDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: StateFlow<ChronoSettings> =
        store.settingsFlow.stateIn(scope, SharingStarted.Eagerly, ChronoSettings())

    suspend fun setLastTimer(name: String, durationMillis: Long) =
        store.setLastTimer(name, durationMillis)

    suspend fun setLastActiveTab(tab: ChronoTab) =
        store.setLastActiveTab(tab)
}
