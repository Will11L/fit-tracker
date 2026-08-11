package com.example.sportapp.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601

/**
 * Nutrition A1 (2026-06-17) — portage Android du modèle serveur `food_portions`
 * (cf. serveur/app/models/food_portion.py). Portions nommées d'un aliment
 * (« 1 œuf = 60 g »). Ownership indirect : FoodPortion -> Food -> User.
 *
 * Pas de `user_id` (le serveur ne l'expose pas, ownership via le Food parent).
 * FK Room vers Food (CASCADE, miroir du serveur).
 */
@Entity(
    tableName = "food_portions",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["food_uuid"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Food::class,
            parentColumns = ["uuid"],
            childColumns = ["food_uuid"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FoodPortion(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "food_uuid") val foodUUID: String,

    val label: String,                                  // « 1 œuf », « 1 cuillère à soupe »
    val grams: Float,

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
