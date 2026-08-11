package com.example.sportapp.feature.nutrition.domain

import com.example.sportapp.core.data.model.Meal
import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.core.data.model.MealPreset
import com.example.sportapp.core.data.model.NutritionGoal

/**
 * Logique pure du Journal nutrition (sections, cumuls, anneaux du calendrier) —
 * sans Compose ni Android, testable en isolation. Miroir Android de
 * `appli-web/.../nutrition/{journal-utils,journal-month-utils,macro-colors}.ts`.
 *
 * Totaux dérivés des snapshots per-100 g (D5) : total = per100g × quantityG / 100.
 * Les couleurs/labels d'affichage des macros vivent côté UI (NutritionColors.kt +
 * strings.xml, politique 18) — ici on ne manipule que des clés et des nombres.
 */

/** Les 5 macros affichées. UPPER_CASE (politique 11). Ordre canonique d'affichage. */
enum class MacroKey { KCAL, CARBS, FAT, PROTEIN, FIBER }

/** Macros portées par les 4 anneaux concentriques du calendrier (fibres exclues). */
enum class RingMacroKey { KCAL, CARBS, FAT, PROTEIN }

/** Totaux kcal + macros dérivés des snapshots per-100 g des entries. */
data class MacroTotals(
    val kcal: Float = 0f,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    /** Fibres (D11) — snapshot optionnel, null traité comme 0 dans les cumuls. */
    val fiber: Float = 0f,
) {
    fun valueOf(key: MacroKey): Float = when (key) {
        MacroKey.KCAL -> kcal
        MacroKey.CARBS -> carbs
        MacroKey.FAT -> fat
        MacroKey.PROTEIN -> protein
        MacroKey.FIBER -> fiber
    }
}

val ZERO_TOTALS = MacroTotals()

/** Totaux d'une entry depuis son snapshot per-100 g. */
fun entryTotals(e: MealEntry): MacroTotals {
    val f = e.quantityG / 100f
    return MacroTotals(
        kcal = e.kcalPer100g * f,
        protein = e.proteinPer100g * f,
        carbs = e.carbsPer100g * f,
        fat = e.fatPer100g * f,
        fiber = (e.fiberPer100g ?: 0f) * f,
    )
}

/** Cumul des totaux d'une liste d'entries. */
fun sumTotals(entries: List<MealEntry>): MacroTotals {
    var kcal = 0f; var protein = 0f; var carbs = 0f; var fat = 0f; var fiber = 0f
    for (e in entries) {
        val t = entryTotals(e)
        kcal += t.kcal; protein += t.protein; carbs += t.carbs; fat += t.fat; fiber += t.fiber
    }
    return MacroTotals(kcal, protein, carbs, fat, fiber)
}

/**
 * Cible fibres dérivée : 15 g pour 1000 kcal de l'objectif calorique (reco santé
 * courante). null si aucune cible kcal (pas de barre, comme les autres macros).
 */
fun fiberTargetG(kcalGoal: Float?): Float? =
    if (kcalGoal != null && kcalGoal > 0f) (kcalGoal / 1000f) * 15f else null

/**
 * Total sucres du jour (g) depuis les snapshots per-100g des entries (total = per100g × q / 100,
 * null traité comme 0). Note sémantique : sugarPer100g = sucres TOTAUX (OFF) — comparés au
 * plafond « totaux » de [sugarLimitsG] (fruits comptés, plafond calibré en conséquence).
 * Miroir de `sumSugarG` web.
 */
fun sumSugarG(entries: List<MealEntry>): Float =
    entries.fold(0f) { acc, e -> acc + (e.sugarPer100g ?: 0f) * (e.quantityG / 100f) }

/** Plafond des sucres (g/jour) + repère « idéal » — cf. [sugarLimitsG]. */
data class SugarLimits(val limitG: Float, val idealG: Float)

/**
 * Plafond sucres (g/jour) sur les sucres TOTAUX, combinant les deux repères officiels (décision
 * 2026-07-13) : proportionnel à la cible kcal active — g = 5 % du nombre de kcal (≡ 20 % de
 * l'AET ÷ 4 kcal/g, transposition « totaux » de la limite OMS sucres libres, le ×2 absorbant
 * les sucres naturels fruits/laitages) — borné au repère français ANSES de 100 g/j (atteint à
 * 2000 kcal ; plus de calories ne justifie pas plus de sucre). Ex. 1800 → 90 g, 2600 → 100 g.
 * Repère « idéal » = moitié du plafond (miroir du rapport OMS 10 % → 5 %). Contrairement à
 * fiberTargetG, jamais null : sans cible kcal active, repli 2000 kcal → 100 g / 50 g.
 * Miroir de `sugarLimitsG` web.
 */
