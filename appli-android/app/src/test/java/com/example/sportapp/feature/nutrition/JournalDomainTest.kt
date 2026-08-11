package com.example.sportapp.feature.nutrition

import com.example.sportapp.core.data.model.Meal
import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.core.data.model.MealPreset
import com.example.sportapp.core.data.model.NutritionGoal
import com.example.sportapp.feature.nutrition.domain.MicroKey
import com.example.sportapp.feature.nutrition.domain.RingMacroKey
import com.example.sportapp.feature.nutrition.domain.HIGH_SUGAR_PER_100G
import com.example.sportapp.feature.nutrition.domain.activeGoalFor
import com.example.sportapp.feature.nutrition.domain.buildSections
import com.example.sportapp.feature.nutrition.domain.consumedSugarG
import com.example.sportapp.feature.nutrition.domain.dailyTotalsForMonth
import com.example.sportapp.feature.nutrition.domain.entryTotals
import com.example.sportapp.feature.nutrition.domain.fiberTargetG
import com.example.sportapp.feature.nutrition.domain.isHighSugar
import com.example.sportapp.feature.nutrition.domain.microRows
import com.example.sportapp.feature.nutrition.domain.pastMeals
import com.example.sportapp.feature.nutrition.domain.sugarLimitsG
import com.example.sportapp.feature.nutrition.domain.sumMicroTotals
import com.example.sportapp.feature.nutrition.domain.sumSugarG
import com.example.sportapp.feature.nutrition.domain.sumTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests JVM purs de la logique du Journal nutrition (A2). */
class JournalDomainTest {

    private fun meal(uuid: String, date: String, order: Int = 0, preset: String? = null, name: String = "M") =
        Meal(uuid = uuid, userId = 1, date = date, name = name, orderIndex = order, presetUuid = preset)

    private fun preset(uuid: String, name: String, order: Int) =
        MealPreset(uuid = uuid, userId = 1, name = name, orderIndex = order)

    private fun entry(
        uuid: String, mealUuid: String, qty: Float,
        kcal: Float = 100f, p: Float = 0f, c: Float = 0f, f: Float = 0f,
        sodium: Float? = null, sugar: Float? = null,
    ) = MealEntry(
        uuid = uuid, mealUUID = mealUuid, displayName = "E", quantityG = qty,
        kcalPer100g = kcal, proteinPer100g = p, carbsPer100g = c, fatPer100g = f,
        sodiumPer100g = sodium, sugarPer100g = sugar,
    )

    private fun goal(uuid: String, from: String, kcal: Float, p: Float, c: Float, f: Float) =
        NutritionGoal(uuid = uuid, userId = 1, effectiveFrom = from, kcal = kcal, proteinG = p, carbsG = c, fatG = f)

    @Test
    fun `entryTotals scales per-100g snapshot by quantity`() {
        val t = entryTotals(entry("e", "m", qty = 250f, kcal = 200f, p = 10f, c = 20f, f = 5f))
        assertEquals(500f, t.kcal, 0.001f)
        assertEquals(25f, t.protein, 0.001f)
        assertEquals(50f, t.carbs, 0.001f)
        assertEquals(12.5f, t.fat, 0.001f)
    }

    @Test
    fun `sumTotals adds all entries`() {
        val totals = sumTotals(
            listOf(
                entry("a", "m", qty = 100f, kcal = 100f),
                entry("b", "m", qty = 200f, kcal = 100f),
            )
        )
        assertEquals(300f, totals.kcal, 0.001f)
    }

    @Test
    fun `buildSections matches preset by presetUuid and appends ad hoc`() {
        val presets = listOf(preset("p1", "Breakfast", 0), preset("p2", "Lunch", 1))
        val meals = listOf(
            meal("m1", "2026-06-17", order = 0, preset = "p1", name = "Breakfast"),
            meal("m9", "2026-06-17", order = 5, preset = null, name = "Snack"), // ad hoc
        )
        val entries = listOf(entry("e1", "m1", qty = 100f, kcal = 250f))

        val sections = buildSections(presets, meals, entries)

        assertEquals(3, sections.size)
        // p1 matched to m1 with its entry
        assertEquals("Breakfast", sections[0].name)
        assertEquals("m1", sections[0].meal?.uuid)
        assertEquals(1, sections[0].entries.size)
        assertEquals(250f, sections[0].totals.kcal, 0.001f)
        // p2 has no meal yet -> empty section, meal null
        assertEquals("Lunch", sections[1].name)
        assertNull(sections[1].meal)
        assertTrue(sections[1].entries.isEmpty())
        // ad hoc appended last
        assertEquals("Snack", sections[2].name)
        assertNull(sections[2].presetUuid)
    }

