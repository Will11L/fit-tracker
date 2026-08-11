package com.example.sportapp.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import java.util.UUID

@Entity(
    tableName = "planned_workout_exercises",
    indices = [androidx.room.Index(value = ["uuid"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = PlannedWorkout::class,
            parentColumns = ["uuid"],
            childColumns = ["planned_workout_uuid"],
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
data class PlannedWorkoutExercise(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "planned_workout_uuid") val plannedWorkoutUUID: String,
    @ColumnInfo(name = "exercise_uuid") val exerciseUUID: String,
    val sets: Int,
    val reps: String,
    val phase: String,   // "WARMUP", "TRAINING", "POST_TRAINING"
    val status: String,  // "PLANNED", "DONE", "NOT_STARTED", "SKIPPED"
    @ColumnInfo(name = "order") val order: Int = 0,
    val ignored: Boolean = false,

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
