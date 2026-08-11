package com.example.sportapp.feature.nutrition.domain

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.example.sportapp.R

/**
 * Taxonomie d'affichage des groupes d'aliments (colonne `foods.food_group`, UPPER_CASE,
 * politique 11) — port Android du sous-ensemble UI de
 * `appli-web/.../nutrition/food-category.ts` : label FR + couleur mnémotechnique du badge.
 * 18 groupes curatés ; COMPLEMENT_MACRO et COMPLEMENT_MICRO partagent le label « Compléments »
 * et la même teinte. Le mapping OFF (categories_tags → groupe) et la dérivation du règne vivent
 * côté serveur / web — non requis ici. Pur (pas de runtime Compose).
 *
 * Couleurs = port 1:1 des tokens `--food-grp-*` (dark) du web (`_colors.scss`), jamais de M3 brut.
 * Le badge portant aussi le texte, des clusters de teintes proches sont assumés (cf. note web).
 */

// Teintes par groupe (valeurs dark theme du web).
private val grpViandeRouge = Color(0xFFC0392B)
private val grpViandeBlanche = Color(0xFFE0A08C)
private val grpPoisson = Color(0xFF2E86C1)
private val grpFruitsDeMer = Color(0xFFFF8A65)
private val grpOeuf = Color(0xFFF4C430)
private val grpLaitage = Color(0xFFCDBB93)
private val grpLegumineuse = Color(0xFF8A9A2E)
private val grpLegume = Color(0xFF2ECC71)
private val grpFruit = Color(0xFFE0408A)
private val grpCerealeFeculent = Color(0xFFCBA24C)
private val grpNoixGraine = Color(0xFF966A47)
private val grpMatiereGrasse = Color(0xFFE6C84F)
private val grpProduitSucre = Color(0xFFE879C7)
private val grpBoisson = Color(0xFF3BC9DB)
private val grpPlatCompose = Color(0xFF7B8794)
private val grpComplement = Color(0xFF8A40EF) // = brightPurple (miroir --food-grp-complement web)
private val grpAutre = Color(0xFF95A5A6)

/** Couleur (badge) du groupe — fallback AUTRE si code inconnu / null. */
fun foodGroupColor(foodGroup: String?): Color = when (foodGroup?.uppercase()) {
    "VIANDE_ROUGE" -> grpViandeRouge
    "VIANDE_BLANCHE" -> grpViandeBlanche
    "POISSON" -> grpPoisson
    "FRUITS_DE_MER" -> grpFruitsDeMer
    "OEUF" -> grpOeuf
    "LAITAGE" -> grpLaitage
    "LEGUMINEUSE" -> grpLegumineuse
    "LEGUME" -> grpLegume
    "FRUIT" -> grpFruit
    "CEREALE_FECULENT" -> grpCerealeFeculent
    "NOIX_GRAINE" -> grpNoixGraine
    "MATIERE_GRASSE" -> grpMatiereGrasse
    "PRODUIT_SUCRE" -> grpProduitSucre
    "BOISSON" -> grpBoisson
    "PLAT_COMPOSE" -> grpPlatCompose
    "COMPLEMENT_MACRO", "COMPLEMENT_MICRO" -> grpComplement
    else -> grpAutre
}

/** Resource du label FR du groupe — fallback AUTRE si code inconnu / null. */
@StringRes
fun foodGroupLabelRes(foodGroup: String?): Int = when (foodGroup?.uppercase()) {
    "VIANDE_ROUGE" -> R.string.nutrition_food_group_viande_rouge
    "VIANDE_BLANCHE" -> R.string.nutrition_food_group_viande_blanche
    "POISSON" -> R.string.nutrition_food_group_poisson
    "FRUITS_DE_MER" -> R.string.nutrition_food_group_fruits_de_mer
    "OEUF" -> R.string.nutrition_food_group_oeuf
    "LAITAGE" -> R.string.nutrition_food_group_laitage
    "LEGUMINEUSE" -> R.string.nutrition_food_group_legumineuse
    "LEGUME" -> R.string.nutrition_food_group_legume
    "FRUIT" -> R.string.nutrition_food_group_fruit
    "CEREALE_FECULENT" -> R.string.nutrition_food_group_cereale_feculent
    "NOIX_GRAINE" -> R.string.nutrition_food_group_noix_graine
    "MATIERE_GRASSE" -> R.string.nutrition_food_group_matiere_grasse
    "PRODUIT_SUCRE" -> R.string.nutrition_food_group_produit_sucre
    "BOISSON" -> R.string.nutrition_food_group_boisson
    "PLAT_COMPOSE" -> R.string.nutrition_food_group_plat_compose
    "COMPLEMENT_MACRO", "COMPLEMENT_MICRO" -> R.string.nutrition_food_group_complement
    else -> R.string.nutrition_food_group_autre
}
