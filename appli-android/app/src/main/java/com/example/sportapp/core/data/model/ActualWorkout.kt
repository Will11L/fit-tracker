package com.example.sportapp.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import com.google.gson.annotations.SerializedName
import java.util.UUID

@Entity(
    tableName = "actual_workouts",
    indices = [
        androidx.room.Index(value = ["uuid"], unique = true),
        androidx.room.Index(value = ["date"]),
    ]
)
data class ActualWorkout(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "name") val name: String,
    val date: String,   // date au format ISO8601
    val notes: String? = null,
    val location: String? = null,
    @ColumnInfo(name = "is_done")val isDone: Boolean = false,

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
