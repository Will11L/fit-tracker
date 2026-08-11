package com.example.sportapp.core.domain.tasks

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.sportapp.core.data.model.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3 (2026-05-12) : schedule/cancel les rappels Task via WorkManager.
 *
 * Pour chaque Task avec [Task.reminderMinutesBefore] != null :
 *   - Calcule la prochaine occurrence (next upcoming, pas passe)
 *   - Schedule un OneTimeWorkRequest avec initialDelay = next - reminderMinutesBefore
 *   - WorkRequest tagged avec task.uuid (pour cancel/replace propre)
 *   - ExistingWorkPolicy.REPLACE : si on re-schedule (update task), l'ancien
 *     est annule + nouveau remplace.
 *
 * Si la prochaine occurrence est dans le passe ou si reminderMinutesBefore=null,
 * on cancel sans rescheduler.
 */
@Singleton
class TaskReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Annule + schedule le rappel pour cette task. Idempotent. */
    fun schedule(task: Task) {
        val workName = workNameFor(task.uuid)
        val workManager = WorkManager.getInstance(context)

        // Cancel preexisting (idempotence)
        workManager.cancelUniqueWork(workName)

        val minutesBefore = task.reminderMinutesBefore
        if (minutesBefore == null || minutesBefore < 0) {
            Log.d(TAG, "schedule: no reminder for task ${task.uuid}")
            return
        }
        if (task.pendingDeletion || !task.isActive) {
            Log.d(TAG, "schedule: task ${task.uuid} inactive/pending, skip")
            return
        }

        val nextOccurrence = nextUpcomingDateTime(task) ?: run {
            Log.d(TAG, "schedule: no upcoming occurrence for ${task.uuid}, skip")
            return
        }

        val reminderTime = nextOccurrence.minusMinutes(minutesBefore.toLong())
        val now = LocalDateTime.now()
        if (reminderTime.isBefore(now)) {
            Log.d(TAG, "schedule: reminder time already passed for ${task.uuid}, skip")
            return
        }

        val delayMs = java.time.Duration.between(now, reminderTime).toMillis()

        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(TaskReminderWorker.KEY_TASK_UUID, task.uuid)
                    .putString(
                        TaskReminderWorker.KEY_OCCURRENCE_DATE,
                        nextOccurrence.toLocalDate().toString()
                    )
                    .build()
            )
            .addTag(TaskReminderWorker.TAG_REMINDER)
            .build()

        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
        Log.d(
            TAG,
            "scheduled reminder for ${task.uuid} title='${task.title}' " +
                "at $reminderTime (delay ${delayMs / 1000}s, occurrence $nextOccurrence)"
        )
    }

    /** Annule le rappel (utilise au delete). */
    fun cancel(taskUuid: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workNameFor(taskUuid))
        Log.d(TAG, "cancelled reminder for $taskUuid")
    }

    /**
     * Calcule la prochaine occurrence (date + time) >= now pour cette Task,
     * ou null si aucune (passee, expiree, ou no recurrence).
     *
     * Pour NONE : task.dueDate + task.dueTime (default 09:00 si dueTime null).
     * Pour DAILY : aujourd'hui ou demain selon dueTime vs maintenant.
     * Pour WEEKLY/MONTHLY/YEARLY : utilise ScheduledTaskExpander dans un range
     * raisonnable (max 1 an dans le futur).
     */
    internal fun nextUpcomingDateTime(task: Task): LocalDateTime? {
        val time = parseTime(task.dueTime) ?: DEFAULT_REMINDER_TIME
        val today = LocalDate.now(ZoneId.systemDefault())

        // Range "next 366 days" max (eviter loop infini si rien trouve).
        val maxRange = today.plusDays(366)

        val candidates: List<LocalDate> = when (task.recurrenceKind) {
            "NONE" -> {
                val due = task.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: return null
                if (due < today) emptyList() else listOf(due)
            }
            "DAILY" -> {
                // DAILY : aujourd'hui + 1 prochain jour (pour gerer le cas ou
                // l'heure aujourd'hui est passee, on regarde demain).
                listOf(today, today.plusDays(1))
            }
            "WEEKLY", "MONTHLY", "YEARLY" -> {
                ScheduledTaskExpander.occurrencesInRange(task, today, maxRange)
            }
            else -> return null
        }

        val now = LocalDateTime.now()
        return candidates
            .map { LocalDateTime.of(it, time) }
            .firstOrNull { !it.isBefore(now) }
    }

    private fun parseTime(s: String?): LocalTime? =
        s?.let { runCatching { LocalTime.parse(it, TIME_FORMAT) }.getOrNull() }

    companion object {
        private const val TAG = "TaskReminderScheduler"
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        /** Si la task n'a pas de dueTime explicite, on suppose 09:00. */
        private val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

        fun workNameFor(taskUuid: String) = "${TaskReminderWorker.WORK_NAME_PREFIX}$taskUuid"
    }
}
