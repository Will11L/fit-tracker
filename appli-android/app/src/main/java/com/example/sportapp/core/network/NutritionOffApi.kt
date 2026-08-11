package com.example.sportapp.core.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Proxy serveur Open Food Facts (Nutrition V2, docs/NUTRITION_DESIGN.md §4.1).
 * Read-only : recherche texte + lookup par code-barres, normalisés per-100 g
 * vers le format [OffProduct] (copié dans le catalogue local à l'import).
 * Auth JWT obligatoire (politique 8) — passe par le client authentifié.
 */
interface NutritionOffApi {

    @GET("nutrition/off/search")
    suspend fun search(
        @Query("q") q: String,
        @Query("pageSize") pageSize: Int = 20,
    ): List<OffProduct>

    @GET("nutrition/off/product/{barcode}")
    suspend fun product(@Path("barcode") barcode: String): OffProduct
}
