package com.example.sportapp.feature.health.data

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.sportapp.core.data.local.HealthStepCountDao
import com.example.sportapp.core.data.model.HealthStepCount
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.feature.health.domain.HealthConnectMapper
import com.example.sportapp.feature.health.domain.HealthUuids
import com.example.sportapp.feature.health.domain.StepSamplingLogic
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Worker périodique (WorkManager, cf. [StepSamplingScheduler]) qui construit de VRAIES
 * tranches de pas par demi-heure : relève le total de pas du jour dans Health Connect
 * (lecture pleine-journée = total courant exact malgré la proration Samsung des
 * fenêtres partielles) et en déduit la tranche courante via [StepSamplingLogic] (SET
 * télescopant → somme du jour = total Samsung).
 *
 * Au 1er relevé du jour (rattrapage), réutilise le mécanisme tombstone de l'import
 * proraté (commit 6502c08c) : les buckets résiduels du jour sont marqués pendingDeletion
 * avant d'écrire la tranche de rattrapage → pas de double-comptage à la transition.
 *
 * Best-effort : HC indisponible / permission background absente → lecture null → skip
 * (Result.success, état non avancé). Exception inattendue → Result.retry.
 */
@HiltWorker
class StepSamplingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val manager: HealthConnectManager,
    private val stepCountDao: HealthStepCountDao,
    private val store: StepSamplingStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Désactivé entre-temps (toggle off) : no-op même si un cycle périodique fire.
        if (!store.isEnabled()) return Result.success()
        val userId = CurrentUserManager.userId ?: return Result.success()

        return try {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val from = today.atStartOfDay(zone).toInstant()
            val to = today.plusDays(1).atStartOfDay(zone).toInstant()

            // Total courant du jour (pleine-journée = exact). null → HC indispo / pas de
            // permission background → on saute ce relevé sans avancer l'état.
            val total = manager.readDayStepTotal(from, to)?.toInt() ?: return Result.success()

            val now = Instant.now()
            val todayStr = HealthConnectMapper.date(from, zone)
            val nowSlot = HealthConnectMapper.slotStartHhmm(now, zone)
            val step = StepSamplingLogic.next(store.readState(), todayStr, nowSlot, total)

            if (step.resetDay) {
                // Rattrapage : efface les buckets du jour absents de l'écriture (proratés
                // ou résidus d'un crash) pour que la SOMME du jour ne double jamais.
                stepCountDao.getAllOnce()
                    .filter {
                        it.userId == userId && it.date == todayStr &&
                            !it.pendingDeletion && it.bucketStart != step.slot
                    }
                    .forEach { stepCountDao.markAsPendingDeletion(it.uuid) }
            }

            // SET de la tranche (value 0 = pas encore de pas dans la tranche → pas de bucket).
            if (step.value > 0) {
                val uuid = HealthUuids.stepBucket(userId, todayStr, step.slot)
                val existing = stepCountDao.getByUUID(uuid)
                if (existing == null || existing.steps != step.value || existing.pendingDeletion) {
                    stepCountDao.insertAll(
                        listOf(
                            HealthStepCount(
                                uuid = uuid,
                                userId = userId,
                                date = todayStr,
                                bucketStart = step.slot,
                                steps = step.value,
                            ),
                        ),
                    )
                }
            }
            store.writeState(step.newState)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "step sampling failed: ${e.message}")
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "StepSamplingWorker"
    }
}
