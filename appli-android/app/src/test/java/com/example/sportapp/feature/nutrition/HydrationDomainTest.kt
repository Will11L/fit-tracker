package com.example.sportapp.feature.nutrition

import com.example.sportapp.core.data.model.HealthGoal
import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.core.data.model.WaterIntake
import com.example.sportapp.feature.nutrition.domain.activeWaterGoalMl
import com.example.sportapp.feature.nutrition.domain.dayHydrationMl
import com.example.sportapp.feature.nutrition.domain.detectWaterFromOffCategories
import com.example.sportapp.feature.nutrition.domain.mealWaterMl
import com.example.sportapp.feature.nutrition.domain.manualWaterMl
import com.example.sportapp.feature.nutrition.domain.waterGoalUuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Logique pure de l'Hydratation (2026-07-05). */
class HydrationDomainTest {

    private fun intake(uuid: String, date: String, ml: Int) =
        WaterIntake(uuid = uuid, userId = 1, date = date, amountMl = ml)

    private fun entry(uuid: String, mealUuid: String, foodUuid: String?, grams: Float) =
        MealEntry(
            uuid = uuid,
            mealUUID = mealUuid,
            foodUUID = foodUuid,
            displayName = "x",
            quantityG = grams,
            kcalPer100g = 0f,
            proteinPer100g = 0f,
            carbsPer100g = 0f,
            fatPer100g = 0f,
        )

    private fun goal(uuid: String, type: String, target: Float, from: String) =
        HealthGoal(uuid = uuid, userId = 1, type = type, target = target, effectiveFrom = from)

    // ── détection eau ────────────────────────────────────────────────────────
    @Test
    fun `detects water from off categories family`() {
        assertTrue(detectWaterFromOffCategories(listOf("en:beverages", "en:waters")))
        assertTrue(detectWaterFromOffCategories(listOf("en:mineral-waters")))
        assertTrue(detectWaterFromOffCategories(listOf("en:spring-waters")))
        assertTrue(detectWaterFromOffCategories(listOf("fr:eaux", "en:sparkling-waters")))
    }

    @Test
    fun `does not flag non-water categories`() {
        assertFalse(detectWaterFromOffCategories(listOf("en:sodas", "en:beverages")))
        assertFalse(detectWaterFromOffCategories(emptyList()))
        assertFalse(detectWaterFromOffCategories(listOf("en:waterfowls"))) // piège : pas "waters"
    }

    // ── total du jour ────────────────────────────────────────────────────────
    @Test
    fun `manual water sums only the selected day`() {
        val intakes = listOf(
            intake("a", "2026-07-05", 250),
            intake("b", "2026-07-05", 500),
            intake("c", "2026-07-04", 1000),
        )
        assertEquals(750, manualWaterMl(intakes, "2026-07-05"))
    }

    @Test
    fun `meal water counts only water foods in the day meals (1g = 1ml)`() {
        val dayMeals = setOf("m1")
        val entries = listOf(
            entry("e1", "m1", "waterFood", 500f),   // eau → compte 500 ml
            entry("e2", "m1", "solidFood", 200f),   // pas eau → ignoré
            entry("e3", "other", "waterFood", 300f),// autre repas (autre jour) → ignoré
            entry("e4", "m1", null, 100f),          // aliment supprimé → ignoré
        )
        val waterFoods = setOf("waterFood")
        assertEquals(500, mealWaterMl(dayMeals, entries, waterFoods))
    }

    @Test
    fun `day hydration is manual plus meal water`() {
        val intakes = listOf(intake("a", "2026-07-05", 250))
        val dayMeals = setOf("m1")
        val entries = listOf(entry("e1", "m1", "w", 750f))
        val total = dayHydrationMl("2026-07-05", intakes, dayMeals, entries, setOf("w"))
        assertEquals(1000, total)
    }

    // ── objectif WATER_ML ────────────────────────────────────────────────────
    @Test
    fun `active water goal picks the latest effectiveFrom for the type`() {
        val goals = listOf(
            goal("g1", "WATER_ML", 2000f, "2026-07-01"),
            goal("g2", "WATER_ML", 2500f, "2026-07-05"),
            goal("g3", "STEPS", 10000f, "2026-07-05"), // autre type → ignoré
        )
        assertEquals(2500, activeWaterGoalMl(goals, "2026-07-05"))
        assertEquals(2000, activeWaterGoalMl(goals, "2026-07-03"))
        assertNull(activeWaterGoalMl(goals, "2026-06-30"))
    }

    // ── uuid déterministe ────────────────────────────────────────────────────
    @Test
    fun `water goal uuid is deterministic per user and day`() {
        assertEquals(waterGoalUuid(1, "2026-07-05"), waterGoalUuid(1, "2026-07-05"))
        assertTrue(waterGoalUuid(1, "2026-07-05") != waterGoalUuid(1, "2026-07-06"))
        assertTrue(waterGoalUuid(1, "2026-07-05") != waterGoalUuid(2, "2026-07-05"))
    }
}
