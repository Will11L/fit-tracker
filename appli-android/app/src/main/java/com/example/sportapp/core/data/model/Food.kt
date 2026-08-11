package com.example.sportapp.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601

/**
 * Nutrition A1 (2026-06-17) — portage Android du modèle serveur `foods`
 * (cf. serveur/app/models/food.py). Catalogue d'aliments user-scoped (Type A).
 *
 * `source` UPPER_CASE (politique 11) : CUSTOM | CIQUAL | OFF.
 * Macros + micros (vitamines/minéraux) per 100 g ; les micros sont nullables
 * (dispo partiellement selon la source). Noms wire camelCase (Gson IDENTITY),
 * colonnes Room snake_case (politique 17).
 */
@Entity(
    tableName = "foods",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["user_id"]),
    ]
)
data class Food(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "user_id") val userId: Int,

    val name: String,
    val brand: String? = null,
    val source: String,                                              // CUSTOM | CIQUAL | OFF
    @ColumnInfo(name = "source_ref") val sourceRef: String? = null,
    @ColumnInfo(name = "food_group") val foodGroup: String? = null,  // groupe curate UPPER_CASE, nullable

    @ColumnInfo(name = "kcal_per_100g") val kcalPer100g: Float,
    @ColumnInfo(name = "protein_per_100g") val proteinPer100g: Float,
    @ColumnInfo(name = "carbs_per_100g") val carbsPer100g: Float,
    @ColumnInfo(name = "fat_per_100g") val fatPer100g: Float,
    @ColumnInfo(name = "fiber_per_100g") val fiberPer100g: Float? = null,
    @ColumnInfo(name = "sugar_per_100g") val sugarPer100g: Float? = null,
    @ColumnInfo(name = "sat_fat_per_100g") val satFatPer100g: Float? = null,
    @ColumnInfo(name = "salt_per_100g") val saltPer100g: Float? = null,
    // Vitamines & minéraux (pack essentiel ~10, D11 étendu)
    @ColumnInfo(name = "iron_per_100g") val ironPer100g: Float? = null,
    @ColumnInfo(name = "calcium_per_100g") val calciumPer100g: Float? = null,
    @ColumnInfo(name = "magnesium_per_100g") val magnesiumPer100g: Float? = null,
    @ColumnInfo(name = "zinc_per_100g") val zincPer100g: Float? = null,
    @ColumnInfo(name = "potassium_per_100g") val potassiumPer100g: Float? = null,
    @ColumnInfo(name = "sodium_per_100g") val sodiumPer100g: Float? = null,
    @ColumnInfo(name = "vitamin_c_per_100g") val vitaminCPer100g: Float? = null,
    @ColumnInfo(name = "vitamin_d_per_100g") val vitaminDPer100g: Float? = null,
    @ColumnInfo(name = "vitamin_b12_per_100g") val vitaminB12Per100g: Float? = null,
    @ColumnInfo(name = "vitamin_a_per_100g") val vitaminAPer100g: Float? = null,

    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    // Hydratation (2026-07-05) : boisson eau → auto-comptage hydratation (1 g = 1 ml).
    @ColumnInfo(name = "is_water", defaultValue = "0") val isWater: Boolean = false,

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
