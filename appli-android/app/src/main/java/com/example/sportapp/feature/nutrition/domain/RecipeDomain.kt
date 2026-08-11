package com.example.sportapp.feature.nutrition.domain

import com.example.sportapp.core.data.model.Food
import com.example.sportapp.core.data.model.Recipe
import com.example.sportapp.core.data.model.RecipeIngredient

/**
 * Logique pure des Recettes & repas enregistrés (A4) — sans Compose ni Android,
 * testable en isolation. Miroir Android de `appli-web/.../nutrition/recipe-utils.ts`.
 *
 * Les ingrédients sont des références VIVANTES vers Food (pas de snapshot : une
 * recette est un modèle) → tout est recalculé à la volée depuis le catalogue.
 */

/** Type d'une recette (`kind`), UPPER_CASE (politique 11). Miroir serveur + web. */
object RecipeKind {
    /** Plat composé : inséré au journal comme un aliment, macros AU PRORATA du poids consommé. */
    const val RECIPE = "RECIPE"

    /** Repas enregistré : ses ingrédients sont insérés tels quels dans une période, en un tap. */
    const val SAVED_MEAL = "SAVED_MEAL"
}

/**
 * Macros + micros dérivés d'une recette (NUTRITION_DESIGN §3.3).
 *
 * [totals] : totaux des ingrédients (somme des per-100 g × quantité).
 * [ingredientsWeightG] : somme des quantités d'ingrédients (poids cru).
 * [weightBaseG] : base de calcul per-100 g = `totalWeightG` (poids cuit, kind=RECIPE)
 *   sinon le poids cru.
 * [per100g] : macros per-100 g du plat — snapshot à poser sur une MealEntry kind=RECIPE.
 * [microTotals] : micros agrégés (totaux absolus de la recette), 0 si absent.
 * [microPer100g] : micros per-100 g du plat (microTotals rapportés à weightBaseG) —
 *   snapshot à poser sur une MealEntry kind=RECIPE (symétrise le tracking micros).
 */
data class RecipeMacros(
    val totals: MacroTotals,
    val ingredientsWeightG: Float,
    val weightBaseG: Float,
    val per100g: MacroTotals,
    val microTotals: Map<MicroKey, Float>,
    val microPer100g: Map<MicroKey, Float>,
)

/** Recettes scindées par kind pour un affichage en deux sections (Plats / Repas enregistrés). */
data class RecipesByKind<T>(
    /** kind=RECIPE (plats composés, insérés au prorata du poids consommé). */
    val recipes: List<T>,
    /** kind=SAVED_MEAL (repas enregistrés, ingrédients insérés tels quels en un tap). */
    val savedMeals: List<T>,
)

/**
 * Sépare une liste en plats (RECIPE) et repas enregistrés (SAVED_MEAL), en préservant
 * l'ordre d'entrée. [kindOf] extrait le `kind` de chaque élément. Pur, testable.
 */
fun <T> splitRecipesByKind(rows: List<T>, kindOf: (T) -> String): RecipesByKind<T> {
    val recipes = mutableListOf<T>()
    val savedMeals = mutableListOf<T>()
    for (row in rows) {
        if (kindOf(row) == RecipeKind.SAVED_MEAL) savedMeals.add(row) else recipes.add(row)
    }
    return RecipesByKind(recipes, savedMeals)
}

/**
 * Calcule les macros + micros d'une recette depuis ses ingrédients et le catalogue.
 * Les ingrédients dont le Food n'existe plus sont ignorés (référence vivante).
 * `totalWeightG` (kind=RECIPE) gère le ratio cru/cuit : les mêmes macros réparties
 * sur le poids final cuit. Pur — miroir de `recipeMacros` web.
 */
fun recipeMacros(
    recipe: Recipe,
    ingredients: List<RecipeIngredient>,
    foodsByUuid: Map<String, Food>,
): RecipeMacros {
    var kcal = 0f; var protein = 0f; var carbs = 0f; var fat = 0f; var fiber = 0f
    val microTotals = MicroKey.entries.associateWith { 0f }.toMutableMap()
    var ingredientsWeightG = 0f
    for (ing in ingredients) {
        val food = foodsByUuid[ing.foodUUID] ?: continue
        val f = ing.quantityG / 100f
        // kcal effective selon la source de l'ingrédient (D12) — cohérent avec la kcal
        // snapshotée des entries du journal.
        kcal += effectiveFoodKcal(food) * f
        protein += food.proteinPer100g * f
        carbs += food.carbsPer100g * f
        fat += food.fatPer100g * f
        fiber += (food.fiberPer100g ?: 0f) * f
        // Micros : même règle que les macros (per-100 g × facteur quantité, null traité 0) — D11.
        for (k in MicroKey.entries) {
            microTotals[k] = (microTotals[k] ?: 0f) + (food.microPer100g(k) ?: 0f) * f
        }
        ingredientsWeightG += ing.quantityG
    }
    val totals = MacroTotals(kcal = kcal, protein = protein, carbs = carbs, fat = fat, fiber = fiber)
    val weightBaseG =
        if (recipe.kind == RecipeKind.RECIPE && (recipe.totalWeightG ?: 0f) > 0f) recipe.totalWeightG!!
        else ingredientsWeightG
    val per100g =
        if (weightBaseG > 0f) MacroTotals(
            kcal = totals.kcal / weightBaseG * 100f,
            protein = totals.protein / weightBaseG * 100f,
            carbs = totals.carbs / weightBaseG * 100f,
            fat = totals.fat / weightBaseG * 100f,
            fiber = totals.fiber / weightBaseG * 100f,
        ) else ZERO_TOTALS
    // Micros per-100 g : mêmes totaux rapportés au poids de base (0 si recette vide).
    val microPer100g = MicroKey.entries.associateWith { 0f }.toMutableMap()
    if (weightBaseG > 0f) {
        for (k in MicroKey.entries) {
            microPer100g[k] = (microTotals[k] ?: 0f) / weightBaseG * 100f
        }
    }
    return RecipeMacros(totals, ingredientsWeightG, weightBaseG, per100g, microTotals, microPer100g)
}
