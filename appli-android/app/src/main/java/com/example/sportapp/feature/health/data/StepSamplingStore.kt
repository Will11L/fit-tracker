package com.example.sportapp.feature.health.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.sportapp.feature.health.domain.StepSamplingLogic.SamplingState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.stepSamplingDataStore by preferencesDataStore(name = "health_step_sampling")

/**
 * Persiste le réglage utilisateur de l'échantillonnage des pas (toggle Settings) et
 * l'état de l'échantillonneur ([SamplingState] : dernier relevé). DataStore dédié
 * (séparé de l'onboarding) car l'état est écrit par le worker en arrière-plan.
 */
@Singleton
class StepSamplingStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val STATE_DATE = stringPreferencesKey("state_date")
        val STATE_LAST_TOTAL = intPreferencesKey("state_last_total")
        val STATE_OPEN_SLOT = stringPreferencesKey("state_open_slot")
        val STATE_OPEN_BASE = intPreferencesKey("state_open_base")
    }

    /** Toggle : l'échantillonnage est-il activé (observable pour l'UI Settings) ? */
    val enabledFlow: Flow<Boolean> = context.stepSamplingDataStore.data.map { it[Keys.ENABLED] ?: false }

    suspend fun isEnabled(): Boolean = enabledFlow.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.stepSamplingDataStore.edit { it[Keys.ENABLED] = enabled }
    }

    /** État du dernier relevé (defaults = jamais relevé → 1er relevé = rattrapage). */
    suspend fun readState(): SamplingState {
        val prefs = context.stepSamplingDataStore.data.first()
        return SamplingState(
            date = prefs[Keys.STATE_DATE] ?: "",
            lastTotal = prefs[Keys.STATE_LAST_TOTAL] ?: 0,
            openSlot = prefs[Keys.STATE_OPEN_SLOT] ?: "",
            openSlotBase = prefs[Keys.STATE_OPEN_BASE] ?: 0,
        )
    }

    suspend fun writeState(state: SamplingState) {
        context.stepSamplingDataStore.edit {
            it[Keys.STATE_DATE] = state.date
            it[Keys.STATE_LAST_TOTAL] = state.lastTotal
            it[Keys.STATE_OPEN_SLOT] = state.openSlot
            it[Keys.STATE_OPEN_BASE] = state.openSlotBase
        }
    }

    /** Réinitialise l'état (désactivation) → la prochaine activation repart en rattrapage. */
    suspend fun clearState() {
        context.stepSamplingDataStore.edit {
            it.remove(Keys.STATE_DATE)
            it.remove(Keys.STATE_LAST_TOTAL)
            it.remove(Keys.STATE_OPEN_SLOT)
            it.remove(Keys.STATE_OPEN_BASE)
        }
    }
}
