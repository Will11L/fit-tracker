package com.example.sportapp.feature.nutrition

import com.example.sportapp.core.data.model.Food
import com.example.sportapp.feature.nutrition.domain.CatalogueGroup
import com.example.sportapp.feature.nutrition.domain.FoodSource
import com.example.sportapp.feature.nutrition.domain.NutrientKey
import com.example.sportapp.feature.nutrition.domain.NutrientThreshold
import com.example.sportapp.feature.nutrition.domain.ThresholdOp
import com.example.sportapp.feature.nutrition.domain.buildCatalogue
import com.example.sportapp.feature.nutrition.domain.effectiveFoodKcal
import com.example.sportapp.feature.nutrition.domain.foodNutrientValue
import com.example.sportapp.feature.nutrition.domain.kcalFromMacros
import com.example.sportapp.feature.nutrition.domain.passesThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests JVM purs de la logique du Catalogue d'aliments (A3). */
class FoodCatalogueTest {

    private fun food(
        uuid: String,
        name: String,
        brand: String? = null,
        source: String = FoodSource.CIQUAL,
        kcal: Float = 0f,
        p: Float = 0f,
        c: Float = 0f,
        f: Float = 0f,
        fiber: Float? = null,
        iron: Float? = null,
        favorite: Boolean = false,
        archived: Boolean = false,
    ) = Food(
        uuid = uuid,
        userId = 1,
        name = name,
        brand = brand,
        source = source,
        kcalPer100g = kcal,
        proteinPer100g = p,
        carbsPer100g = c,
        fatPer100g = f,
        fiberPer100g = fiber,
        ironPer100g = iron,
        isFavorite = favorite,
        archived = archived,
    )

    // ─── kcal effective (D12) ─────────────────────────────────────────────────

    @Test
    fun `kcalFromMacros applies Atwater plus fiber`() {
        // 4P + 4G + 9L + 2F = 4*10 + 4*20 + 9*5 + 2*3 = 40+80+45+6 = 171
        assertEquals(171f, kcalFromMacros(10f, 20f, 5f, 3f), 0.001f)
    }

    @Test
    fun `CIQUAL kcal is derived from macros, ignoring stored value`() {
        val ciqual = food("a", "Egg", source = FoodSource.CIQUAL, kcal = 999f, p = 13f, c = 1f, f = 11f)
        assertEquals(kcalFromMacros(13f, 1f, 11f, null), effectiveFoodKcal(ciqual), 0.001f)
    }

    @Test
    fun `OFF kcal keeps the label value`() {
        val off = food("a", "Bar", source = FoodSource.OFF, kcal = 250f, p = 5f, c = 30f, f = 10f)
        assertEquals(250f, effectiveFoodKcal(off), 0.001f)
    }

    @Test
    fun `CUSTOM kcal uses entered value when positive, else derived`() {
        val withKcal = food("a", "X", source = FoodSource.CUSTOM, kcal = 120f, p = 1f, c = 1f, f = 1f)
        assertEquals(120f, effectiveFoodKcal(withKcal), 0.001f)
        val noKcal = food("b", "Y", source = FoodSource.CUSTOM, kcal = 0f, p = 10f, c = 0f, f = 0f)
        assertEquals(40f, effectiveFoodKcal(noKcal), 0.001f)
    }

    // ─── Seuils ────────────────────────────────────────────────────────────────

    @Test
    fun `absent micro counts as zero so a gte threshold excludes it`() {
        val noIron = food("a", "X", iron = null)
        assertEquals(0f, foodNutrientValue(noIron, NutrientKey.IRON), 0.001f)
        assertFalse(passesThresholds(noIron, listOf(NutrientThreshold(NutrientKey.IRON, ThresholdOp.GTE, 1f))))
    }

    @Test
    fun `thresholds combine with AND`() {
        val chicken = food("a", "Chicken", source = FoodSource.CIQUAL, p = 27f, c = 0f, f = 3f)
        val highProtein = NutrientThreshold(NutrientKey.PROTEIN, ThresholdOp.GTE, 20f)
        val lowFat = NutrientThreshold(NutrientKey.FAT, ThresholdOp.LTE, 5f)
        assertTrue(passesThresholds(chicken, listOf(highProtein, lowFat)))
        // Ajout d'un 3e seuil non satisfait → exclu.
        val highFiber = NutrientThreshold(NutrientKey.FIBER, ThresholdOp.GTE, 5f)
        assertFalse(passesThresholds(chicken, listOf(highProtein, lowFat, highFiber)))
    }

