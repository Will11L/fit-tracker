package com.example.sportapp.designsystem.common_components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Tests JVM du comportement de [CalendarMonthGrid] (refactor 2026-05-30, commit
 * 9187db6 : extraction slot-based des grilles inline dupliquees de
 * CalendarViewScreen + TasksCalendarScreen).
 *
 * Le composable lui-meme depend de Compose (BoxWithConstraints / LazyVerticalGrid),
 * donc non testable en JVM pur. Ce qui est testable et load-bearing, c'est la
 * logique observable que la grille rend :
 *
 *   1. Construction de la liste de cellules : [firstDayOffset] cellules vides
 *      (Spacer) en tete, suivies d'une cellule par jour du mois (1..lengthOfMonth).
 *      C'est exactement le `buildList { repeat(firstDayOffset){add(null)}; ... }`
 *      du composable, re-derive ici pour garder le test pur (pas de Robolectric/
 *      Compose UI test dans la stack JVM).
 *
 *   2. Convention d'offset Monday-first `(dayOfWeek.value + 6) % 7` que les deux
 *      appelants calculent et passent en [firstDayOffset]. Si cette formule
 *      casse, le calendrier s'affiche decale -> regression visible.
 *
 * Helpers prives = miroir exact de la source. Si la source change, ces tests
 * doivent etre revus (ils gardent le contrat, pas l'implementation interne).
 */
class CalendarMonthGridLogicTest {

    /** Miroir du `buildList` interne de CalendarMonthGrid. */
    private fun buildDays(month: YearMonth, firstDayOffset: Int): List<LocalDate?> =
        buildList {
            repeat(firstDayOffset) { add(null) }
            for (day in 1..month.lengthOfMonth()) {
                add(LocalDate.of(month.year, month.month, day))
            }
        }

    /** Miroir de la formule Monday-first des appelants (Calendar + Tasks). */
    private fun mondayFirstOffset(month: YearMonth): Int =
        (month.atDay(1).dayOfWeek.value + 6) % 7

    // ----- construction de la liste de cellules -----

    @Test
    fun `total cell count is offset plus days in month`() {
        // Mai 2026 : 31 jours. Offset 4 -> 35 cellules.
        val days = buildDays(YearMonth.of(2026, 5), firstDayOffset = 4)
        assertEquals(4 + 31, days.size)
    }

    @Test
    fun `leading offset cells are null spacers`() {
        val days = buildDays(YearMonth.of(2026, 5), firstDayOffset = 4)
        assertEquals(listOf(null, null, null, null), days.take(4))
    }

    @Test
    fun `day cells are consecutive dates of the month after the offset`() {
        val month = YearMonth.of(2026, 5)
        val days = buildDays(month, firstDayOffset = 4)

        // 1ere cellule non-vide = jour 1, derniere = dernier jour du mois.
        assertEquals(LocalDate.of(2026, 5, 1), days[4])
        assertEquals(LocalDate.of(2026, 5, 31), days.last())

        // Aucune cellule nulle apres l'offset, et sequence stricte.
        val dayCells = days.drop(4).filterNotNull()
        assertEquals(31, dayCells.size)
        dayCells.forEachIndexed { i, date ->
            assertEquals(LocalDate.of(2026, 5, i + 1), date)
        }
    }

    @Test
    fun `zero offset produces no leading spacers`() {
        val days = buildDays(YearMonth.of(2026, 2), firstDayOffset = 0)
        assertEquals(LocalDate.of(2026, 2, 1), days.first())
        assertEquals(28, days.size) // fevrier 2026 non bissextile
    }

    @Test
    fun `february leap year has 29 day cells`() {
        // 2024 est bissextile.
        val days = buildDays(YearMonth.of(2024, 2), firstDayOffset = 0)
        assertEquals(29, days.filterNotNull().size)
        assertEquals(LocalDate.of(2024, 2, 29), days.last())
    }

    // ----- convention d'offset Monday-first des appelants -----

    @Test
    fun `monday first offset is 0 when month starts on monday`() {
        // 1er juin 2026 = lundi.
        assertEquals(0, mondayFirstOffset(YearMonth.of(2026, 6)))
    }

    @Test
    fun `monday first offset is 6 when month starts on sunday`() {
        // 1er mars 2026 = dimanche -> 6 cellules vides avant.
        assertEquals(6, mondayFirstOffset(YearMonth.of(2026, 3)))
    }

    @Test
    fun `monday first offset matches each weekday position`() {
        // 1er mai 2026 = vendredi -> position 4 (Lun=0..Ven=4).
        assertEquals(4, mondayFirstOffset(YearMonth.of(2026, 5)))
        // 1er janvier 2026 = jeudi -> 3.
        assertEquals(3, mondayFirstOffset(YearMonth.of(2026, 1)))
    }
}
