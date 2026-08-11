package com.example.sportapp.feature.settings

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettingsRepository @Inject constructor(
    private val store: SettingsDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: StateFlow<AppSettings> =
        store.settingsFlow.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, AppSettings())

    suspend fun setVibrateOnInAppNotification(enabled: Boolean) = store.setVibrate(enabled)
    suspend fun setSoundOnInAppNotification(enabled: Boolean) = store.setSound(enabled)
    suspend fun setShowInAppNotificationOverlay(enabled: Boolean) = store.setOverlay(enabled)
    suspend fun setShowPhoneNotifications(enabled: Boolean) = store.setPhone(enabled)
    suspend fun setNotifyTasks(enabled: Boolean) = store.setNotifyTasks(enabled)
    suspend fun setNotifyTimers(enabled: Boolean) = store.setNotifyTimers(enabled)
    suspend fun setNotifyRoutines(enabled: Boolean) = store.setNotifyRoutines(enabled)
    suspend fun setDefaultReminderMinutesBefore(minutes: Int?) = store.setDefaultReminder(minutes)
}
