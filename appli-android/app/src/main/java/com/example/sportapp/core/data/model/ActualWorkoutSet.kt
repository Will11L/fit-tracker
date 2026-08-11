package com.example.sportapp.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import java.util.UUID

@Entity(
    tableName = "actual_workout_sets",
    indices = [
        androidx.room.Index(value = ["uuid"], unique = true),
        androidx.room.Index(value = ["actual_workout_exercise_uuid"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ActualWorkoutExercise::class,
            parentColumns = ["uuid"],
            childColumns = ["actual_workout_exercise_uuid"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ActualWorkoutSet(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "actual_workout_exercise_uuid") val actualWorkoutExerciseUUID: String,
    @ColumnInfo(name = "set_order") val setOrder: Int = 0,
    val reps: Int,
    val weight: Float,
    @ColumnInfo(name = "is_dropset") val isDropset: Boolean = false,
    val notes: String? = null,
    val recommendation: String? = null,
    val status: String,

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
