package com.example.sportapp.core.data.model.projections

import androidx.room.ColumnInfo

data class ExerciseAllTimeStatsRow(
    @ColumnInfo(name = "maxWeight") val maxWeight: Float,
    @ColumnInfo(name = "totalSets") val totalSets: Int,
    @ColumnInfo(name = "totalVolume") val totalVolume: Float
)
