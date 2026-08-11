package com.example.sportapp.core.data.model

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import java.util.UUID

@Entity(
    tableName = "cycle_workouts",
    indices = [androidx.room.Index(value = ["uuid"], unique = true)],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = PlannedWorkout::class,
            parentColumns = ["uuid"],
            childColumns = ["planned_workout_uuid"],
            onDelete = androidx.room.ForeignKey.CASCADE
        ),
        androidx.room.ForeignKey(
            entity = TrainingCycle::class,
            parentColumns = ["uuid"],
            childColumns = ["training_cycle_uuid"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
)
data class CycleWorkout(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "planned_workout_uuid") val plannedWorkoutUUID: String,
    @ColumnInfo(name = "training_cycle_uuid") val trainingCycleUUID: String,

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
