package com.example.sportapp.core.data.model.projections

import androidx.room.ColumnInfo

data class ExerciseMuscleSimple(
    @ColumnInfo(name = "exercise_uuid") val exerciseUUID: String,
    @ColumnInfo(name = "muscle_uuid") val muscleUUID: String
)
