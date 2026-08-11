package com.example.sportapp.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601

/**
 * Santé / Health Connect V1 (2026-06-17) — portage Android du modèle serveur
 * `health_metrics` (cf. serveur/app/models/health_metric.py). Type A user-scoped.
 *
 * Métriques passives génériques, vendor-agnostiques. Une row = une mesure.
 * `type` UPPER_CASE (politique 11) : HEART_RATE / SLEEP / DISTANCE / ACTIVE_CALORIES.
 * `unit` self-describing (bpm / min / m / km / kcal...). Ancrage temporel :
 * `date` (jour, requis, indexé) + `startTime` optionnel ("HH:MM") pour l'intraday.
 */
@Entity(
    tableName = "health_metrics",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["user_id"]),
        Index(value = ["date"]),
    ]
)
data class HealthMetric(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "user_id") val userId: Int,

    val type: String,                                       // UPPER_CASE
    val value: Float,
    val unit: String,                                       // bpm | min | m | km | kcal...
    val date: String,                                       // "YYYY-MM-DD"
    @ColumnInfo(name = "start_time") val startTime: String? = null,  // "HH:MM" intraday optionnel

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