    @Test
    fun `buildSections falls back to name match for legacy meals`() {
        val presets = listOf(preset("p1", "Breakfast", 0))
        val meals = listOf(meal("m1", "2026-06-17", preset = null, name = "Breakfast"))
        val sections = buildSections(presets, meals, emptyList())
        assertEquals(1, sections.size)
        assertEquals("m1", sections[0].meal?.uuid) // matched by name despite presetUuid null
    }

    @Test
    fun `activeGoalFor picks greatest effectiveFrom not after date`() {
        val goals = listOf(
            goal("g1", "2026-01-01", 2000f, 150f, 200f, 60f),
            goal("g2", "2026-06-01", 2500f, 180f, 250f, 80f),
            goal("g3", "2026-12-01", 3000f, 200f, 300f, 90f),
        )
        assertEquals("g2", activeGoalFor(goals, "2026-06-17")?.uuid)
        assertEquals("g1", activeGoalFor(goals, "2026-03-01")?.uuid)
        assertNull(activeGoalFor(goals, "2025-12-31"))
    }

    @Test
    fun `dailyTotalsForMonth aggregates per day and computes progress vs goal`() {
        val meals = listOf(meal("m1", "2026-06-17"))
        val entries = listOf(entry("e1", "m1", qty = 100f, kcal = 1000f))
        val goals = listOf(goal("g1", "2026-06-01", kcal = 2000f, p = 100f, c = 100f, f = 100f))

        val map = dailyTotalsForMonth(listOf("2026-06-17", "2026-06-18"), entries, meals, goals)

        val d17 = map["2026-06-17"]!!
        assertTrue(d17.hasData)
        assertEquals(1000f, d17.totals.kcal, 0.001f)
        assertEquals(0.5f, d17.progress[RingMacroKey.KCAL]!!, 0.001f)

        val d18 = map["2026-06-18"]!!
        assertFalse(d18.hasData)
        assertEquals(0f, d18.progress[RingMacroKey.KCAL]!!, 0.001f)
    }

    @Test
    fun `dailyTotalsForMonth caps ring progress at 1 and maps each macro to its own target`() {
        // Cibles distinctes par macro : un swap carbs/fat/protein dans targetOf
        // donnerait des progressions fausses. kcal volontairement dépassé pour
        // vérifier que l'anneau plafonne à 1 (jamais de débordement).
        val meals = listOf(meal("m1", "2026-06-17"))
        val entries = listOf(entry("e1", "m1", qty = 100f, kcal = 4000f, p = 75f, c = 50f, f = 80f))
        val goals = listOf(goal("g1", "2026-06-01", kcal = 2000f, p = 150f, c = 200f, f = 100f))

        val day = dailyTotalsForMonth(listOf("2026-06-17"), entries, meals, goals)["2026-06-17"]!!

        // kcal 4000 / 2000 = 2.0 -> borné à 1.0
        assertEquals(1f, day.progress[RingMacroKey.KCAL]!!, 0.001f)
        // carbs 50 / 200 = 0.25 (champ carbsG)
        assertEquals(0.25f, day.progress[RingMacroKey.CARBS]!!, 0.001f)
        // fat 80 / 100 = 0.8 (champ fatG)
        assertEquals(0.8f, day.progress[RingMacroKey.FAT]!!, 0.001f)
        // protein 75 / 150 = 0.5 (champ proteinG)
        assertEquals(0.5f, day.progress[RingMacroKey.PROTEIN]!!, 0.001f)
    }

    @Test
    fun `fiberTargetG derives 15g per 1000 kcal and is null without a kcal goal`() {
        assertEquals(30f, fiberTargetG(2000f)!!, 0.001f) // 2000/1000 * 15
        assertEquals(7.5f, fiberTargetG(500f)!!, 0.001f) // 500/1000 * 15
        assertNull(fiberTargetG(null)) // pas de cible kcal -> pas de barre fibres
        assertNull(fiberTargetG(0f))   // kcal 0 -> pas de barre
    }

