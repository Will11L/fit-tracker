package com.example.sportapp.core.data.model.projections

import androidx.room.ColumnInfo

data class MuscleWeeklyVolumeRow(
    @ColumnInfo(name = "weekIso") val weekIso: String,
    @ColumnInfo(name = "volume") val volume: Float,
    @ColumnInfo(name = "setCount") val setCount: Int
)
