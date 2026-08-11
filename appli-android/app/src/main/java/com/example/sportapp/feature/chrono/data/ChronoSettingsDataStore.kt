package com.example.sportapp.feature.chrono.data

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.chronoDataStore by preferencesDataStore(name = "chrono_settings")

class ChronoSettingsDataStore(private val context: Context) {

    private object Keys {
        val LAST_TIMER_NAME = stringPreferencesKey("last_timer_name")
        val LAST_TIMER_DURATION_MS = longPreferencesKey("last_timer_duration_ms")
        val LAST_ACTIVE_TAB = stringPreferencesKey("last_active_tab")
    }

    val settingsFlow: Flow<ChronoSettings> = context.chronoDataStore.data.map { prefs ->
        ChronoSettings(
            lastTimerName = prefs[Keys.LAST_TIMER_NAME] ?: "",
            lastTimerDurationMillis = prefs[Keys.LAST_TIMER_DURATION_MS] ?: 60_000L,
            lastActiveTab = prefs[Keys.LAST_ACTIVE_TAB]
                ?.let { runCatching { ChronoTab.valueOf(it) }.getOrNull() }
                ?: ChronoTab.STOPWATCH,
        )
    }

    suspend fun setLastTimer(name: String, durationMillis: Long) {
        context.chronoDataStore.edit {
            it[Keys.LAST_TIMER_NAME] = name
            it[Keys.LAST_TIMER_DURATION_MS] = durationMillis
        }
    }

    suspend fun setLastActiveTab(tab: ChronoTab) {
        context.chronoDataStore.edit {
            it[Keys.LAST_ACTIVE_TAB] = tab.name
        }
    }
}
