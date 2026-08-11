package com.example.sportapp.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601

/**
 * Nutrition A1 (2026-06-17) — portage Android du modèle serveur `recipes`
 * (cf. serveur/app/models/recipe.py). Plats composés ET repas enregistrés
 * (D7 : une seule entité couvre les deux). Type A user-scoped.
 *
 * `kind` UPPER_CASE (politique 11) :
 *   - RECIPE     : plat (macros au prorata du poids consommé via total_weight_g)
 *   - SAVED_MEAL : repas enregistré (insertion des ingrédients tels quels)
 */
@Entity(
    tableName = "recipes",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["user_id"]),
    ]
)
data class Recipe(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "user_id") val userId: Int,

    val name: String,                                                  // user-typed, non traduit
    val kind: String,                                                  // RECIPE | SAVED_MEAL
    @ColumnInfo(name = "total_weight_g") val totalWeightG: Float? = null,   // kind=RECIPE seulement

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
