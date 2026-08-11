package com.example.sportapp.core.domain.tasks

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.sportapp.R
import com.example.sportapp.core.data.local.TaskDao
import com.example.sportapp.feature.notifications.data.NotificationRepository
import com.example.sportapp.feature.notifications.domain.NotificationCenter
import com.example.sportapp.feature.notifications.utils.NotificationType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Phase 3 (2026-05-12) : Worker fire un notification systeme + persiste in-app
 * quand un rappel Task est du. Schedule via TaskReminderScheduler.
 *
 * Input data :
 *   - "taskUuid" : str — l'UUID de la Task concernee
 *   - "occurrenceDate" : str — date d'occurrence (YYYY-MM-DD) pour distinguer
 *     les recurrences (e.g. weekly Lun, mardi suivant, etc.)
 *
 * Si la task n'existe plus (deleted) au moment du fire → no-op.
 */
@HiltWorker
class TaskReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val taskDao: TaskDao,
    private val notifRepo: NotificationRepository,
    private val notifCenter: NotificationCenter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskUuid = inputData.getString(KEY_TASK_UUID) ?: return Result.failure()
        val occurrenceDate = inputData.getString(KEY_OCCURRENCE_DATE) ?: ""

        return try {
            val task = taskDao.getByUUID(taskUuid)
            if (task == null || task.pendingDeletion || !task.isActive) {
                Log.d(TAG, "Task $taskUuid absent/inactive, skip reminder")
                return Result.success()
            }

            val notif = notifRepo.build(
                userId = task.userId,
                type = NotificationType.TASK_REMINDER,
                title = applicationContext.getString(R.string.notif_task_reminder_title),
                body = applicationContext.getString(R.string.notif_task_reminder_body, task.title),
                data = mapOf(
                    "taskUuid" to task.uuid,
                    "occurrenceDate" to occurrenceDate,
                    "screen" to "tasks",
                ),
            )
            notifCenter.post(notif, showOverlay = false)
            Log.d(TAG, "Reminder fired for task=$taskUuid title=${task.title}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "TaskReminderWorker failed for $taskUuid: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "TaskReminderWorker"
        const val KEY_TASK_UUID = "taskUuid"
        const val KEY_OCCURRENCE_DATE = "occurrenceDate"
        const val WORK_NAME_PREFIX = "task_reminder_"
        const val TAG_REMINDER = "task_reminder"
    }
}
