package com.example.sportapp.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601

/**
 * Phase 0 (2026-05-12) : remplace RoutineTaskCheck. Rename `date` -> `occurrence_date`.
 *
 * Une coche par jour par tache (UniqueConstraint user_id + task_uuid + occurrence_date).
 * Idempotent : re-PUT du meme triplet ecrase la row existante.
 */
@Entity(
    tableName = "task_checks",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["user_id", "task_uuid", "occurrence_date"], unique = true),
        Index(value = ["user_id"]),
        Index(value = ["task_uuid"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Task::class,
            parentColumns = ["uuid"],
            childColumns = ["task_uuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TaskCheck(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "user_id") val userId: Int,

    @ColumnInfo(name = "task_uuid") val taskUUID: String,

    @ColumnInfo(name = "occurrence_date") val occurrenceDate: String,   // YYYY-MM-DD

    @ColumnInfo(name = "is_checked", defaultValue = "0") val isChecked: Boolean = false,

    @ColumnInfo(name = "checked_at") val checkedAt: String? = null,     // ISO8601 datetime

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
