package com.example.sportapp.feature.demo_tour.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flag "demo tour active" persisté per-user via SharedPreferences.
 *
 * Cycle de vie :
 * 1. Au Finish onboarding, si toggle ON : `markTourActive(userId)` + insertSampleWorkouts.
 * 2. Sessions 2+ ajouteront `markTourDone(userId)` à la fin du tour visuel.
 * 3. Au prochain ColdStart : `SplashScreenViewModel` regarde si flag actif → cleanup
 *    les sample data + `clearTourActive(userId)`. Crash-safe : si l'app crashe
 *    pendant le tour, le flag survit et le cleanup tourne au démarrage suivant.
 *
 * Per-user (clé inclut `userId`) pour distinguer plusieurs comptes sur même device.
 */
@Singleton
class DemoTourRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {

    private val prefs = appContext.getSharedPreferences("demo_tour_flags", Context.MODE_PRIVATE)

    fun isTourActive(userId: Int): Boolean = prefs.getBoolean(keyFor(userId), false)

    fun markTourActive(userId: Int) {
        prefs.edit { putBoolean(keyFor(userId), true) }
    }

    fun clearTourActive(userId: Int) {
        prefs.edit { remove(keyFor(userId)) }
    }

    private fun keyFor(userId: Int) = "demo_tour_active_user_$userId"
}
