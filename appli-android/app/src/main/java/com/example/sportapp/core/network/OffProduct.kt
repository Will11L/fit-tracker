package com.example.sportapp.core.network

/**
 * Produit Open Food Facts normalisé per-100 g par le proxy serveur
 * (`GET /nutrition/off/...`, docs/NUTRITION_DESIGN.md §4.1) — miroir Android de
 * `appli-web/.../core/models/off-product.model.ts`. Mêmes noms de champs que
 * [com.example.sportapp.core.data.model.Food] pour copie directe vers le
 * catalogue à l'import (source=OFF, sourceRef=barcode). Non persisté localement.
 *
 * Wire camelCase (Gson IDENTITY) ; le serveur renvoie déjà l'alias camelCase
 * (politique 17).
 */
data class OffProduct(
    val sourceRef: String,                       // barcode
    val name: String,
    val brand: String? = null,
    val kcalPer100g: Float,
    val proteinPer100g: Float,
    val carbsPer100g: Float,
    val fatPer100g: Float,
    val fiberPer100g: Float? = null,
    val sugarPer100g: Float? = null,
    val satFatPer100g: Float? = null,
    val saltPer100g: Float? = null,
    val ironPer100g: Float? = null,
    val calciumPer100g: Float? = null,
    val magnesiumPer100g: Float? = null,
    val zincPer100g: Float? = null,
    val potassiumPer100g: Float? = null,
    val sodiumPer100g: Float? = null,
    val vitaminCPer100g: Float? = null,
    val vitaminDPer100g: Float? = null,
    val vitaminB12Per100g: Float? = null,
    val vitaminAPer100g: Float? = null,
    val servingSize: String? = null,
    val servingQuantityG: Float? = null,
    val categoriesTags: List<String> = emptyList(),
)
