package com.example.sportapp.feature.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val VIBRATE = booleanPreferencesKey("vibrate_on_in_app_notification")
        val SOUND = booleanPreferencesKey("sound_on_in_app_notification")
        val OVERLAY = booleanPreferencesKey("show_in_app_notification_overlay")
        val PHONE = booleanPreferencesKey("show_phone_notifications")
        val NOTIFY_TASKS = booleanPreferencesKey("notify_tasks")
        val NOTIFY_TIMERS = booleanPreferencesKey("notify_timers")
        val NOTIFY_ROUTINES = booleanPreferencesKey("notify_routines")
        val DEFAULT_REMINDER = intPreferencesKey("default_reminder_minutes_before")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            vibrateOnInAppNotification = prefs[Keys.VIBRATE] ?: true,
            soundOnInAppNotification = prefs[Keys.SOUND] ?: false,
            showInAppNotificationOverlay = prefs[Keys.OVERLAY] ?: true,
            showPhoneNotifications = prefs[Keys.PHONE] ?: true,
            notifyTasks = prefs[Keys.NOTIFY_TASKS] ?: true,
            notifyTimers = prefs[Keys.NOTIFY_TIMERS] ?: true,
            notifyRoutines = prefs[Keys.NOTIFY_ROUTINES] ?: true,
            // Convention : clé absente -> défaut usine 15 ; sentinelle -1 = "Aucun"
            // (null) choisi explicitement par l'user ; sinon la valeur stockée.
            defaultReminderMinutesBefore = prefs[Keys.DEFAULT_REMINDER].let { raw ->
                when {
                    raw == null -> 15
                    raw < 0 -> null
                    else -> raw
                }
            }
        )
    }

    suspend fun setVibrate(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIBRATE] = enabled }
    }

    suspend fun setSound(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOUND] = enabled }
    }

    suspend fun setOverlay(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OVERLAY] = enabled }
    }

    suspend fun setPhone(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PHONE] = enabled }
    }

    suspend fun setNotifyTasks(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_TASKS] = enabled }
    }

    suspend fun setNotifyTimers(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_TIMERS] = enabled }
    }

    suspend fun setNotifyRoutines(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_ROUTINES] = enabled }
    }

    /** null = "Aucun" (stocké -1) ; sinon les minutes. */
    suspend fun setDefaultReminder(minutes: Int?) {
        context.dataStore.edit { it[Keys.DEFAULT_REMINDER] = minutes ?: -1 }
    }
}
