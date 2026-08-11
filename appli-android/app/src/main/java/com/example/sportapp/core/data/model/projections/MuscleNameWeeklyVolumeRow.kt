package com.example.sportapp.core.data.model.projections

import androidx.room.ColumnInfo

/**
 * B3-2 Stats overview : volume hebdo agrege par muscle (par nom) sur le range.
 * Le ViewModel applique ensuite [com.example.sportapp.core.data.MuscleGroups] pour
 * regrouper en zones (Chest / Back / Shoulders / Arms / Legs / Core).
 */
data class MuscleNameWeeklyVolumeRow(
    @ColumnInfo(name = "muscleName") val muscleName: String,
    @ColumnInfo(name = "weekIso") val weekIso: String,
    @ColumnInfo(name = "volume") val volume: Float,
)
