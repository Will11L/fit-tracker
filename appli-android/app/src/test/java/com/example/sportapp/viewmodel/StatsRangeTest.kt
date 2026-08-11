package com.example.sportapp.viewmodel

import com.example.sportapp.core.stats.StatsRange
import com.example.sportapp.core.stats.StatsRangeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * B3-2 Etape 7 (2026-05-07) : tests JVM de la sealed class StatsRange et du
 * singleton StatsRangeState. Fonctions pures, pas de mock requis.
 */
class StatsRangeTest {

    private val today = LocalDate.of(2026, 5, 7)

    @Test
    fun `Last7Days computes bounds correctly`() {
        val (start, end) = StatsRange.Last7Days.computeBounds(today)
        assertEquals("2026-04-30", start)
        assertEquals("2026-05-07", end)
    }

    @Test
    fun `Last30Days computes bounds correctly`() {
        val (start, end) = StatsRange.Last30Days.computeBounds(today)
        assertEquals("2026-04-07", start)
        assertEquals("2026-05-07", end)
    }

    @Test
    fun `Last3Months computes bounds correctly`() {
        val (start, end) = StatsRange.Last3Months.computeBounds(today)
        assertEquals("2026-02-07", start)
        assertEquals("2026-05-07", end)
    }

    @Test
    fun `Last6Months computes bounds correctly`() {
        val (start, end) = StatsRange.Last6Months.computeBounds(today)
        assertEquals("2025-11-07", start)
        assertEquals("2026-05-07", end)
    }

    @Test
    fun `LastYear computes bounds correctly`() {
        val (start, end) = StatsRange.LastYear.computeBounds(today)
        assertEquals("2025-05-07", start)
        assertEquals("2026-05-07", end)
    }

    @Test
    fun `All returns very early start date`() {
        val (start, end) = StatsRange.All.computeBounds(today)
        assertEquals("2000-01-01", start)
        assertEquals("2026-05-07", end)
    }

    @Test
    fun `Custom returns its own start and end dates verbatim`() {
        val custom = StatsRange.Custom(
            startDate = LocalDate.of(2025, 1, 1),
            endDate = LocalDate.of(2025, 12, 31),
        )
        val (start, end) = custom.computeBounds(today)
        assertEquals("2025-01-01", start)
        assertEquals("2025-12-31", end)
    }

    @Test
    fun `StatsRangeState defaults to Last7Days`() {
        val state = StatsRangeState()
        assertEquals(StatsRange.Last7Days, state.range.value)
    }

    @Test
    fun `StatsRangeState propagates setRange to flow`() = runBlocking {
        val state = StatsRangeState()
        state.setRange(StatsRange.LastYear)
        assertEquals(StatsRange.LastYear, state.range.first())
    }
}
