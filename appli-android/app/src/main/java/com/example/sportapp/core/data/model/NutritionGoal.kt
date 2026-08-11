package com.example.sportapp.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601

/**
 * Nutrition A1 (2026-06-17) — portage Android du modèle serveur `nutrition_goals`
 * (cf. serveur/app/models/nutrition_goal.py). Cibles quotidiennes kcal + macros.
 * Type A user-scoped.
 *
 * La cible active un jour J = celle avec le plus grand `effective_from` <= J.
 * `day_kind` UPPER_CASE (politique 11) : ALL en v1.
 */
@Entity(
    tableName = "nutrition_goals",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["user_id"]),
        Index(value = ["effective_from"]),
    ]
)
data class NutritionGoal(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "user_id") val userId: Int,

    @ColumnInfo(name = "effective_from") val effectiveFrom: String,   // "YYYY-MM-DD"
    @ColumnInfo(name = "day_kind") val dayKind: String = "ALL",       // ALL (v1)

    val kcal: Float,
    @ColumnInfo(name = "protein_g") val proteinG: Float,
    @ColumnInfo(name = "carbs_g") val carbsG: Float,
    @ColumnInfo(name = "fat_g") val fatG: Float,

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
