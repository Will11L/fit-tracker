package com.example.sportapp.feature.nutrition.domain

import com.example.sportapp.core.data.model.Meal
import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.core.data.model.NutritionGoal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Analyse pure d'une cible nutrition (page Objectifs A5) — miroir Android de
 * `appli-web/.../nutrition/{goal-macros,goal-analysis}.ts` + l'agrégat 7 jours
 * de `nutrition-stats-utils.ts`. Sans Compose ni Android, testable en isolation.
 *
 * Convention macro-first (NUTRITION_DESIGN D12) : l'utilisateur saisit seulement
 * les 3 macros (P/G/L) ; le total kcal et la cible fibres sont DÉRIVÉS. Les fibres
 * ne sont pas une colonne — la cible fibres = 15 g / 1000 kcal de l'objectif.
 */

/** Cible fibres santé : 15 g pour 1000 kcal (reco EU). Cohérent avec fiberTargetG. */
const val FIBER_G_PER_1000_KCAL = 15f

/** Part calorique réservée aux fibres : 15 g/1000 kcal × 2 kcal/g = 0,03 du total. */
private const val FIBER_KCAL_FRACTION = (FIBER_G_PER_1000_KCAL * 2f) / 1000f // 0,03

/** Total kcal + fibres dérivés des 3 macros (macro-first, D12). */
data class DerivedGoal(val kcal: Float, val fiberG: Float)

/**
 * Total kcal + fibres dérivés des 3 macros (macro-first, D12). Le `/ 0,97` résout
 * la circularité fibres↔kcal (les fibres dépendent du total, qui dépend des fibres).
 * Ex. P180/G250/L80 → base 2440 → total 2515 kcal, 38 g fibres. Pur.
 */
fun deriveGoalFromMacros(proteinG: Float, carbsG: Float, fatG: Float): DerivedGoal {
    val base = 4f * proteinG + 4f * carbsG + 9f * fatG
    val kcal = base / (1f - FIBER_KCAL_FRACTION) // base / 0,97
    val fiberG = FIBER_G_PER_1000_KCAL * kcal / 1000f
    return DerivedGoal(kcal, fiberG)
}

/** Part calorique d'un macro de la cible : kcal apportées + % du total (0..100). */
data class MacroKcalShare(val key: MacroKey, val kcal: Float, val percent: Float)

/** Ordre canonique des parts du donut (= les 4 macros porteurs de kcal). */
val KCAL_MACRO_KEYS: List<MacroKey> = listOf(MacroKey.CARBS, MacroKey.FAT, MacroKey.PROTEIN, MacroKey.FIBER)

/**
 * Répartition calorique de la cible par macro (facteurs Atwater 4·P + 4·G + 9·L +
 * 2·fibres, D12). Les fibres dérivent du kcal (15 g/1000 kcal) ; leur poste (~3 %)
 * est inclus pour que la somme des parts fasse 100 %. Pur.
 */
fun macroKcalBreakdown(goal: NutritionGoal): List<MacroKcalShare> {
    val fiberG = fiberTargetG(goal.kcal) ?: 0f
    val kcalByKey = mapOf(
        MacroKey.CARBS to 4f * goal.carbsG,
        MacroKey.FAT to 9f * goal.fatG,
        MacroKey.PROTEIN to 4f * goal.proteinG,
        MacroKey.FIBER to 2f * fiberG,
    )
    val total = kcalByKey.values.sum()
    return KCAL_MACRO_KEYS.map { key ->
        val kcal = kcalByKey.getValue(key)
        MacroKcalShare(key, kcal, if (total > 0f) kcal / total * 100f else 0f)
    }
}

/**
 * Apport d'un macro rapporté au poids corporel (g/kg). null si le poids n'est pas
 * renseigné dans le profil (dégradation propre : la page affiche `—`). Pur.
 */
fun macroPerKg(macroG: Float, weightKg: Float?): Float? =
    if (weightKg != null && weightKg > 0f) macroG / weightKg else null

/**
 * Densité en fibres de la cible (g / 1000 kcal). Par construction ≈ 15 (cible
 * dérivée à 15 g/1000 kcal) — exposée comme repère santé. 0 si pas de kcal. Pur.
 */
fun fiberDensity(kcal: Float): Float {
    val fiberG = fiberTargetG(kcal) ?: 0f
    return if (kcal > 0f) fiberG / (kcal / 1000f) else 0f
}

/** Nombre de jours calendaires inclus dans [startIso, endIso] (≥ 1). */
fun rangeDayCountInclusive(startIso: String, endIso: String): Int {
    val start = runCatching { LocalDate.parse(startIso) }.getOrNull() ?: return 1
    val end = runCatching { LocalDate.parse(endIso) }.getOrNull() ?: return 1
    return (ChronoUnit.DAYS.between(start, end) + 1).toInt().coerceAtLeast(1)
}

/**
 * Moyenne consommée par jour sur [startIso, endIso] inclus (cumuls dérivés des
 * snapshots per-100 g, divisés par le nombre de jours calendaires — les jours sans
 * saisie comptent comme 0, comme la moyenne /jour de la page Objectifs web). Pur —
 * alimente le comparatif « cible vs réel 7 j » (barres + radar).
 */
fun averageDailyConsumed(
    meals: List<Meal>,
    entries: List<MealEntry>,
    startIso: String,
    endIso: String,
): MacroTotals {
    val mealDateByUuid: Map<String, String> = meals.associate { it.uuid to it.date }
    var kcal = 0f; var protein = 0f; var carbs = 0f; var fat = 0f; var fiber = 0f
    for (e in entries) {
        val date = mealDateByUuid[e.mealUUID] ?: continue
        if (date < startIso || date > endIso) continue
        val t = entryTotals(e)
        kcal += t.kcal; protein += t.protein; carbs += t.carbs; fat += t.fat; fiber += t.fiber
    }
    val days = rangeDayCountInclusive(startIso, endIso).toFloat()
    return MacroTotals(kcal / days, protein / days, carbs / days, fat / days, fiber / days)
}
