package com.example.sportapp.feature.nutrition

import com.example.sportapp.core.data.model.Food
import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.core.data.model.Recipe
import com.example.sportapp.core.data.model.RecipeIngredient
import com.example.sportapp.feature.nutrition.domain.MicroKey
import com.example.sportapp.feature.nutrition.domain.RecipeKind
import com.example.sportapp.feature.nutrition.domain.entryTotals
import com.example.sportapp.feature.nutrition.domain.recipeMacros
import com.example.sportapp.feature.nutrition.domain.splitRecipesByKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests JVM purs de la logique des Recettes & repas enregistrés (A4). */
class RecipeDomainTest {

    /** source=CUSTOM avec kcal explicite → effectiveFoodKcal renvoie kcalPer100g (test déterministe). */
    private fun food(
        uuid: String, kcal: Float, p: Float = 0f, c: Float = 0f, f: Float = 0f, iron: Float? = null,
    ) = Food(
        uuid = uuid, userId = 1, name = uuid, source = "CUSTOM",
        kcalPer100g = kcal, proteinPer100g = p, carbsPer100g = c, fatPer100g = f, ironPer100g = iron,
    )

    private fun recipe(uuid: String, kind: String, weight: Float? = null) =
        Recipe(uuid = uuid, userId = 1, name = uuid, kind = kind, totalWeightG = weight)

    private fun ingredient(recipe: String, food: String, qty: Float, order: Int) =
        RecipeIngredient(uuid = "$recipe-$food", recipeUUID = recipe, foodUUID = food, quantityG = qty, orderIndex = order)

    @Test
    fun `recipeMacros sums ingredient macros scaled by quantity`() {
        val foods = mapOf(
            "egg" to food("egg", kcal = 150f, p = 13f),
            "oat" to food("oat", kcal = 380f, c = 60f),
        )
        val r = recipe("r", RecipeKind.SAVED_MEAL)
        val macros = recipeMacros(
            r,
            listOf(ingredient("r", "egg", 100f, 0), ingredient("r", "oat", 50f, 1)),
            foods,
        )
        // egg: 150 kcal + 13 P ; oat (×0.5): 190 kcal + 30 C
        assertEquals(340f, macros.totals.kcal, 0.01f)
        assertEquals(13f, macros.totals.protein, 0.01f)
        assertEquals(30f, macros.totals.carbs, 0.01f)
        assertEquals(150f, macros.ingredientsWeightG, 0.01f)
        // SAVED_MEAL → base de poids = poids cru (150 g).
        assertEquals(150f, macros.weightBaseG, 0.01f)
    }

    @Test
    fun `RECIPE prorates per-100g to the cooked weight`() {
        // 200 g d'un aliment 100 kcal / 10 P → totaux 200 kcal / 20 P, poids cru 200 g.
        // Poids cuit déclaré 100 g → per-100g rapporté au poids cuit.
        val foods = mapOf("rice" to food("rice", kcal = 100f, p = 10f))
        val r = recipe("r", RecipeKind.RECIPE, weight = 100f)
        val macros = recipeMacros(r, listOf(ingredient("r", "rice", 200f, 0)), foods)

        assertEquals(200f, macros.totals.kcal, 0.01f)
        assertEquals(100f, macros.weightBaseG, 0.01f)         // = poids cuit
        assertEquals(200f, macros.per100g.kcal, 0.01f)        // 200 kcal sur 100 g cuit
        assertEquals(20f, macros.per100g.protein, 0.01f)
    }

    @Test
    fun `consuming half the cooked dish logs half its macros (pro-rata)`() {
        val foods = mapOf("rice" to food("rice", kcal = 100f, p = 10f))
        val r = recipe("r", RecipeKind.RECIPE, weight = 100f)
        val per100g = recipeMacros(r, listOf(ingredient("r", "rice", 200f, 0)), foods).per100g

        // L'entry journal snapshote per100g ; consommer 50 g (= moitié des 100 g cuits).
        val entry = MealEntry(
            uuid = "e", mealUUID = "m", displayName = "r", quantityG = 50f,
            kcalPer100g = per100g.kcal, proteinPer100g = per100g.protein,
            carbsPer100g = per100g.carbs, fatPer100g = per100g.fat,
        )
        val totals = entryTotals(entry)
        // Moitié du plat = moitié de 200 kcal / 20 P.
        assertEquals(100f, totals.kcal, 0.01f)
        assertEquals(10f, totals.protein, 0.01f)
    }

    @Test
    fun `RECIPE without cooked weight bases per-100g on raw weight`() {
        val foods = mapOf("rice" to food("rice", kcal = 100f))
        val r = recipe("r", RecipeKind.RECIPE, weight = null)
        val macros = recipeMacros(r, listOf(ingredient("r", "rice", 200f, 0)), foods)
        assertEquals(200f, macros.weightBaseG, 0.01f)   // repli sur le poids cru
        assertEquals(100f, macros.per100g.kcal, 0.01f)  // 200 kcal sur 200 g
    }

    @Test
    fun `recipeMacros ignores ingredients whose food is missing`() {
        val foods = mapOf("a" to food("a", kcal = 100f))
        val r = recipe("r", RecipeKind.SAVED_MEAL)
        val macros = recipeMacros(
            r,
            listOf(ingredient("r", "a", 100f, 0), ingredient("r", "ghost", 100f, 1)),
            foods,
        )
        assertEquals(100f, macros.totals.kcal, 0.01f)
        assertEquals(100f, macros.ingredientsWeightG, 0.01f)
    }

    @Test
    fun `micros aggregate and prorate like macros`() {
        // 200 g d'un aliment 5 mg fer / 100 g → 10 mg total ; poids cuit 100 g → 10 mg / 100 g.
        val foods = mapOf("spinach" to food("spinach", kcal = 20f, iron = 5f))
        val r = recipe("r", RecipeKind.RECIPE, weight = 100f)
        val macros = recipeMacros(r, listOf(ingredient("r", "spinach", 200f, 0)), foods)
        assertEquals(10f, macros.microTotals[MicroKey.IRON]!!, 0.01f)
        assertEquals(10f, macros.microPer100g[MicroKey.IRON]!!, 0.01f)
    }

    @Test
    fun `splitRecipesByKind separates dishes and saved meals preserving order`() {
        val list = listOf(
            recipe("a", RecipeKind.RECIPE),
            recipe("b", RecipeKind.SAVED_MEAL),
            recipe("c", RecipeKind.RECIPE),
        )
        val split = splitRecipesByKind(list) { it.kind }
        assertEquals(listOf("a", "c"), split.recipes.map { it.uuid })
        assertEquals(listOf("b"), split.savedMeals.map { it.uuid })
    }
}
