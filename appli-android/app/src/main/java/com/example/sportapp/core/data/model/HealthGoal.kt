package com.example.sportapp.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601

/**
 * Santé / Health Connect V1 (2026-06-17) — portage Android du modèle serveur
 * `health_goals` (cf. serveur/app/models/health_goal.py). Type A user-scoped.
 *
 * Objectif santé versionné dans le temps (même sémantique que NutritionGoal).
 * L'objectif actif d'un `type` un jour J = celui avec le plus grand
 * `effective_from` <= J. `type` UPPER_CASE (politique 11) : STEPS en v1, extensible.
 */
@Entity(
    tableName = "health_goals",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["user_id"]),
        Index(value = ["effective_from"]),
    ]
)
data class HealthGoal(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "user_id") val userId: Int,

    val type: String,                                       // UPPER_CASE : STEPS (v1)
    val target: Float,
    @ColumnInfo(name = "effective_from") val effectiveFrom: String,  // "YYYY-MM-DD"

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
