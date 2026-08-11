package com.example.sportapp.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Relation
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import com.google.gson.annotations.SerializedName
import java.util.UUID

@Entity(
    tableName = "actual_workout_exercises",
    indices = [
        androidx.room.Index(value = ["uuid"], unique = true),
        androidx.room.Index(value = ["actual_workout_uuid"]),
        androidx.room.Index(value = ["exercise_uuid"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ActualWorkout::class,
            parentColumns = ["uuid"],
            childColumns = ["actual_workout_uuid"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["uuid"],
            childColumns = ["exercise_uuid"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ActualWorkoutExercise(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "actual_workout_uuid") val actualWorkoutUUID: String,
    @ColumnInfo(name = "exercise_uuid") val exerciseUUID: String,
    @ColumnInfo(defaultValue = "0") val sets: Int = 0,
    @ColumnInfo(defaultValue = "0-1") val reps: String,
    val phase: String,
    val status: String,
    val order: Int,
    @ColumnInfo(name = "added_manually") val addedManually: Boolean = false,

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)