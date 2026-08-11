package com.example.sportapp.feature.nutrition.domain

import com.example.sportapp.core.data.model.Food
import com.example.sportapp.core.data.model.MealEntry
import kotlin.math.roundToInt

/**
 * Vitamines & minéraux (pack essentiel 10, D11 étendu) — logique pure, testable
 * sans Compose ni Android. Miroir Android de `appli-web/.../nutrition/micros.ts`.
 *
 * v1 : affichage seulement (pas de cibles éditables). Cibles = VNR UE
 * (règlement 1169/2011) pour 9 micros ; Sodium = repère PLAFOND (2000 mg, repère
 * OMS). Valeurs snapshot per-100 g sur la MealEntry, mises à l'échelle de la
 * quantité consommée (× quantityG / 100). Les labels d'affichage vivent dans
 * `strings.xml` (politique 18) et sont résolus côté composable via la clé enum.
 */

/** Famille d'un micronutriment (teinte d'affichage par famille, pas par %VNR). */
enum class MicroFamily { MINERAL, VITAMIN }

/** Unité d'affichage d'un micronutriment. */
enum class MicroUnit(val symbol: String) { MG("mg"), UG("µg") }

/**
 * Les 10 micros suivis. UPPER_CASE (politique 11). Chaque clé porte sa cible
 * (VNR ou plafond), son unité, sa famille, et le flag `isLimit` (Sodium = repère
 * plafond, pas objectif). Ordre = minéraux puis vitamines (= ordre d'affichage).
 */
enum class MicroKey(
    val family: MicroFamily,
    val unit: MicroUnit,
    /** VNR (objectif à atteindre) ou plafond (Sodium). */
    val target: Float,
    /** true => repère plafond (Sodium) : alerte si dépassé, pas un objectif. */
    val isLimit: Boolean,
) {
    IRON(MicroFamily.MINERAL, MicroUnit.MG, 14f, false),
    CALCIUM(MicroFamily.MINERAL, MicroUnit.MG, 800f, false),
    MAGNESIUM(MicroFamily.MINERAL, MicroUnit.MG, 375f, false),
    ZINC(MicroFamily.MINERAL, MicroUnit.MG, 10f, false),
    POTASSIUM(MicroFamily.MINERAL, MicroUnit.MG, 2000f, false),
    SODIUM(MicroFamily.MINERAL, MicroUnit.MG, 2000f, true),
    VITAMIN_C(MicroFamily.VITAMIN, MicroUnit.MG, 80f, false),
    VITAMIN_D(MicroFamily.VITAMIN, MicroUnit.UG, 5f, false),
    VITAMIN_B12(MicroFamily.VITAMIN, MicroUnit.UG, 2.5f, false),
    VITAMIN_A(MicroFamily.VITAMIN, MicroUnit.UG, 800f, false),
}

/** Valeur per-100 g d'un micro sur une entry (null si absente de la source). */
fun MealEntry.microPer100g(key: MicroKey): Float? = when (key) {
    MicroKey.IRON -> ironPer100g
    MicroKey.CALCIUM -> calciumPer100g
    MicroKey.MAGNESIUM -> magnesiumPer100g
    MicroKey.ZINC -> zincPer100g
    MicroKey.POTASSIUM -> potassiumPer100g
    MicroKey.SODIUM -> sodiumPer100g
    MicroKey.VITAMIN_C -> vitaminCPer100g
    MicroKey.VITAMIN_D -> vitaminDPer100g
    MicroKey.VITAMIN_B12 -> vitaminB12Per100g
    MicroKey.VITAMIN_A -> vitaminAPer100g
}

/** Valeur per-100 g d'un micro sur un aliment du catalogue (null si absente de la source). */
fun Food.microPer100g(key: MicroKey): Float? = when (key) {
    MicroKey.IRON -> ironPer100g
    MicroKey.CALCIUM -> calciumPer100g
    MicroKey.MAGNESIUM -> magnesiumPer100g
    MicroKey.ZINC -> zincPer100g
    MicroKey.POTASSIUM -> potassiumPer100g
    MicroKey.SODIUM -> sodiumPer100g
    MicroKey.VITAMIN_C -> vitaminCPer100g
    MicroKey.VITAMIN_D -> vitaminDPer100g
    MicroKey.VITAMIN_B12 -> vitaminB12Per100g
    MicroKey.VITAMIN_A -> vitaminAPer100g
}

