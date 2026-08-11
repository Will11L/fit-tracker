package com.example.sportapp.feature.health.data

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Planifie / annule le worker périodique d'échantillonnage des pas ([StepSamplingWorker])
 * via WorkManager, en miroir de [com.example.sportapp.core.domain.routines.RoutinePeriodStartScheduler].
 *
 * [enable] enfile un travail périodique (cadence [SAMPLE_INTERVAL_MIN] min, minimum
 * WorkManager) + un relevé immédiat one-time (rattrapage rapide au moment de
 * l'activation, sans attendre le 1er cycle). [disable] annule le tout.
 */
@Singleton
class StepSamplingScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun enable() {
        val wm = WorkManager.getInstance(context)
        val periodic = PeriodicWorkRequestBuilder<StepSamplingWorker>(SAMPLE_INTERVAL_MIN, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, periodic)
        // Relevé immédiat pour le rattrapage à l'activation (le périodique ne fire pas tout de suite).
        val kick = OneTimeWorkRequestBuilder<StepSamplingWorker>().addTag(TAG).build()
        wm.enqueueUniqueWork(WORK_NAME_KICK, ExistingWorkPolicy.REPLACE, kick)
    }

    fun disable() {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(WORK_NAME)
        wm.cancelUniqueWork(WORK_NAME_KICK)
    }

    companion object {
        const val WORK_NAME = "health_step_sampling"
        const val WORK_NAME_KICK = "health_step_sampling_kick"
        const val TAG = "health_sampling"
        const val SAMPLE_INTERVAL_MIN = 15L // minimum WorkManager pour un travail périodique
    }
}
