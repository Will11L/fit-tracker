package com.example.sportapp.core.domain.routines

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.sportapp.R
import com.example.sportapp.core.data.local.RoutinePeriodDao
import com.example.sportapp.feature.notifications.data.NotificationRepository
import com.example.sportapp.feature.notifications.domain.NotificationCenter
import com.example.sportapp.feature.notifications.utils.NotificationType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 2026-06-08 : Worker qui tire une notification système + persiste in-app quand
 * un rappel de [com.example.sportapp.core.data.model.RoutinePeriod] se déclenche.
 * Un seul Worker paramétré par `KEY_KIND` couvre les 2 rappels :
 *   - START -> ROUTINE_PERIOD_START (avant le début)
 *   - END   -> ROUTINE_PERIOD_END   (avant la fin)
 * Schedule via [RoutinePeriodStartScheduler].
 *
 * Input data :
 *   - "periodUuid"     : str — l'UUID de la RoutinePeriod concernée
 *   - "kind"           : str — "START" | "END"
 *   - "occurrenceDate" : str — date d'occurrence (YYYY-MM-DD)
 *
 * Une période se répète chaque jour : après avoir tiré la notif, le Worker rappelle
 * [RoutinePeriodStartScheduler.schedule] qui re-planifie les 2 rappels du lendemain
 * (REPLACE idempotent). Si la période n'existe plus / est pendingDeletion au moment
 * du fire → no-op, pas de re-planification.
 */
@HiltWorker
class RoutinePeriodStartWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val periodDao: RoutinePeriodDao,
    private val notifRepo: NotificationRepository,
    private val notifCenter: NotificationCenter,
    private val scheduler: RoutinePeriodStartScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val periodUuid = inputData.getString(KEY_PERIOD_UUID) ?: return Result.failure()
        val occurrenceDate = inputData.getString(KEY_OCCURRENCE_DATE) ?: ""
        val kind = inputData.getString(KEY_KIND) ?: KIND_START

        return try {
            val period = periodDao.getByUUID(periodUuid)
            if (period == null || period.pendingDeletion) {
                Log.d(TAG, "Period $periodUuid absent/pendingDeletion, skip (no reschedule)")
                return Result.success()
            }

            val isEnd = kind == KIND_END
            val notif = notifRepo.build(
                userId = period.userId,
                type = if (isEnd) NotificationType.ROUTINE_PERIOD_END else NotificationType.ROUTINE_PERIOD_START,
                title = applicationContext.getString(
                    if (isEnd) R.string.notif_routine_period_end_title
                    else R.string.notif_routine_period_start_title
                ),
                body = applicationContext.getString(
                    if (isEnd) R.string.notif_routine_period_end_body
                    else R.string.notif_routine_period_start_body,
                    period.name,
                ),
                data = mapOf(
                    "periodUuid" to period.uuid,
                    "occurrenceDate" to occurrenceDate,
                    "kind" to kind,
                    "screen" to "tasks",
                ),
            )
            notifCenter.post(notif, showOverlay = false)
            Log.d(TAG, "Period-$kind fired for=$periodUuid name=${period.name}")

            // Re-planifie les rappels du lendemain (période quotidienne). schedule()
            // re-planifie les 2 rappels (REPLACE idempotent).
            scheduler.schedule(period)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "RoutinePeriodStartWorker failed for $periodUuid: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "RoutinePeriodStartWorker"
        const val KEY_PERIOD_UUID = "periodUuid"
        const val KEY_OCCURRENCE_DATE = "occurrenceDate"
        const val KEY_KIND = "kind"
        const val KIND_START = "START"
        const val KIND_END = "END"
        const val WORK_NAME_PREFIX_START = "routine_period_start_"
        const val WORK_NAME_PREFIX_END = "routine_period_end_"
        const val TAG_REMINDER = "routine_period_reminder"
    }
}
