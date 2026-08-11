package com.example.sportapp.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601

/**
 * Nutrition A1 (2026-06-17) — portage Android du modèle serveur `meal_presets`
 * (cf. serveur/app/models/meal_preset.py). Périodes habituelles du journal
 * (« Petit-déj », « Déjeuner »...), gérées par l'utilisateur. Type A user-scoped.
 *
 * `order_index` = ordre des sections du journal. `default_time` = "HH:MM" indicatif.
 */
@Entity(
    tableName = "meal_presets",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["user_id"]),
    ]
)
data class MealPreset(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "user_id") val userId: Int,

    val name: String,                                          // user-typed, non traduit
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    @ColumnInfo(name = "default_time") val defaultTime: String? = null,   // "HH:MM"

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
