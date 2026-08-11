package com.example.sportapp.core.domain.tasks

import com.example.sportapp.core.data.model.Task
import java.time.LocalDate
import java.time.YearMonth

/**
 * Phase 2 (2026-05-12) : genere les occurrences d'une Task recurrente sur un
 * range de dates donne, a la volee (pas stocke en DB).
 *
 * Pour 1 Task recurrence_kind = NONE :
 *   - 1 occurrence unique = task.dueDate
 *
 * Pour 1 Task recurrence_kind = WEEKLY :
 *   - toutes les dates D telles que D.dayOfWeek matche `recurrenceWeekdays`
 *     (encoding Mon=0..Sun=6) ET D >= recurrenceStartDate ET (recurrenceEndDate
 *     null OR D <= recurrenceEndDate).
 *
 * Pour 1 Task recurrence_kind = MONTHLY :
 *   - meme jour-du-mois que recurrenceStartDate, repete chaque mois entre
 *     start et end. Skip silencieusement si dayOfMonth > nbDaysInMonth (ex :
 *     31 en fevrier).
 *
 * Pour 1 Task recurrence_kind = YEARLY :
 *   - meme (mois, jour) que recurrenceStartDate, repete chaque annee entre
 *     start et end.
 *
 * Pour 1 Task recurrence_kind = DAILY :
 *   - non gere par le Calendar (les routines daily restent dans le Daily tab
 *     RoutineTasksScreen). Retourne emptyList si call ici.
 *
 * Pure logic, testable sans Android dependency.
 */
object ScheduledTaskExpander {

    /** Retourne les occurrences d'une Task dans le mois [yearMonth]. */
    fun occurrencesForMonth(task: Task, yearMonth: YearMonth): List<LocalDate> {
        val rangeStart = yearMonth.atDay(1)
        val rangeEnd = yearMonth.atEndOfMonth()
        return occurrencesInRange(task, rangeStart, rangeEnd)
    }

    /** Retourne les occurrences d'une Task entre [from..to] inclus. */
    fun occurrencesInRange(task: Task, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (from > to) return emptyList()
        val raw = when (task.recurrenceKind) {
            "NONE" -> {
                val due = task.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                if (due != null && due in from..to) listOf(due) else emptyList()
            }
            "WEEKLY" -> expandWeekly(task, from, to)
            "MONTHLY" -> expandMonthly(task, from, to)
            "YEARLY" -> expandYearly(task, from, to)
            "DAILY" -> emptyList()  // gere par RoutineTasksScreen, pas Calendar
            else -> emptyList()
        }
        // B.4 (2026-05-12) : filtre les dates exclues (mode "Only this" du
        // dialog edit recurrence). Pour NONE et DAILY, excludedDates est ignore
        // (mais pour NONE le filtrage est inoffensif si la dueDate est exclue --
        // l'user a juste "supprime" cette occurrence ; rarement utile).
        val excluded = task.excludedDates.toSet()
        if (excluded.isEmpty()) return raw
        return raw.filterNot { it.toString() in excluded }
    }

    private fun expandWeekly(task: Task, from: LocalDate, to: LocalDate): List<LocalDate> {
        val weekdays = task.recurrenceWeekdays?.toSet() ?: return emptyList()
        if (weekdays.isEmpty()) return emptyList()
        val start = task.recurrenceStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return emptyList()
        val end = task.recurrenceEndDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        val effectiveFrom = maxOf(from, start)
        val effectiveTo = if (end != null) minOf(to, end) else to
        if (effectiveFrom > effectiveTo) return emptyList()

        // dayOfWeek.value : Mon=1..Sun=7 (ISO). On encode Mon=0..Sun=6.
        val out = mutableListOf<LocalDate>()
        var d = effectiveFrom
        while (d <= effectiveTo) {
            val weekdayIdx = d.dayOfWeek.value - 1   // 0..6
            if (weekdayIdx in weekdays) out.add(d)
            d = d.plusDays(1)
        }
        return out
    }

    private fun expandMonthly(task: Task, from: LocalDate, to: LocalDate): List<LocalDate> {
        val start = task.recurrenceStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return emptyList()
        val end = task.recurrenceEndDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val dayOfMonth = start.dayOfMonth

        val out = mutableListOf<LocalDate>()
        // Iterer mois par mois dans [from..to] et tenter de poser dayOfMonth.
        var ym = YearMonth.from(maxOf(from, start))
        val ymEnd = YearMonth.from(to)
        while (ym <= ymEnd) {
            if (dayOfMonth <= ym.lengthOfMonth()) {
                val d = ym.atDay(dayOfMonth)
                if (d >= start && d in from..to && (end == null || d <= end)) {
                    out.add(d)
                }
            }
            ym = ym.plusMonths(1)
        }
        return out
    }

    private fun expandYearly(task: Task, from: LocalDate, to: LocalDate): List<LocalDate> {
        val start = task.recurrenceStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return emptyList()
        val end = task.recurrenceEndDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val month = start.month
        val dayOfMonth = start.dayOfMonth

        val out = mutableListOf<LocalDate>()
        var year = maxOf(from.year, start.year)
        while (year <= to.year) {
            val ym = YearMonth.of(year, month)
            if (dayOfMonth <= ym.lengthOfMonth()) {
                val d = ym.atDay(dayOfMonth)
                if (d >= start && d in from..to && (end == null || d <= end)) {
                    out.add(d)
                }
            }
            year += 1
        }
        return out
    }
}