    @Test
    fun `sumSugarG cumulates sugars from per-100g snapshots scaled by quantity, null as 0`() {
        val total = sumSugarG(
            listOf(
                entry("a", "m", qty = 200f, sugar = 10f),  // 20 g
                entry("b", "m", qty = 50f, sugar = 4f),    // 2 g
                entry("c", "m", qty = 100f, sugar = null), // 0 g (snapshot absent)
            )
        )
        assertEquals(22f, total, 0.001f)
        assertEquals(0f, sumSugarG(emptyList()), 0.001f)
    }

    @Test
    fun `sugarLimitsG derives cap as 5 percent of kcal target bounded at 100g, ideal at half, falls back to 2000 kcal`() {
        // 2000 kcal -> 5 % du nombre de kcal = 100 g (cap ANSES atteint) ; ideal = moitié = 50 g
        assertEquals(100f, sugarLimitsG(2000f).limitG, 0.001f)
        assertEquals(50f, sugarLimitsG(2000f).idealG, 0.001f)
        // Sous 2000 kcal : proportionnel (5 % du nombre de kcal).
        assertEquals(80f, sugarLimitsG(1600f).limitG, 0.001f)
        assertEquals(40f, sugarLimitsG(1600f).idealG, 0.001f)
        // Au-dessus de 2000 kcal : cap ANSES 100 g (plus de calories != plus de sucre).
        assertEquals(100f, sugarLimitsG(2500f).limitG, 0.001f)
        assertEquals(50f, sugarLimitsG(2500f).idealG, 0.001f)
        // Jamais null : sans cible kcal active, repli sur la base 2000 kcal -> 100 g / 50 g
        assertEquals(100f, sugarLimitsG(null).limitG, 0.001f)
        assertEquals(50f, sugarLimitsG(null).idealG, 0.001f)
        assertEquals(100f, sugarLimitsG(0f).limitG, 0.001f)
    }

    @Test
    fun `isHighSugar flags foods strictly above 22,5g per 100g and treats null as not high`() {
        // Miroir 1:1 du helper web (repère étiquetage UK « high in sugar »).
        assertEquals(22.5f, HIGH_SUGAR_PER_100G, 0.001f)
        assertFalse(isHighSugar(22.5f)) // frontière : le seuil lui-même n'alerte pas
        assertTrue(isHighSugar(22.6f))
        assertTrue(isHighSugar(45f))
        assertFalse(isHighSugar(4f))
        assertFalse(isHighSugar(null)) // sucres non renseignés -> jamais d'alerte
    }

    @Test
    fun `consumedSugarG scales the per-100g snapshot by quantity and is null when unknown`() {
        assertEquals(20f, entry("a", "m", qty = 200f, sugar = 10f).consumedSugarG()!!, 0.001f)
        assertEquals(2f, entry("b", "m", qty = 50f, sugar = 4f).consumedSugarG()!!, 0.001f)
        // != sumSugarG (null = 0 dans le cumul du jour) : ici null = « inconnu » -> pas de ligne au dépli.
        assertNull(entry("c", "m", qty = 100f, sugar = null).consumedSugarG())
    }

    @Test
    fun `microRows flags sodium over the limit and bounds progress`() {
        val totals = sumMicroTotals(listOf(entry("e", "m", qty = 100f, sodium = 3000f)))
        val rows = microRows(totals)
        val sodium = rows.first { it.key == MicroKey.SODIUM }
        assertTrue(sodium.exceeded)            // 3000 > 2000 plafond
        assertEquals(1f, sodium.progress, 0.001f) // borné à 1
        val iron = rows.first { it.key == MicroKey.IRON }
        assertEquals(0f, iron.progress, 0.001f) // pas de fer -> 0
    }

    @Test
    fun `pastMeals returns only past non-empty meals most recent first`() {
        val meals = listOf(
            meal("today", "2026-06-17", name = "Today"),
            meal("y", "2026-06-16", name = "Yesterday"),
            meal("old", "2026-06-10", name = "Old"),
            meal("emptyPast", "2026-06-15", name = "EmptyPast"),
        )
        val entries = listOf(
            entry("e0", "today", qty = 100f),
            entry("e1", "y", qty = 100f, kcal = 200f),
            entry("e2", "old", qty = 100f),
        )
        val result = pastMeals(meals, entries, beforeDay = "2026-06-17")
        assertEquals(listOf("y", "old"), result.map { it.meal.uuid }) // emptyPast excluded, today excluded
        assertEquals(200f, result.first().totals.kcal, 0.001f)
    }
}
