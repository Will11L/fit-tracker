package com.example.sportapp.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601

/**
 * Santé / Health Connect V1 (2026-06-17) — portage Android du modèle serveur
 * `health_step_counts` (cf. serveur/app/models/health_step_count.py). Type A
 * user-scoped.
 *
 * Compteur de pas en buckets intraday : chaque row = une tranche de la journée
 * (bucket horaire). Le total quotidien se dérive par SUM(steps) sur la date.
 * L'`uuid` est stable par user+date+bucketStart (cf. HealthImporter) : le bucket
 * courant est ré-upsert au fil de la journée sans créer de doublon.
 */
@Entity(
    tableName = "health_step_counts",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["user_id"]),
        Index(value = ["date"]),
    ]
)
data class HealthStepCount(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "user_id") val userId: Int,

    val date: String,                                       // "YYYY-MM-DD"
    @ColumnInfo(name = "bucket_start") val bucketStart: String,  // "HH:MM" début de tranche
    val steps: Int,

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
