package com.example.sportapp.feature.nutrition.domain

import com.example.sportapp.core.data.model.Meal
import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.core.data.model.NutritionGoal
import com.example.sportapp.core.stats.ChartGranularity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Logique pure des Stats nutrition (A6) — sans Compose ni Android, testable en
 * isolation. Miroir Android de `appli-web/.../nutrition/nutrition-stats-utils.ts`.
 *
 * Contrainte produit (mémoire `nutrition-android-nav-mode`) : un seul macro à la
 * fois (sélecteur), pas la grille tout-en-un du web. On ne calcule donc que la
 * série + le top aliments du macro sélectionné. Totaux dérivés des snapshots
 * per-100 g des entries (D5), comme le reste du domaine nutrition.
 */

/**
 * Une ligne du « Top aliments » : un aliment (regroupé par ref stable food/recipe,
 * repli sur le displayName) avec sa somme du macro sur la période + sa part du total.
 */
data class TopFood(
    /** Clé de regroupement (foodUUID ?? recipeUUID ?? displayName). */
    val key: String,
    val displayName: String,
    /** Somme du macro sur la période (kcal ou g). */
    val value: Float,
    /** Part du total de la période, 0..1 (0 si total nul). */
    val share: Float,
)

/**
 * Classe les aliments par apport décroissant pour un macro sur [startIso, endIso]
 * inclus. Agrège par aliment (ref stable, repli sur le nom affiché), somme le macro
 * dérivé du snapshot per-100 g, ignore les apports ≤ 0 et les entries dont le repas
 * parent est absent ou hors période. Pur — alimente la liste « Top aliments ».
 */
fun topFoodsByMacro(
    entries: List<MealEntry>,
    meals: List<Meal>,
    startIso: String,
    endIso: String,
    macroKey: MacroKey,
): List<TopFood> {
    val mealById = meals.associateBy { it.uuid }
    val acc = LinkedHashMap<String, Pair<String, Float>>()  // key -> (displayName, value)
    var total = 0f
    for (e in entries) {
        val meal = mealById[e.mealUUID] ?: continue
        if (meal.date < startIso || meal.date > endIso) continue
        val v = entryTotals(e).valueOf(macroKey)
        if (v <= 0f) continue
        val key = e.foodUUID ?: e.recipeUUID ?: e.displayName
        val cur = acc[key]
        acc[key] = (cur?.first ?: e.displayName) to ((cur?.second ?: 0f) + v)
        total += v
    }
    return acc.map { (key, pair) ->
        TopFood(key, pair.first, pair.second, if (total > 0f) pair.second / total else 0f)
    }.sortedWith(compareByDescending<TopFood> { it.value }.thenBy { it.displayName })
}

/** Série d'une macro sur la période : valeurs consommées + cibles alignées sur les buckets. */
data class MacroSeries(
    val buckets: List<String>,
    /** Consommé par bucket (jour ou semaine selon la granularité). */
    val consumed: List<Float>,
    /** Cible cumulée par bucket (Σ cible active de chaque jour du bucket). Vide possible. */
    val target: List<Float>,
)

/** Date du repas le plus ancien (borne basse réelle pour clamper la période « Tout »). */
fun earliestMealDate(meals: List<Meal>): String? =
    meals.minOfOrNull { it.date }

/** Granularité d'affichage : jour si ≤ 14 jours de période, sinon semaine (miroir web). */
fun granularityFor(startIso: String, endIso: String): ChartGranularity {
    val days = ChronoUnit.DAYS.between(LocalDate.parse(startIso), LocalDate.parse(endIso)) + 1
    return if (days <= 14) ChartGranularity.DAILY else ChartGranularity.WEEKLY
}

/**
 * Bucket semaine équivalent SQLite strftime('%Y-%W') : 00-53, lundi premier jour
 * (miroir Stats sport + web `weekBucket`). "YYYY-WW".
 */
fun weekBucket(day: String): String {
    val date = LocalDate.parse(day)
    val dayOfYear = date.dayOfYear                       // 1-based
    val mondayBased = (date.dayOfWeek.value + 6) % 7     // Monday=0 .. Sunday=6
    val week = Math.floorDiv(dayOfYear - 1 - mondayBased + 7, 7)
    return "${date.year}-${week.toString().padStart(2, '0')}"
}

fun bucketOf(day: String, gran: ChartGranularity): String =
    if (gran == ChartGranularity.DAILY) day else weekBucket(day)

/** Cible d'un macro depuis un goal (fibres dérivées du kcal, comme le journal). 0 si pas de cible. */
private fun macroTargetOf(goal: NutritionGoal?, key: MacroKey): Float = when {
    goal == null -> 0f
    key == MacroKey.KCAL -> goal.kcal
    key == MacroKey.CARBS -> goal.carbsG
    key == MacroKey.FAT -> goal.fatG
    key == MacroKey.PROTEIN -> goal.proteinG
    else -> fiberTargetG(goal.kcal) ?: 0f
}

/**
 * Agrège, pour le macro choisi, le consommé + la cible par bucket sur [startIso,
 * endIso] inclus. Énumère chaque jour de la période (buckets continus, jours à 0
 * inclus) pour que la courbe de cible reste continue même les jours sans saisie.
 * Pur — alimente le graphe (barres / courbe). La cible d'un bucket = Σ sur chaque
 * jour du bucket de la cible active ce jour-là (§3.7, D8 pas de goal hebdo dédié).
 */
fun aggregateMacroSeries(
    entries: List<MealEntry>,
    meals: List<Meal>,
    goals: List<NutritionGoal>,
    startIso: String,
    endIso: String,
    gran: ChartGranularity,
    macroKey: MacroKey,
): MacroSeries {
    val mealDateByUuid = meals.associate { it.uuid to it.date }
    val consumedByDay = HashMap<String, Float>()
    for (e in entries) {
        val date = mealDateByUuid[e.mealUUID] ?: continue
        if (date < startIso || date > endIso) continue
        consumedByDay[date] = (consumedByDay[date] ?: 0f) + entryTotals(e).valueOf(macroKey)
    }

    val buckets = mutableListOf<String>()
    val index = HashMap<String, Int>()
    val consumed = mutableListOf<Float>()
    val target = mutableListOf<Float>()

    var d = LocalDate.parse(startIso)
    val end = LocalDate.parse(endIso)
    while (!d.isAfter(end)) {
        val day = d.toString()
        val b = bucketOf(day, gran)
        val idx = index.getOrPut(b) {
            buckets.add(b); consumed.add(0f); target.add(0f); buckets.size - 1
        }
        consumedByDay[day]?.let { consumed[idx] = consumed[idx] + it }
        target[idx] = target[idx] + macroTargetOf(activeGoalFor(goals, day), macroKey)
        d = d.plusDays(1)
    }
    return MacroSeries(buckets, consumed, target)
}
