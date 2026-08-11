package com.example.sportapp.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import java.util.UUID

@Entity(
    tableName = "muscle_goals",
    indices = [androidx.room.Index(value = ["uuid"], unique = true)],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = Muscle::class,
            parentColumns = ["uuid"],
            childColumns = ["muscle_uuid"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
)
data class MuscleGoal(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "muscle_uuid") val muscleUUID: String,
    val priority: String,
    val done: Int,
    val target: String,
    @ColumnInfo(name = "week_iso") val weekISO: String,
    val status: String = "IN_PROGRESS", // Can be "DONE", "IN_PROGRESS", "NOT_STARTED", "SKIPPED"
    @ColumnInfo(name = "added_manually") val addedManually: Boolean = false,

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