    // ─── Regroupement de la liste ─────────────────────────────────────────────

    @Test
    fun `default groups split favorites, all, and archived`() {
        val foods = listOf(
            food("a", "Apple", favorite = true),
            food("b", "Banana"),
            food("c", "Old", archived = true),
        )
        val noArchive = buildCatalogue(foods, query = "", thresholds = emptyList(), showArchived = false)
        assertEquals(listOf(CatalogueGroup.FAVORITES, CatalogueGroup.ALL), noArchive.map { it.group })
        // Archivé masqué tant que showArchived = false.
        assertTrue(noArchive.none { b -> b.foods.any { it.uuid == "c" } })

        val withArchive = buildCatalogue(foods, query = "", thresholds = emptyList(), showArchived = true)
        assertEquals(
            listOf(CatalogueGroup.FAVORITES, CatalogueGroup.ALL, CatalogueGroup.ARCHIVED),
            withArchive.map { it.group },
        )
    }

    @Test
    fun `text search matches name or brand and flattens to a single block`() {
        val foods = listOf(
            food("a", "Greek yogurt", brand = "Fage"),
            food("b", "Whey", brand = "MyProtein"),
        )
        val byName = buildCatalogue(foods, query = "yog", thresholds = emptyList(), showArchived = false)
        assertEquals(1, byName.size)
        assertEquals(null, byName[0].group)
        assertEquals(listOf("a"), byName[0].foods.map { it.uuid })

        val byBrand = buildCatalogue(foods, query = "myprot", thresholds = emptyList(), showArchived = false)
        assertEquals(listOf("b"), byBrand.flatMap { it.foods }.map { it.uuid })
    }

    @Test
    fun `search and thresholds combine - both must match`() {
        val foods = listOf(
            food("a", "Chicken breast", source = FoodSource.CIQUAL, p = 27f, f = 2f),
            food("b", "Chicken nugget", source = FoodSource.CIQUAL, p = 14f, f = 18f),
        )
        val blocks = buildCatalogue(
            foods,
            query = "chicken",
            thresholds = listOf(NutrientThreshold(NutrientKey.PROTEIN, ThresholdOp.GTE, 20f)),
            showArchived = false,
        )
        // Les deux contiennent "chicken" mais seul "a" passe le seuil protéine.
        assertEquals(listOf("a"), blocks.flatMap { it.foods }.map { it.uuid })
    }

    @Test
    fun `no match returns empty list`() {
        val foods = listOf(food("a", "Apple"))
        assertTrue(buildCatalogue(foods, query = "zzz", thresholds = emptyList(), showArchived = false).isEmpty())
    }

    @Test
    fun `kcal threshold filters on effective kcal, not the stored value`() {
        // CIQUAL : kcal stockée 999 mais dérivée des macros = 4*10 = 40 (D12).
        // Le filtre doit utiliser la kcal effective (cohérent avec l'affichage).
        val ciqual = food("a", "Lean", source = FoodSource.CIQUAL, kcal = 999f, p = 10f, c = 0f, f = 0f)
        assertEquals(40f, foodNutrientValue(ciqual, NutrientKey.KCAL), 0.001f)
        assertTrue(
            "≤100 doit passer car la kcal effective vaut 40, pas 999",
            passesThresholds(ciqual, listOf(NutrientThreshold(NutrientKey.KCAL, ThresholdOp.LTE, 100f))),
        )
        assertFalse(
            "≥500 doit échouer car la kcal effective vaut 40",
            passesThresholds(ciqual, listOf(NutrientThreshold(NutrientKey.KCAL, ThresholdOp.GTE, 500f))),
        )
    }

    @Test
    fun `search reaches archived foods only when showArchived is on`() {
        val foods = listOf(
            food("a", "Apple juice"),
            food("b", "Apple pie", archived = true),
        )
        // Par défaut : l'archivé est hors du pool de recherche.
        val hidden = buildCatalogue(foods, query = "apple", thresholds = emptyList(), showArchived = false)
        assertEquals(listOf("a"), hidden.flatMap { it.foods }.map { it.uuid })
        // showArchived = true : l'archivé redevient cherchable.
        val shown = buildCatalogue(foods, query = "apple", thresholds = emptyList(), showArchived = true)
        assertEquals(listOf("a", "b"), shown.flatMap { it.foods }.map { it.uuid })
    }
}
