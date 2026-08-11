package com.example.sportapp.feature.onboarding.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository pour l'onboarding B1.
 * - Flag "done" par user via SharedPreferences (clé `onboarding_done_user_<userId>`).
 *   Permet de distinguer plusieurs comptes sur un même device.
 * - Préférences UI persistées via DataStore (cf. OnboardingDataStore).
 */
@Singleton
class OnboardingRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    val dataStore: OnboardingDataStore,
) {

    private val flagPrefs = appContext.getSharedPreferences("onboarding_flags", Context.MODE_PRIVATE)

    fun isDone(userId: Int): Boolean = flagPrefs.getBoolean(keyFor(userId), false)

    fun markDone(userId: Int) {
        flagPrefs.edit { putBoolean(keyFor(userId), true) }
    }

    fun reset(userId: Int) {
        flagPrefs.edit { remove(keyFor(userId)) }
    }

    /** Reset complet pour re-tester onboarding from scratch :
     *  - clear le flag done de l'user
     *  - wipe les preferences DataStore (theme/locale/units/etc reviennent aux defaults SYSTEM) */
    suspend fun resetForUser(userId: Int) {
        reset(userId)
        dataStore.resetAll()
    }

    val preferences: kotlinx.coroutines.flow.Flow<OnboardingPreferences> = dataStore.preferencesFlow

    suspend fun setPreferences(prefs: OnboardingPreferences) = dataStore.setPreferences(prefs)

    suspend fun setWeightUnit(unit: WeightUnit) = dataStore.setWeightUnit(unit)

    suspend fun setLengthUnit(unit: LengthUnit) = dataStore.setLengthUnit(unit)

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.setThemeMode(mode)

    suspend fun setAppLocale(locale: AppLocale) = dataStore.setAppLocale(locale)

    suspend fun setStartScreen(screen: StartScreen) = dataStore.setStartScreen(screen)

    private fun keyFor(userId: Int) = "onboarding_done_user_$userId"
}
