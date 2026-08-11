package com.example.sportapp.feature.nutrition

import com.example.sportapp.core.data.model.Meal
import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.core.data.model.NutritionGoal
import com.example.sportapp.feature.nutrition.domain.MacroKey
import com.example.sportapp.feature.nutrition.domain.averageDailyConsumed
import com.example.sportapp.feature.nutrition.domain.deriveGoalFromMacros
import com.example.sportapp.feature.nutrition.domain.fiberDensity
import com.example.sportapp.feature.nutrition.domain.macroKcalBreakdown
import com.example.sportapp.feature.nutrition.domain.macroPerKg
import com.example.sportapp.feature.nutrition.domain.rangeDayCountInclusive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests JVM purs de l'analyse des Objectifs nutrition (A5). */
class GoalAnalysisTest {

    private fun meal(uuid: String, date: String) =
        Meal(uuid = uuid, userId = 1, date = date, name = "M", orderIndex = 0, presetUuid = null)

    private fun entry(uuid: String, mealUuid: String, qty: Float, kcal: Float, p: Float = 0f, c: Float = 0f, f: Float = 0f) =
        MealEntry(
            uuid = uuid, mealUUID = mealUuid, displayName = "E", quantityG = qty,
            kcalPer100g = kcal, proteinPer100g = p, carbsPer100g = c, fatPer100g = f,
        )

    private fun goal(kcal: Float, p: Float, c: Float, f: Float) =
        NutritionGoal(uuid = "g", userId = 1, effectiveFrom = "2026-01-01", kcal = kcal, proteinG = p, carbsG = c, fatG = f)

    @Test
    fun `deriveGoalFromMacros integrates the fiber space (macro-first D12)`() {
        // P180 / G250 / L80 -> base 2440 -> total 2515 kcal, ~38 g fibres (exemple web).
        val d = deriveGoalFromMacros(proteinG = 180f, carbsG = 250f, fatG = 80f)
        assertEquals(2515f, d.kcal, 1f)
        assertEquals(38f, d.fiberG, 0.5f)
    }

    @Test
    fun `deriveGoalFromMacros is zero when no macro is entered`() {
        val d = deriveGoalFromMacros(0f, 0f, 0f)
        assertEquals(0f, d.kcal, 0.001f)
        assertEquals(0f, d.fiberG, 0.001f)
    }

    @Test
    fun `macroKcalBreakdown percentages sum to 100 and order is carbs fat protein fiber`() {
        val breakdown = macroKcalBreakdown(goal(kcal = 2000f, p = 150f, c = 200f, f = 60f))
        assertEquals(listOf(MacroKey.CARBS, MacroKey.FAT, MacroKey.PROTEIN, MacroKey.FIBER), breakdown.map { it.key })
        val sum = breakdown.sumOf { it.percent.toDouble() }
        assertEquals(100.0, sum, 0.01)
        // Glucides = 4 * 200 = 800 kcal de macro.
        assertEquals(800f, breakdown.first { it.key == MacroKey.CARBS }.kcal, 0.001f)
    }

    @Test
    fun `fiberDensity is ~15 for any positive kcal and 0 otherwise`() {
        assertEquals(15f, fiberDensity(2000f), 0.001f)
        assertEquals(15f, fiberDensity(3500f), 0.001f)
        assertEquals(0f, fiberDensity(0f), 0.001f)
    }

    @Test
    fun `macroPerKg divides by weight and is null without a profile weight`() {
        assertEquals(2f, macroPerKg(160f, 80f)!!, 0.001f)
        assertNull(macroPerKg(160f, null))
        assertNull(macroPerKg(160f, 0f))
    }

    @Test
    fun `rangeDayCountInclusive counts both bounds`() {
        assertEquals(7, rangeDayCountInclusive("2026-06-11", "2026-06-17"))
        assertEquals(1, rangeDayCountInclusive("2026-06-17", "2026-06-17"))
    }

    @Test
    fun `averageDailyConsumed sums in-range entries and divides by the day count`() {
        val meals = listOf(
            meal("m1", "2026-06-17"),
            meal("m2", "2026-06-15"),
            meal("old", "2026-06-01"), // hors fenêtre 7 jours
        )
        val entries = listOf(
            entry("e1", "m1", qty = 100f, kcal = 400f, p = 30f),
            entry("e2", "m2", qty = 100f, kcal = 300f, p = 10f),
            entry("eOld", "old", qty = 100f, kcal = 9999f),
        )
        // Fenêtre 2026-06-11..2026-06-17 = 7 jours ; total in-range = 700 kcal / 7 = 100.
        val avg = averageDailyConsumed(meals, entries, "2026-06-11", "2026-06-17")
        assertEquals(100f, avg.kcal, 0.001f)
        assertEquals(40f / 7f, avg.protein, 0.001f)
        assertTrue(avg.fat == 0f)
    }
}
