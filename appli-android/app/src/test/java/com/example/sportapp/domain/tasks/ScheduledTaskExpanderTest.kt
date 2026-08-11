package com.example.sportapp.core.domain.tasks

import com.example.sportapp.core.data.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ScheduledTaskExpanderTest {

    private fun taskOf(
        recurrenceKind: String,
        dueDate: String? = null,
        weekdays: List<Int>? = null,
        startDate: String? = null,
        endDate: String? = null,
        excludedDates: List<String> = emptyList(),
    ): Task = Task(
        uuid = "uuid-${System.nanoTime()}",
        userId = 1,
        title = "Test",
        recurrenceKind = recurrenceKind,
        dueDate = dueDate,
        recurrenceWeekdays = weekdays,
        recurrenceStartDate = startDate,
        recurrenceEndDate = endDate,
        excludedDates = excludedDates,
    )

    // ─── NONE ─────────────────────────────────────────────────────────────────

    @Test fun `NONE in month returns one date`() {
        val t = taskOf("NONE", dueDate = "2026-05-15")
        val occ = ScheduledTaskExpander.occurrencesForMonth(t, YearMonth.of(2026, 5))
        assertEquals(listOf(LocalDate.of(2026, 5, 15)), occ)
    }

    @Test fun `NONE outside month returns empty`() {
        val t = taskOf("NONE", dueDate = "2026-06-01")
        val occ = ScheduledTaskExpander.occurrencesForMonth(t, YearMonth.of(2026, 5))
        assertEquals(emptyList<LocalDate>(), occ)
    }

    // ─── WEEKLY ───────────────────────────────────────────────────────────────

    @Test fun `WEEKLY mondays in may 2026`() {
        // Mai 2026 commence vendredi 1. Lundis : 4, 11, 18, 25.
        val t = taskOf(
            "WEEKLY",
            weekdays = listOf(0),  // Mon=0
            startDate = "2026-04-01",
        )
        val occ = ScheduledTaskExpander.occurrencesForMonth(t, YearMonth.of(2026, 5))
        assertEquals(
            listOf(
                LocalDate.of(2026, 5, 4),
                LocalDate.of(2026, 5, 11),
                LocalDate.of(2026, 5, 18),
                LocalDate.of(2026, 5, 25),
            ),
            occ,
        )
    }

    @Test fun `WEEKLY multi-weekdays MWF in may 2026`() {
        val t = taskOf(
            "WEEKLY",
            weekdays = listOf(0, 2, 4),  // Mon Wed Fri
            startDate = "2026-04-01",
        )
        val occ = ScheduledTaskExpander.occurrencesForMonth(t, YearMonth.of(2026, 5))
        // Mai 2026 : Mon 4/11/18/25, Wed 6/13/20/27, Fri 1/8/15/22/29
        assertEquals(13, occ.size)
        assertEquals(LocalDate.of(2026, 5, 1), occ.first())   // Friday May 1
    }

    @Test fun `WEEKLY respects start_date`() {
        val t = taskOf(
            "WEEKLY",
            weekdays = listOf(5),  // Sat
            startDate = "2026-05-20",  // skip Sat May 2, 9, 16
        )
        val occ = ScheduledTaskExpander.occurrencesForMonth(t, YearMonth.of(2026, 5))
        assertEquals(listOf(LocalDate.of(2026, 5, 23), LocalDate.of(2026, 5, 30)), occ)
    }

    @Test fun `WEEKLY respects end_date`() {
        val t = taskOf(
            "WEEKLY",
            weekdays = listOf(5),  // Sat
            startDate = "2026-04-01",
            endDate = "2026-05-10",
        )
        val occ = ScheduledTaskExpander.occurrencesForMonth(t, YearMonth.of(2026, 5))
        assertEquals(listOf(LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 9)), occ)
    }

    // ─── MONTHLY ──────────────────────────────────────────────────────────────

    @Test fun `MONTHLY day 15 in may`() {
        val t = taskOf("MONTHLY", startDate = "2026-01-15")
        val occ = ScheduledTaskExpander.occurrencesForMonth(t, YearMonth.of(2026, 5))
        assertEquals(listOf(LocalDate.of(2026, 5, 15)), occ)
    }

    @Test fun `MONTHLY day 31 skips february 2027`() {
        val t = taskOf("MONTHLY", startDate = "2026-01-31")
        val occ = ScheduledTaskExpander.occurrencesForMonth(t, YearMonth.of(2027, 2))
        assertEquals(emptyList<LocalDate>(), occ)  // Feb 2027 has 28 days, skip
    }

    @Test fun `MONTHLY skips months before start`() {
        val t = taskOf("MONTHLY", startDate = "2026-06-01")
        val occ = ScheduledTaskExpander.occurrencesForMonth(t, YearMonth.of(2026, 5))
        assertEquals(emptyList<LocalDate>(), occ)  // May before June 1
    }

    // ─── YEARLY ───────────────────────────────────────────────────────────────

    @Test fun `YEARLY birthday april 22`() {
        val t = taskOf("YEARLY", startDate = "2024-04-22")
        val occ = ScheduledTaskExpander.occurrencesForMonth(t, YearMonth.of(2026, 4))
        assertEquals(listOf(LocalDate.of(2026, 4, 22)), occ)
    }

    @Test fun `YEARLY skip other months`() {
        val t = taskOf("YEARLY", startDate = "2024-04-22")
        val occ = ScheduledTaskExpander.occurrencesForMonth(t, YearMonth.of(2026, 5))
        assertEquals(emptyList<LocalDate>(), occ)
    }

    @Test fun `YEARLY leap day feb 29 skips non-leap`() {
        val t = taskOf("YEARLY", startDate = "2024-02-29")  // 2024 = leap
        val occ2025 = ScheduledTaskExpander.occurrencesForMonth(t, YearMonth.of(2025, 2))  // non-leap
        assertEquals(emptyList<LocalDate>(), occ2025)
        val occ2028 = ScheduledTaskExpander.occurrencesForMonth(t, YearMonth.of(2028, 2))  // leap
        assertEquals(listOf(LocalDate.of(2028, 2, 29)), occ2028)
    }

    // ─── DAILY ────────────────────────────────────────────────────────────────

    @Test fun `DAILY returns empty in Calendar (handled by Daily tab)`() {
        val t = taskOf("DAILY", startDate = "2026-01-01")
        val occ = ScheduledTaskExpander.occurrencesForMonth(t, YearMonth.of(2026, 5))
        assertEquals(emptyList<LocalDate>(), occ)
    }

    // ─── B.4 excludedDates filter ─────────────────────────────────────────────

    @Test fun `WEEKLY skips excluded dates`() {
        // Mai 2026 lundis : 4, 11, 18, 25. Exclude 18 -> [4, 11, 25].
        val t = taskOf(
            "WEEKLY",
            weekdays = listOf(0),
            startDate = "2026-04-01",
            excludedDates = listOf("2026-05-18"),
        )
        val occ = ScheduledTaskExpander.occurrencesForMonth(t, YearMonth.of(2026, 5))
        assertEquals(
            listOf(LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 25)),
            occ,
        )
    }

    @Test fun `MONTHLY skips excluded date`() {
        // Le 15 de chaque mois sur Q2 2026 : avril 15, mai 15, juin 15. Exclude mai 15.
        val t = taskOf("MONTHLY", startDate = "2026-04-15", excludedDates = listOf("2026-05-15"))
        val occ = ScheduledTaskExpander.occurrencesInRange(
            t,
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 6, 30),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 4, 15), LocalDate.of(2026, 6, 15)),
            occ,
        )
    }

    @Test fun `empty excludedDates means no exclusion`() {
        val t = taskOf("WEEKLY", weekdays = listOf(0), startDate = "2026-04-01", excludedDates = emptyList())
        val occ = ScheduledTaskExpander.occurrencesForMonth(t, YearMonth.of(2026, 5))
        assertEquals(4, occ.size)  // 4, 11, 18, 25
    }
}
