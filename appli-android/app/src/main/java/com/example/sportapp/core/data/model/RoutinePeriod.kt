package com.example.sportapp.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601

@Entity(
    tableName = "routine_periods",
    indices = [androidx.room.Index(value = ["uuid"], unique = true)]
)
data class RoutinePeriod(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "user_id") val userId: Int,

    val name: String,

    @ColumnInfo(name = "start_time") val startTime: String, // "06:30"
    @ColumnInfo(name = "end_time") val endTime: String,     // "09:00"
    @ColumnInfo(name = "order_index", defaultValue = "0") val order: Int = 0,

    // Rappels notifs (2026-06-08) : minutes avant le début / la fin de la période.
    // null = rappel désactivé, 0 = pile à l'heure, N = N min avant. Indépendants.
    @ColumnInfo(name = "reminder_before_start_minutes") val reminderBeforeStartMinutes: Int? = null,
    @ColumnInfo(name = "reminder_before_end_minutes") val reminderBeforeEndMinutes: Int? = null,

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
