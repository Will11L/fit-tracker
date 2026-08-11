package com.example.sportapp.core.data.model.projections

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.example.sportapp.core.data.model.ActualWorkoutExercise

data class ActualWorkoutExerciseWithWorkoutDateAndSets(
    @Embedded val actualWorkoutExercise: ActualWorkoutExercise,
    @ColumnInfo(name = "workoutDate") val workoutDate: String,
    @ColumnInfo(name = "setsCount") val setsCount: Int,
    @ColumnInfo(name = "totalReps") val totalReps: Int,
    @ColumnInfo(name = "doneSetsCount") val doneSetsCount: Int,
    @ColumnInfo(name = "doneReps") val doneReps: Int,
)
