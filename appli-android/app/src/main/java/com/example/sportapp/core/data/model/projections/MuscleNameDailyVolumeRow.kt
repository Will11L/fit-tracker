package com.example.sportapp.core.data.model.projections

import androidx.room.ColumnInfo

/**
 * B3-2 Stats overview : volume agrege par muscle (par nom) et par JOUR sur le
 * range. Utilise quand le range est court (<= 14 jours) — le ViewModel choisit
 * daily vs weekly selon la duree du range pour eviter d'avoir 1 seul point sur
 * '1 week' (Vico ne peut pas tracer une ligne avec 1 point).
 */
data class MuscleNameDailyVolumeRow(
    @ColumnInfo(name = "muscleName") val muscleName: String,
    @ColumnInfo(name = "dayIso") val dayIso: String,
    @ColumnInfo(name = "volume") val volume: Float,
)
