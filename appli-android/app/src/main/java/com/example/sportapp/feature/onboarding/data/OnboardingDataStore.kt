package com.example.sportapp.feature.onboarding.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding_settings")

class OnboardingDataStore(private val context: Context) {

    private object Keys {
        val WEEK_START = stringPreferencesKey("week_start")
        val MORNING_HOUR = intPreferencesKey("morning_routine_hour")
        val MORNING_MINUTE = intPreferencesKey("morning_routine_minute")
        val AUTO_SYNC_WIFI = booleanPreferencesKey("auto_sync_on_wifi")
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val LENGTH_UNIT = stringPreferencesKey("length_unit")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_LOCALE = stringPreferencesKey("app_locale")
        val RUN_DEMO_TOUR = booleanPreferencesKey("run_demo_tour")
        val START_SCREEN = stringPreferencesKey("start_screen")
    }

    /** Flag "onboarding done" est par-user (persisté via SharedPreferences classique
     *  dans `OnboardingRepository` car CurrentUserManager pattern + cleanup au logout). */

    val preferencesFlow: Flow<OnboardingPreferences> = context.onboardingDataStore.data.map { prefs ->
        OnboardingPreferences(
            weekStart = prefs[Keys.WEEK_START]
                ?.let { runCatching { WeekStart.valueOf(it) }.getOrNull() }
                ?: WeekStart.MONDAY,
            morningRoutineHour = prefs[Keys.MORNING_HOUR] ?: 6,
            morningRoutineMinute = prefs[Keys.MORNING_MINUTE] ?: 0,
            autoSyncOnWifi = prefs[Keys.AUTO_SYNC_WIFI] ?: true,
            weightUnit = prefs[Keys.WEIGHT_UNIT]
                ?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() }
                ?: WeightUnit.KG,
            lengthUnit = prefs[Keys.LENGTH_UNIT]
                ?.let { runCatching { LengthUnit.valueOf(it) }.getOrNull() }
                ?: LengthUnit.CM,
            themeMode = prefs[Keys.THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            appLocale = prefs[Keys.APP_LOCALE]
                ?.let { runCatching { AppLocale.valueOf(it) }.getOrNull() }
                ?: AppLocale.SYSTEM,
            runDemoTour = prefs[Keys.RUN_DEMO_TOUR] ?: true,
            startScreen = prefs[Keys.START_SCREEN]
                ?.let { runCatching { StartScreen.valueOf(it) }.getOrNull() }
                ?: StartScreen.HOME,
        )
    }

    suspend fun snapshot(): OnboardingPreferences = preferencesFlow.first()

    suspend fun setPreferences(prefs: OnboardingPreferences) {
        context.onboardingDataStore.edit {
            it[Keys.WEEK_START] = prefs.weekStart.name
            it[Keys.MORNING_HOUR] = prefs.morningRoutineHour
            it[Keys.MORNING_MINUTE] = prefs.morningRoutineMinute
            it[Keys.AUTO_SYNC_WIFI] = prefs.autoSyncOnWifi
            it[Keys.WEIGHT_UNIT] = prefs.weightUnit.name
            it[Keys.LENGTH_UNIT] = prefs.lengthUnit.name
            it[Keys.THEME_MODE] = prefs.themeMode.name
            it[Keys.APP_LOCALE] = prefs.appLocale.name
            it[Keys.RUN_DEMO_TOUR] = prefs.runDemoTour
            it[Keys.START_SCREEN] = prefs.startScreen.name
        }
    }

    /** Setters live (Settings post-onboarding -- pas de draft à confirmer). */
    suspend fun setWeightUnit(unit: WeightUnit) {
        context.onboardingDataStore.edit { it[Keys.WEIGHT_UNIT] = unit.name }
    }

    suspend fun setLengthUnit(unit: LengthUnit) {
        context.onboardingDataStore.edit { it[Keys.LENGTH_UNIT] = unit.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.onboardingDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAppLocale(locale: AppLocale) {
        context.onboardingDataStore.edit { it[Keys.APP_LOCALE] = locale.name }
    }

    suspend fun setStartScreen(screen: StartScreen) {
        context.onboardingDataStore.edit { it[Keys.START_SCREEN] = screen.name }
    }

    /** Wipe toutes les preferences -> au prochain read, les defaults de
     *  [OnboardingPreferences] s'appliquent (themeMode=SYSTEM, appLocale=SYSTEM, etc). */
    suspend fun resetAll() {
        context.onboardingDataStore.edit { it.clear() }
    }
}