/**
 * Cumul jour des 10 micros depuis les snapshots per-100 g des entries
 * (total = per100g × quantityG / 100, null traité comme 0). Pur.
 */
fun sumMicroTotals(entries: List<MealEntry>): Map<MicroKey, Float> {
    val acc = MicroKey.entries.associateWith { 0f }.toMutableMap()
    for (e in entries) {
        val factor = e.quantityG / 100f
        for (k in MicroKey.entries) {
            acc[k] = (acc[k] ?: 0f) + (e.microPer100g(k) ?: 0f) * factor
        }
    }
    return acc
}

/** Ligne d'affichage d'un micro vs sa cible (barre), dérivée des cumuls du jour. */
data class MicroRow(
    val key: MicroKey,
    val value: Float,
    /** Avancement borné 0..1 (pour la barre). */
    val progress: Float,
    /** Sodium seulement : cumul strictement au-dessus du plafond (couleur d'alerte). */
    val exceeded: Boolean,
)

/** Construit les 10 lignes micros (cumul vs VNR / plafond) — pur, testable. */
fun microRows(totals: Map<MicroKey, Float>): List<MicroRow> =
    MicroKey.entries.map { key ->
        val value = totals[key] ?: 0f
        val ratio = if (key.target > 0f) value / key.target else 0f
        MicroRow(
            key = key,
            value = value,
            progress = ratio.coerceIn(0f, 1f),
            exceeded = key.isLimit && value > key.target,
        )
    }

/**
 * Abréviation compacte d'un micro pour les lignes denses (symbole chimique pour les minéraux,
 * « Vit X » pour les vitamines). Symboles universels → non traduits (comme `short` côté web).
 */
val MicroKey.short: String
    get() = when (this) {
        MicroKey.IRON -> "Fe"
        MicroKey.CALCIUM -> "Ca"
        MicroKey.MAGNESIUM -> "Mg"
        MicroKey.ZINC -> "Zn"
        MicroKey.POTASSIUM -> "K"
        MicroKey.SODIUM -> "Na"
        MicroKey.VITAMIN_C -> "Vit C"
        MicroKey.VITAMIN_D -> "Vit D"
        MicroKey.VITAMIN_B12 -> "Vit B12"
        MicroKey.VITAMIN_A -> "Vit A"
    }

/** Un micro présent (valeur non nulle) prêt à afficher en ligne compacte, coloré par famille. */
data class MicroLineItem(
    val short: String,
    /** Valeur per-100 g arrondie à 1 décimale. */
    val value: Float,
    val unit: String,
    val family: MicroFamily,
)

/**
 * Micros CONSOMMÉS (mis à l'échelle de la quantité) présents d'une entry du journal,
 * en abréviations compactes — pour le dépli micros d'une `MacroEntryRow` (miroir du
 * `entryRow` web). Arrondi à 1 décimale. Pur.
 */
fun MealEntry.consumedMicroLineItems(): List<MicroLineItem> =
    MicroKey.entries.mapNotNull { key ->
        val per100 = microPer100g(key) ?: return@mapNotNull null
        val consumed = per100 * quantityG / 100f
        if (consumed <= 0f) return@mapNotNull null
        MicroLineItem(
            short = key.short,
            value = (consumed * 10f).roundToInt() / 10f,
            unit = key.unit.symbol,
            family = key.family,
        )
    }

/**
 * Micros présents (valeur non nulle) d'un aliment du catalogue, en abréviations compactes, pour la
 * ligne micros d'une row — miroir de `microLineItems` (web). Per-100 g arrondi à 1 décimale. Pur.
 */
fun Food.microLineItems(): List<MicroLineItem> =
    MicroKey.entries.mapNotNull { key ->
        val v = microPer100g(key) ?: return@mapNotNull null
        MicroLineItem(
            short = key.short,
            value = (v * 10f).roundToInt() / 10f,
            unit = key.unit.symbol,
            family = key.family,
        )
    }
