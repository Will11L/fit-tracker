package com.example.sportapp.core.data.model.projections

import androidx.room.ColumnInfo

data class ExerciseDailyStatsRow(
    @ColumnInfo(name = "dayIso") val dayIso: String,
    @ColumnInfo(name = "maxWeight") val maxWeight: Float,
    @ColumnInfo(name = "setCount") val setCount: Int,
    @ColumnInfo(name = "volume") val volume: Float
)
