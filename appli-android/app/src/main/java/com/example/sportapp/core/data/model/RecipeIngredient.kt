package com.example.sportapp.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601

/**
 * Nutrition A1 (2026-06-17) — portage Android du modèle serveur
 * `recipe_ingredients` (cf. serveur/app/models/recipe_ingredient.py). Ingrédient
 * d'une recette. Ownership indirect : RecipeIngredient -> Recipe -> User
 * (pas de `user_id`).
 *
 * `food_uuid` = référence VIVANTE (pas de snapshot : une recette est un modèle,
 * pas de l'historique) -> CASCADE si le Food est supprimé. FK Room CASCADE vers
 * Recipe et Food, miroir du serveur.
 */
@Entity(
    tableName = "recipe_ingredients",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["recipe_uuid"]),
        Index(value = ["food_uuid"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["uuid"],
            childColumns = ["recipe_uuid"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Food::class,
            parentColumns = ["uuid"],
            childColumns = ["food_uuid"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RecipeIngredient(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "recipe_uuid") val recipeUUID: String,
    @ColumnInfo(name = "food_uuid") val foodUUID: String,

    @ColumnInfo(name = "quantity_g") val quantityG: Float,
    @ColumnInfo(name = "order_index") val orderIndex: Int,

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
