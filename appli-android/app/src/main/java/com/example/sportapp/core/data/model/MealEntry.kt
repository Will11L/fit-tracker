package com.example.sportapp.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601

/**
 * Nutrition A1 (2026-06-17) — portage Android du modèle serveur `meal_entries`
 * (cf. serveur/app/models/meal_entry.py). Table centrale : une consommation dans
 * un repas. Ownership indirect : MealEntry -> Meal -> User (pas de `user_id`).
 *
 * Snapshot D5 : macros per-100g + display_name FIGÉS au moment de l'ajout
 * (historique immuable). FK food/recipe en SET NULL (l'entry survit à la
 * suppression de sa source grâce au snapshot) ; FK meal en CASCADE.
 * Totaux dérivés côté client : total = per_100g x quantity_g / 100.
 */
@Entity(
    tableName = "meal_entries",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["meal_uuid"]),
        Index(value = ["food_uuid"]),
        Index(value = ["recipe_uuid"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Meal::class,
            parentColumns = ["uuid"],
            childColumns = ["meal_uuid"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Food::class,
            parentColumns = ["uuid"],
            childColumns = ["food_uuid"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["uuid"],
            childColumns = ["recipe_uuid"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class MealEntry(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "meal_uuid") val mealUUID: String,
    @ColumnInfo(name = "food_uuid") val foodUUID: String? = null,       // ref informative (SET NULL)
    @ColumnInfo(name = "recipe_uuid") val recipeUUID: String? = null,   // ref informative (SET NULL)

    @ColumnInfo(name = "display_name") val displayName: String,         // snapshot du nom
    @ColumnInfo(name = "quantity_g") val quantityG: Float,
    @ColumnInfo(name = "portion_label") val portionLabel: String? = null,

    // Snapshot macros per-100g (D5)
    @ColumnInfo(name = "kcal_per_100g") val kcalPer100g: Float,
    @ColumnInfo(name = "protein_per_100g") val proteinPer100g: Float,
    @ColumnInfo(name = "carbs_per_100g") val carbsPer100g: Float,
    @ColumnInfo(name = "fat_per_100g") val fatPer100g: Float,
    @ColumnInfo(name = "fiber_per_100g") val fiberPer100g: Float? = null,
    @ColumnInfo(name = "sugar_per_100g") val sugarPer100g: Float? = null,
    @ColumnInfo(name = "sat_fat_per_100g") val satFatPer100g: Float? = null,
    @ColumnInfo(name = "salt_per_100g") val saltPer100g: Float? = null,
    // Snapshot vitamines & minéraux (pack essentiel ~10, D11 étendu)
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

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
