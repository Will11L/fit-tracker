package com.example.sportapp.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601

@Entity(
    tableName = "exercises",
    indices = [androidx.room.Index(value = ["uuid"], unique = true)]
)
data class Exercise(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "user_id") val userId: Int,
    val name: String,

    val description: String? = null,
    @ColumnInfo(name = "instructions") val instructions: List<String>? = null,

    @ColumnInfo(name = "recommended_sets") val recommendedSets: Int? = null,
    @ColumnInfo(name = "recommended_reps") val recommendedReps: String? = null,
    @ColumnInfo(name = "duration_in_seconds") val durationInSeconds: Int? = null,
    @ColumnInfo(name = "rest_time_seconds") val restTimeSeconds: Int? = null,
    @ColumnInfo(name = "gif_url") val gifUrl: String? = null,
    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(name = "last_done") val lastDone: String? = null,

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