fun sugarLimitsG(kcalGoal: Float?): SugarLimits {
    val kcal = if (kcalGoal != null && kcalGoal > 0f) kcalGoal else 2000f
    val limitG = minOf(kcal * 0.05f, 100f)
    return SugarLimits(limitG = limitG, idealG = limitG / 2f)
}

/**
 * Seuil « riche en sucres » PAR ALIMENT (per-100 g) : repère étiquetage UK « high in sugar »
 * (> 22,5 g/100 g). Distinct du plafond JOURNALIER [sugarLimitsG] (bandeau) : dans les listes,
 * le sucre est une INFORMATION pour repérer les aliments sucrés au moment de choisir.
 * Miroir de `HIGH_SUGAR_PER_100G` web.
 */
const val HIGH_SUGAR_PER_100G = 22.5f

/** Aliment « riche en sucres » (> 22,5 g/100 g) → couleur d'alerte dans les rows. false si non renseigné. Miroir web. */
fun isHighSugar(sugarPer100g: Float?): Boolean =
    sugarPer100g != null && sugarPer100g > HIGH_SUGAR_PER_100G

/**
 * Sucres consommés d'une entry (g) : snapshot per-100 g × quantité / 100 — null si le snapshot
 * ne les connaît pas (contrairement à [sumSugarG] qui traite null comme 0 dans le cumul du jour).
 * Alimente le dépli micros d'une ligne du journal (MacroEntryRow). Miroir de `entrySugarG` web.
 */
fun MealEntry.consumedSugarG(): Float? = sugarPer100g?.let { it * quantityG / 100f }

/**
 * Cible active pour un jour ("YYYY-MM-DD") = le goal avec le plus grand
 * `effectiveFrom` ≤ date (§3.7 NUTRITION_DESIGN). null si aucune.
 */
fun activeGoalFor(goals: List<NutritionGoal>, date: String): NutritionGoal? {
    var active: NutritionGoal? = null
    for (g in goals) {
        val current = active
        if (g.effectiveFrom <= date && (current == null || g.effectiveFrom > current.effectiveFrom)) {
            active = g
        }
    }
    return active
}

/** Cible d'un macro d'anneau depuis un goal (null si pas de goal). */
fun targetOf(goal: NutritionGoal?, key: RingMacroKey): Float? = when {
    goal == null -> null
    key == RingMacroKey.KCAL -> goal.kcal
    key == RingMacroKey.CARBS -> goal.carbsG
    key == RingMacroKey.FAT -> goal.fatG
    else -> goal.proteinG
}

/** Section du journal : un preset (meal éventuellement absent) ou un repas ad hoc. */
data class JournalSection(
    /** Clé stable : uuid du meal sinon uuid du preset. */
    val key: String,
    val name: String,
    val defaultTime: String?,
    /** null tant qu'aucune entry (pas de rows fantômes, §3.4 NUTRITION_DESIGN). */
    val meal: Meal?,
    /** orderIndex à utiliser si un meal doit être créé pour cette section. */
    val orderIndex: Int,
    /** uuid du preset (null pour ad hoc), à poser sur le meal créé. */
    val presetUuid: String?,
    val entries: List<MealEntry>,
    val totals: MacroTotals,
)

/**
 * Assemble les sections du journal d'un jour : presets triés par orderIndex
 * (appariés au meal du jour par presetUuid stable, repli sur le nom pour les
 * meals legacy presetUuid=null), puis les repas ad hoc restants dans l'ordre.
 * Pur — miroir de `buildSections` web.
 */
fun buildSections(
    presets: List<MealPreset>,
    dayMeals: List<Meal>,
    entries: List<MealEntry>,
): List<JournalSection> {
    val byMeal: Map<String, List<MealEntry>> = entries.groupBy { it.mealUUID }
    val matched = mutableSetOf<String>()
    val sections = mutableListOf<JournalSection>()

    for (p in presets.sortedBy { it.orderIndex }) {
        val meal = dayMeals.firstOrNull { !matched.contains(it.uuid) && it.presetUuid == p.uuid }
            ?: dayMeals.firstOrNull { !matched.contains(it.uuid) && it.presetUuid == null && it.name == p.name }
        if (meal != null) matched.add(meal.uuid)
        val mealEntries = meal?.let { byMeal[it.uuid] } ?: emptyList()
        sections.add(
            JournalSection(
                key = meal?.uuid ?: p.uuid,
                name = p.name,
                defaultTime = p.defaultTime,
                meal = meal,
                orderIndex = p.orderIndex,
                presetUuid = p.uuid,
                entries = mealEntries,
                totals = sumTotals(mealEntries),
            )
        )
    }

    val adHoc = dayMeals.filter { !matched.contains(it.uuid) }.sortedBy { it.orderIndex }
    for (meal in adHoc) {
        val mealEntries = byMeal[meal.uuid] ?: emptyList()
        sections.add(
            JournalSection(
                key = meal.uuid,
                name = meal.name,
                defaultTime = null,
                meal = meal,
                orderIndex = meal.orderIndex,
                presetUuid = null,
                entries = mealEntries,
                totals = sumTotals(mealEntries),
            )
        )
    }
    return sections
}

