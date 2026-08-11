package com.example.sportapp.core.domain.routines

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.sportapp.core.data.model.RoutinePeriod
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Type de rappel d'une période : avant le début (ROUTINE_PERIOD_START) ou
 *  avant la fin (ROUTINE_PERIOD_END). */
enum class PeriodReminderKind { START, END }

/**
 * 2026-06-08 : schedule/cancel les rappels d'une [RoutinePeriod] via WorkManager,
 * en miroir de [com.example.sportapp.core.domain.tasks.TaskReminderScheduler].
 *
 * Une période n'a pas de récurrence explicite : elle se répète CHAQUE jour à
 * [RoutinePeriod.startTime] / [RoutinePeriod.endTime] ("HH:mm"). On planifie 2
 * rappels indépendants (et/ou) :
 *   - avant le DÉBUT  : si [RoutinePeriod.reminderBeforeStartMinutes] != null
 *   - avant la FIN    : si [RoutinePeriod.reminderBeforeEndMinutes]   != null
 * Convention de l'offset : null = désactivé, 0 = pile à l'heure, N = N min avant.
 *
 * Un seul Worker ([RoutinePeriodStartWorker]) paramétré par `KEY_KIND`, + 2
 * unique-works par période (préfixes distincts). La re-planification au jour
 * suivant est assurée par le Worker qui rappelle [schedule] après avoir tiré la
 * notif. WorkRequest tagged + nom unique par (period.uuid, kind).
 */
@Singleton
class RoutinePeriodStartScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Annule + (re)planifie les 2 rappels (début / fin) pour cette période.
     * Chaque rappel n'est planifié que si son offset est non-null (>= 0) ; sinon
     * le work correspondant est annulé ("Aucun" = off par élément). Idempotent.
     */
    fun schedule(period: RoutinePeriod) {
        scheduleKind(period, PeriodReminderKind.START, period.reminderBeforeStartMinutes, period.startTime)
        scheduleKind(period, PeriodReminderKind.END, period.reminderBeforeEndMinutes, period.endTime)
    }

    private fun scheduleKind(
        period: RoutinePeriod,
        kind: PeriodReminderKind,
        offsetMin: Int?,
        baseTimeStr: String,
    ) {
        val workName = workNameFor(period.uuid, kind)
        val workManager = WorkManager.getInstance(context)

        // Rappel désactivé (null) / période en suppression / offset négatif -> cancel.
        if (period.pendingDeletion || offsetMin == null || offsetMin < 0) {
            workManager.cancelUniqueWork(workName)
            return
        }

        val baseTime = parseTime(baseTimeStr) ?: run {
            workManager.cancelUniqueWork(workName)
            Log.d(TAG, "scheduleKind: bad time '$baseTimeStr' for ${period.uuid}/$kind, skip")
            return
        }

        val nextTrigger = nextTriggerDateTime(baseTime, offsetMin)
        // L'occurrence (instant réel de l'évènement) = trigger + offset. C'est elle
        // qu'on passe au worker (la date du trigger peut être la veille à offset > 0).
        val occurrenceDate = nextTrigger.plusMinutes(offsetMin.toLong()).toLocalDate()

        val now = LocalDateTime.now()
        val delayMs = Duration.between(now, nextTrigger).toMillis().coerceAtLeast(0L)

        val request = OneTimeWorkRequestBuilder<RoutinePeriodStartWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(RoutinePeriodStartWorker.KEY_PERIOD_UUID, period.uuid)
                    .putString(RoutinePeriodStartWorker.KEY_KIND, kind.name)
                    .putString(RoutinePeriodStartWorker.KEY_OCCURRENCE_DATE, occurrenceDate.toString())
                    .build()
            )
            .addTag(RoutinePeriodStartWorker.TAG_REMINDER)
            .build()

        // REPLACE suffit à dédupliquer : pas de cancel explicite préalable, ce qui
        // permet au Worker de rappeler schedule() pour le jour suivant sans
        // s'auto-annuler en cours d'exécution.
        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
        Log.d(
            TAG,
            "scheduled period-$kind for ${period.uuid} name='${period.name}' " +
                "trigger=$nextTrigger occurrence=$occurrenceDate (delay ${delayMs / 1000}s, offset $offsetMin)"
        )
    }

    /** (Re)planifie toutes les périodes actives. Bootstrap à l'ouverture de l'écran. */
    fun scheduleAll(periods: List<RoutinePeriod>) {
        periods.forEach { schedule(it) }
    }

    /** Annule les DEUX rappels (début + fin) -- utilisé à la suppression d'une période. */
    fun cancel(periodUuid: String) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(workNameFor(periodUuid, PeriodReminderKind.START))
        workManager.cancelUniqueWork(workNameFor(periodUuid, PeriodReminderKind.END))
        Log.d(TAG, "cancelled period reminders (start+end) for $periodUuid")
    }

    /**
     * Prochain INSTANT DE DÉCLENCHEMENT (= occurrence − offset) strictement > now.
     *
     * 🔴 On calcule le TRIGGER, pas l'occurrence : à offset > 0, le worker doit
     * tirer à `baseTime − offset`. Si on branchait sur l'occurrence (baseTime), le
     * worker fire à baseTime−offset alors que l'occurrence est encore future → il
     * re-planifie le même jour → boucle de notifs. En comparant le trigger à now,
     * on roule proprement au jour suivant quand le trigger d'aujourd'hui est passé.
     *
     * La boucle (vs un simple +1 jour) borne le cas limite où l'offset fait passer
     * le trigger d'aujourd'hui avant minuit : un seul +1 jour pourrait rester dans
     * le passé. Elle termine toujours (occ avance d'1 jour, offset fini).
     */
    internal fun nextTriggerDateTime(baseTime: LocalTime, offsetMin: Int): LocalDateTime {
        val now = LocalDateTime.now()
        var occ = LocalDateTime.of(LocalDate.now(), baseTime)
        while (true) {
            val trigger = occ.minusMinutes(offsetMin.toLong())
            if (trigger.isAfter(now)) return trigger
            occ = occ.plusDays(1)
        }
    }

    internal fun parseTime(s: String?): LocalTime? =
        s?.let { runCatching { LocalTime.parse(it, TIME_FORMAT) }.getOrNull() }

    companion object {
        private const val TAG = "RoutinePeriodStartSched"
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun workNameFor(periodUuid: String, kind: PeriodReminderKind): String =
            when (kind) {
                PeriodReminderKind.START -> "${RoutinePeriodStartWorker.WORK_NAME_PREFIX_START}$periodUuid"
                PeriodReminderKind.END -> "${RoutinePeriodStartWorker.WORK_NAME_PREFIX_END}$periodUuid"
            }
    }
}
