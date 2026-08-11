package com.example.sportapp.feature.nutrition.domain

import com.example.sportapp.core.data.model.Food
import com.example.sportapp.core.data.model.MealEntry

/**
 * Logique pure du Catalogue d'aliments (A3) — recherche plein-texte + filtres
 * multi-critères par seuil (combinables) + regroupement de la liste. Miroir
 * Android de `appli-web/.../nutrition/food-catalogue.ts`. Sans Compose ni
 * Android, testable en isolation.
 *
 * Les seuils portent sur la valeur per-100 g d'un nutriment (kcal *effective*
 * D12 pour rester cohérent avec l'affichage, macros, et les 10 micros). Tous les
 * critères se combinent en ET. Les labels d'affichage vivent dans strings.xml
 * (politique 18) — ici on ne manipule que des clés et des nombres.
 */

/** Opérateur d'un seuil : « au moins » (≥) ou « au plus » (≤). */
enum class ThresholdOp { GTE, LTE }

/**
 * Nutriment filtrable par seuil (per-100 g) : kcal + 4 macros + 10 micros.
 * Ordre = ordre d'affichage dans le panneau de filtres (macros puis micros).
 * `unit` = symbole d'unité affiché à côté du champ de valeur.
 */
enum class NutrientKey(val unit: String) {
    KCAL("kcal"),
    PROTEIN("g"),
    CARBS("g"),
    FAT("g"),
    FIBER("g"),
    IRON("mg"),
    CALCIUM("mg"),
    MAGNESIUM("mg"),
    ZINC("mg"),
    POTASSIUM("mg"),
    SODIUM("mg"),
    VITAMIN_C("mg"),
    VITAMIN_D("µg"),
    VITAMIN_B12("µg"),
    VITAMIN_A("µg"),
}

/** Un filtre de seuil actif : nutriment + opérateur + valeur (per 100 g). */
data class NutrientThreshold(val key: NutrientKey, val op: ThresholdOp, val value: Float)

/**
 * Valeur per-100 g d'un nutriment, pour le filtrage. kcal = kcal *effective*
 * (dérivée selon la source, D12) pour rester cohérent avec la valeur affichée.
 * Micros/fibres absents (null) comptés comme 0 : un seuil « ≥ X » (X > 0) exclut
 * donc naturellement les aliments sans la donnée. Pur.
 */
fun foodNutrientValue(food: Food, key: NutrientKey): Float = when (key) {
    NutrientKey.KCAL -> effectiveFoodKcal(food)
    NutrientKey.PROTEIN -> food.proteinPer100g
    NutrientKey.CARBS -> food.carbsPer100g
    NutrientKey.FAT -> food.fatPer100g
    NutrientKey.FIBER -> food.fiberPer100g ?: 0f
    NutrientKey.IRON -> food.ironPer100g ?: 0f
    NutrientKey.CALCIUM -> food.calciumPer100g ?: 0f
    NutrientKey.MAGNESIUM -> food.magnesiumPer100g ?: 0f
    NutrientKey.ZINC -> food.zincPer100g ?: 0f
    NutrientKey.POTASSIUM -> food.potassiumPer100g ?: 0f
    NutrientKey.SODIUM -> food.sodiumPer100g ?: 0f
    NutrientKey.VITAMIN_C -> food.vitaminCPer100g ?: 0f
    NutrientKey.VITAMIN_D -> food.vitaminDPer100g ?: 0f
    NutrientKey.VITAMIN_B12 -> food.vitaminB12Per100g ?: 0f
    NutrientKey.VITAMIN_A -> food.vitaminAPer100g ?: 0f
}

/** Vrai si l'aliment satisfait TOUS les seuils (ET). Liste vide → vrai. Pur. */
fun passesThresholds(food: Food, thresholds: List<NutrientThreshold>): Boolean =
    thresholds.all { t ->
        val v = foodNutrientValue(food, t.key)
        if (t.op == ThresholdOp.GTE) v >= t.value else v <= t.value
    }

/** Bloc de la liste catalogue (group null = liste à plat de recherche, sans en-tête). */
enum class CatalogueGroup { RECENTS, FAVORITES, ALL, ARCHIVED }

data class FoodGroupBlock(val group: CatalogueGroup?, val foods: List<Food>)

/**
 * uuids des aliments récemment consommés (entries les plus récentes d'abord, dédupliqués,
 * limite 8 par défaut) — miroir `recentFoodUuids` web. Alimente le bloc « Récents ».
 */
fun recentFoodUuids(entries: List<MealEntry>, limit: Int = 8): List<String> {
    val seen = mutableSetOf<String>()
    val out = mutableListOf<String>()
    for (e in entries.sortedByDescending { it.updatedAt ?: "" }) {
        val id = e.foodUUID ?: continue
        if (seen.add(id)) {
            out += id
            if (out.size >= limit) break
        }
    }
    return out
}

/**
 * Construit les blocs de la liste catalogue. Recherche texte OU seuils actifs →
 * liste à plat filtrée (nom/marque ET seuils macros/micros, sans en-tête) ;
 * sinon Favoris puis Tous (aliments non archivés), et optionnellement un bloc
 * « Archivés » en fin de liste. Recherche et seuils se combinent en ET. Suppose
 * `foods` déjà trié par nom (DAO `ORDER BY name ASC`). Pur.
 */
fun buildCatalogue(
    foods: List<Food>,
    query: String,
    thresholds: List<NutrientThreshold>,
    showArchived: Boolean,
    // uuids récemment consommés (cf. [recentFoodUuids]) → bloc « Récents » en tête (miroir web).
    recentUuids: List<String> = emptyList(),
): List<FoodGroupBlock> {
    val active = foods.filter { !it.archived }
    val q = query.trim()

    // Recherche texte OU seuil → liste à plat filtrée (toutes les facettes en ET).
    if (q.isNotEmpty() || thresholds.isNotEmpty()) {
        val pool = if (showArchived) foods else active
        val filtered = pool.filter { f ->
            (q.isEmpty() ||
                f.name.contains(q, ignoreCase = true) ||
                (f.brand?.contains(q, ignoreCase = true) == true)) &&
                passesThresholds(f, thresholds)
        }
        return if (filtered.isEmpty()) emptyList() else listOf(FoodGroupBlock(null, filtered))
    }

    val favorites = active.filter { it.isFavorite }
    val favSet = favorites.map { it.uuid }.toSet()
    // Récents = consommés récemment HORS favoris (miroir buildFoodGroups web) ; « Tous » exclut les deux.
    val byUuid = active.associateBy { it.uuid }
    val recents = recentUuids.filter { it !in favSet }.mapNotNull { byUuid[it] }
    val recentSet = recents.map { it.uuid }.toSet()
    val rest = active.filter { it.uuid !in favSet && it.uuid !in recentSet }

    val blocks = mutableListOf<FoodGroupBlock>()
    if (recents.isNotEmpty()) blocks += FoodGroupBlock(CatalogueGroup.RECENTS, recents)
    if (favorites.isNotEmpty()) blocks += FoodGroupBlock(CatalogueGroup.FAVORITES, favorites)
    if (rest.isNotEmpty()) blocks += FoodGroupBlock(CatalogueGroup.ALL, rest)
    if (showArchived) {
        val archived = foods.filter { it.archived }
        if (archived.isNotEmpty()) blocks += FoodGroupBlock(CatalogueGroup.ARCHIVED, archived)
    }
    return blocks
}