/** Cumuls + cibles + progression d'un jour pour les 4 anneaux d'une case calendrier. */
data class DayRingTotals(
    val date: String,
    val totals: MacroTotals,
    /** Vrai si ≥ 1 entry ce jour-là (sinon anneaux vides). */
    val hasData: Boolean,
    /** Progression 0..1 par macro d'anneau (0 si pas de cible). */
    val progress: Map<RingMacroKey, Float>,
)

private fun emptyRingTotals(date: String) = DayRingTotals(
    date = date,
    totals = ZERO_TOTALS,
    hasData = false,
    progress = RingMacroKey.entries.associateWith { 0f },
)

/**
 * Agrège, pour chaque jour fourni, les cumuls macros + la progression vs la cible
 * active de ce jour-là (activeGoalFor, §3.7). Jour sans entry → hasData=false ;
 * macro sans cible → progress 0. Pur — alimente les cases du calendrier mensuel.
 */
fun dailyTotalsForMonth(
    dayIsos: List<String>,
    entries: List<MealEntry>,
    meals: List<Meal>,
    goals: List<NutritionGoal>,
): Map<String, DayRingTotals> {
    val mealDateByUuid: Map<String, String> = meals.associate { it.uuid to it.date }
    val totalsByDay = mutableMapOf<String, MacroTotals>()
    val countByDay = mutableMapOf<String, Int>()

    for (e in entries) {
        val date = mealDateByUuid[e.mealUUID] ?: continue
        val acc = totalsByDay[date] ?: ZERO_TOTALS
        val t = entryTotals(e)
        totalsByDay[date] = MacroTotals(
            kcal = acc.kcal + t.kcal,
            protein = acc.protein + t.protein,
            carbs = acc.carbs + t.carbs,
            fat = acc.fat + t.fat,
            fiber = acc.fiber + t.fiber,
        )
        countByDay[date] = (countByDay[date] ?: 0) + 1
    }

    val out = mutableMapOf<String, DayRingTotals>()
    for (date in dayIsos) {
        val totals = totalsByDay[date]
        if (totals == null) {
            out[date] = emptyRingTotals(date)
            continue
        }
        val goal = activeGoalFor(goals, date)
        val progress = RingMacroKey.entries.associateWith { key ->
            val ringTotals = when (key) {
                RingMacroKey.KCAL -> totals.kcal
                RingMacroKey.CARBS -> totals.carbs
                RingMacroKey.FAT -> totals.fat
                RingMacroKey.PROTEIN -> totals.protein
            }
            val target = targetOf(goal, key)
            if (target != null && target > 0f) (ringTotals / target).coerceAtMost(1f) else 0f
        }
        out[date] = DayRingTotals(
            date = date,
            totals = totals,
            hasData = (countByDay[date] ?: 0) > 0,
            progress = progress,
        )
    }
    return out
}

/** Repas non vide (≥ 1 entry) d'un jour passé, candidat à la duplication. */
data class PastMeal(val meal: Meal, val entryCount: Int, val totals: MacroTotals)

/**
 * Repas non vides des jours antérieurs au jour affiché (plus récents d'abord,
 * max 30). Pur — alimente la sheet « Dupliquer un repas passé ».
 */
fun pastMeals(meals: List<Meal>, entries: List<MealEntry>, beforeDay: String): List<PastMeal> {
    val byMeal: Map<String, List<MealEntry>> = entries.groupBy { it.mealUUID }
    return meals
        .filter { it.date < beforeDay && (byMeal[it.uuid]?.isNotEmpty() == true) }
        .sortedWith(compareByDescending<Meal> { it.date }.thenBy { it.orderIndex })
        .take(30)
        .map { meal ->
            val mealEntries = byMeal[meal.uuid] ?: emptyList()
            PastMeal(meal, mealEntries.size, sumTotals(mealEntries))
        }
}
