package com.example.sportapp.feature.nutrition

import com.example.sportapp.core.data.model.Meal
import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.core.data.model.NutritionGoal
import com.example.sportapp.core.stats.ChartGranularity
import com.example.sportapp.feature.nutrition.domain.MacroKey
import com.example.sportapp.feature.nutrition.domain.aggregateMacroSeries
import com.example.sportapp.feature.nutrition.domain.earliestMealDate
import com.example.sportapp.feature.nutrition.domain.granularityFor
import com.example.sportapp.feature.nutrition.domain.topFoodsByMacro
import com.example.sportapp.feature.nutrition.domain.weekBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests JVM purs de la logique des Stats nutrition (A6). */
class NutritionStatsTest {

    private fun meal(uuid: String, date: String) =
        Meal(uuid = uuid, userId = 1, date = date, name = "M", orderIndex = 0)

    private fun entry(
        uuid: String, mealUuid: String, qty: Float,
        kcal: Float = 0f, p: Float = 0f, c: Float = 0f, f: Float = 0f,
        food: String? = null, name: String = "E",
    ) = MealEntry(
        uuid = uuid, mealUUID = mealUuid, foodUUID = food, displayName = name, quantityG = qty,
        kcalPer100g = kcal, proteinPer100g = p, carbsPer100g = c, fatPer100g = f,
    )

    private fun goal(from: String, kcal: Float, p: Float, c: Float, f: Float) =
        NutritionGoal(uuid = "g-$from", userId = 1, effectiveFrom = from, kcal = kcal, proteinG = p, carbsG = c, fatG = f)

    // ─── topFoodsByMacro ─────────────────────────────────────────────────────

    @Test
    fun `topFoodsByMacro aggregates by food and sorts descending with shares`() {
        val meals = listOf(meal("m1", "2026-06-01"), meal("m2", "2026-06-02"))
        val entries = listOf(
            // Rice: 200g @ 100 kcal/100g over 2 entries = 100 + 100 = 200 kcal
            entry("e1", "m1", qty = 100f, kcal = 100f, food = "rice", name = "Rice"),
            entry("e2", "m2", qty = 100f, kcal = 100f, food = "rice", name = "Rice"),
            // Chicken: 100g @ 300 kcal/100g = 300 kcal
            entry("e3", "m1", qty = 100f, kcal = 300f, food = "chicken", name = "Chicken"),
        )
        val top = topFoodsByMacro(entries, meals, "2026-06-01", "2026-06-30", MacroKey.KCAL)

        assertEquals(2, top.size)
        assertEquals("Chicken", top[0].displayName)
        assertEquals(300f, top[0].value, 0.001f)
        assertEquals("Rice", top[1].displayName)
        assertEquals(200f, top[1].value, 0.001f)
        // shares = value / total (total = 500)
        assertEquals(0.6f, top[0].share, 0.001f)
        assertEquals(0.4f, top[1].share, 0.001f)
    }

    @Test
    fun `topFoodsByMacro ignores out-of-period meals and zero contributions`() {
        val meals = listOf(meal("m1", "2026-06-01"), meal("m2", "2026-05-01"))
        val entries = listOf(
            entry("e1", "m1", qty = 100f, kcal = 100f, p = 0f, name = "NoProtein"),  // 0 protein -> ignored for PROTEIN
            entry("e2", "m1", qty = 100f, kcal = 0f, p = 20f, name = "Whey"),         // 20g protein
            entry("e3", "m2", qty = 100f, kcal = 0f, p = 99f, name = "OutOfRange"),   // out of period
        )
        val top = topFoodsByMacro(entries, meals, "2026-06-01", "2026-06-30", MacroKey.PROTEIN)

        assertEquals(1, top.size)
        assertEquals("Whey", top[0].displayName)
        assertEquals(20f, top[0].value, 0.001f)
        assertEquals(1f, top[0].share, 0.001f)
    }

    // ─── aggregateMacroSeries ────────────────────────────────────────────────

    @Test
    fun `aggregateMacroSeries fills continuous daily buckets including empty days`() {
        val meals = listOf(meal("m1", "2026-06-01"), meal("m3", "2026-06-03"))
        val entries = listOf(
            entry("e1", "m1", qty = 100f, kcal = 200f),  // day 1: 200 kcal
            entry("e3", "m3", qty = 50f, kcal = 200f),   // day 3: 100 kcal
        )
        val series = aggregateMacroSeries(
            entries, meals, emptyList(), "2026-06-01", "2026-06-03",
            ChartGranularity.DAILY, MacroKey.KCAL,
        )
        assertEquals(listOf("2026-06-01", "2026-06-02", "2026-06-03"), series.buckets)
        assertEquals(listOf(200f, 0f, 100f), series.consumed)
        // pas de goal -> cibles toutes nulles
        assertTrue(series.target.all { it == 0f })
    }

    @Test
    fun `aggregateMacroSeries sums target from active goal per day`() {
        val meals = emptyList<Meal>()
        val goals = listOf(goal("2026-06-01", kcal = 2000f, p = 150f, c = 200f, f = 60f))
        val series = aggregateMacroSeries(
            emptyList(), meals, goals, "2026-06-01", "2026-06-02",
            ChartGranularity.DAILY, MacroKey.PROTEIN,
        )
        // 2 jours, cible protéine 150 chacun
        assertEquals(listOf(150f, 150f), series.target)
    }

    @Test
    fun `aggregateMacroSeries derives fiber target from kcal goal`() {
        val goals = listOf(goal("2026-06-01", kcal = 2000f, p = 150f, c = 200f, f = 60f))
        val series = aggregateMacroSeries(
            emptyList(), emptyList(), goals, "2026-06-01", "2026-06-01",
            ChartGranularity.DAILY, MacroKey.FIBER,
        )
        // fiberTargetG(2000) = 2000/1000 * 15 = 30
        assertEquals(listOf(30f), series.target)
    }

    @Test
    fun `aggregateMacroSeries groups days into weekly buckets`() {
        // 2026-06-01 is a Monday. Span Mon -> next Mon crosses 2 ISO-%W weeks.
        val series = aggregateMacroSeries(
            emptyList(), emptyList(), emptyList(), "2026-06-01", "2026-06-09",
            ChartGranularity.WEEKLY, MacroKey.KCAL,
        )
        assertEquals(2, series.buckets.size)
        assertEquals(weekBucket("2026-06-01"), series.buckets[0])
        assertEquals(weekBucket("2026-06-08"), series.buckets[1])
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    @Test
    fun `granularityFor switches to weekly past 14 days`() {
        assertEquals(ChartGranularity.DAILY, granularityFor("2026-06-01", "2026-06-14"))   // 14 days
        assertEquals(ChartGranularity.WEEKLY, granularityFor("2026-06-01", "2026-06-15"))  // 15 days
    }

    @Test
    fun `earliestMealDate returns the minimum date`() {
        val meals = listOf(meal("a", "2026-06-10"), meal("b", "2026-06-02"), meal("c", "2026-06-20"))
        assertEquals("2026-06-02", earliestMealDate(meals))
        assertEquals(null, earliestMealDate(emptyList()))
    }
}
