package com.example.sportapp.feature.nutrition.domain

import com.example.sportapp.core.data.model.Food

/**
 * Cohérence kcal ↔ macros (NUTRITION_DESIGN D12) — miroir Android de
 * `appli-web/.../nutrition/food-kcal.ts`. Module PUR (sans Compose ni Android),
 * testable seul.
 *
 * Convention unique : glucides = glucides *disponibles* (fibres exclues), fibres
 * = poste calorique à part. Formule de référence (Atwater + fibres 2 kcal/g,
 * EU 1169/2011) : `kcal = 4·P + 4·G + 9·L + 2·fibres`.
 *
 * La kcal *effective* d'un aliment dépend de sa source :
 *   - `CIQUAL` (brut)  → kcal dérivée des macros (cohérence macros↔kcal).
 *   - `OFF` (étiqueté) → kcal de la source (l'étiquette fait foi).
 *   - `CUSTOM`         → kcal saisie si fournie (> 0), sinon dérivée.
 */

/** Sources d'aliment (UPPER_CASE, politique 11). */
object FoodSource {
    const val CUSTOM = "CUSTOM"
    const val CIQUAL = "CIQUAL"
    const val OFF = "OFF"
}

/**
 * kcal pour 100 g dérivée des macros : `4·P + 4·G + 9·L + 2·fibres` (D12).
 * fibres null → 0 (poste fibres absent). Pur.
 */
fun kcalFromMacros(proteinG: Float, carbsG: Float, fatG: Float, fiberG: Float? = 0f): Float =
    4f * proteinG + 4f * carbsG + 9f * fatG + 2f * (fiberG ?: 0f)

/**
 * kcal effective per-100 g d'un aliment selon sa source (D12). Pur : `CIQUAL`
 * dérive des macros, `OFF` garde l'étiquette, `CUSTOM` garde la valeur saisie si
 * fournie (> 0) sinon dérive. Source inconnue → valeur stockée (repli sûr).
 */
fun effectiveFoodKcal(food: Food): Float {
    val derived = kcalFromMacros(
        food.proteinPer100g,
        food.carbsPer100g,
        food.fatPer100g,
        food.fiberPer100g,
    )
    return when (food.source) {
        FoodSource.CIQUAL -> derived
        FoodSource.OFF -> food.kcalPer100g
        FoodSource.CUSTOM -> if (food.kcalPer100g > 0f) food.kcalPer100g else derived
        else -> food.kcalPer100g
    }
}
