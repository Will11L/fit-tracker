package com.example.sportapp.feature.nutrition.domain

import com.example.sportapp.core.data.model.HealthGoal
import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.core.data.model.WaterIntake
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Logique pure de l'Hydratation (2026-07-05) — sans Compose ni Android, testable
 * en isolation. Total du jour affiché sur la card du journal Nutrition.
 *
 * Le total du jour = prises manuelles (water_intakes) + boissons eau journalisées
 * (meal_entries dont l'aliment est `is_water`, converties **1 g = 1 ml**). Ce total
 * est CALCULÉ (pas de row matérialisée pour les entrées repas) : retirer une entrée
 * repas retire mécaniquement son volume, sans incohérence.
 *
 * L'objectif journalier est un HealthGoal `type = WATER_ML` versionné par
 * effective_from (même sémantique que STEPS / NutritionGoal).
 */

const val WATER_GOAL_TYPE = "WATER_ML"

/**
 * Un aliment OFF est de l'eau si l'une de ses `categoriesTags` appartient à la
 * famille OFF « waters » (`en:waters` et sous-catégories : mineral-waters,
 * spring-waters, sparkling-waters…). Heuristique posée à l'import (le flag
 * `Food.isWater` est ensuite la source de vérité, éditable manuellement).
 */
fun detectWaterFromOffCategories(categoriesTags: List<String>): Boolean =
    categoriesTags.any {
        val slug = it.substringAfter(':')
        slug == "waters" || slug.endsWith("-waters") || slug.endsWith("-water")
    }

/** Objectif d'hydratation actif un jour J (ml) = HealthGoal WATER_ML au plus grand
 *  `effectiveFrom` ≤ J. null si aucun objectif défini. */
fun activeWaterGoalMl(goals: List<HealthGoal>, day: String): Int? =
    goals.filter { it.type == WATER_GOAL_TYPE && it.effectiveFrom <= day }
        .maxByOrNull { it.effectiveFrom }
        ?.target
        ?.roundToInt()

/** Volume issu des prises manuelles (water_intakes) du jour (ml). */
fun manualWaterMl(intakes: List<WaterIntake>, day: String): Int =
    intakes.filter { it.date == day }.sumOf { it.amountMl }

/** Volume issu des entrées repas « eau » du jour (1 g = 1 ml). `waterFoodUuids` =
 *  uuids des aliments marqués `is_water`. */
fun mealWaterMl(
    dayMealUuids: Set<String>,
    entries: List<MealEntry>,
    waterFoodUuids: Set<String>,
): Int {
    var grams = 0f
    for (e in entries) {
        if (e.mealUUID in dayMealUuids && e.foodUUID != null && e.foodUUID in waterFoodUuids) {
            grams += e.quantityG
        }
    }
    return grams.roundToInt()
}

/** Total d'hydratation du jour (ml) = prises manuelles + boissons eau journalisées. */
fun dayHydrationMl(
    day: String,
    intakes: List<WaterIntake>,
    dayMealUuids: Set<String>,
    entries: List<MealEntry>,
    waterFoodUuids: Set<String>,
): Int = manualWaterMl(intakes, day) + mealWaterMl(dayMealUuids, entries, waterFoodUuids)

/** UUID déterministe d'un objectif d'hydratation (user + type + jour) : re-régler le
 *  même jour upsert la même row au lieu de créer un doublon (pattern STEPS). */
fun waterGoalUuid(userId: Int, effectiveFrom: String): String =
    UUID.nameUUIDFromBytes("health_goal:$userId:$WATER_GOAL_TYPE:$effectiveFrom".toByteArray(Charsets.UTF_8))
        .toString()
