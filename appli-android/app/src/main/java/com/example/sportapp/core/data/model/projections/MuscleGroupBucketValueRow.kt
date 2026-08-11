package com.example.sportapp.core.data.model.projections

import androidx.room.ColumnInfo

/**
 * Projection pour les queries Stats agregees au niveau `muscle_group` (le
 * niveau intermediaire de la hierarchie 3-niveaux : zone > muscle_group >
 * muscle precis). Refactor 2026-05-08, cf. CLAUDE.md historique.
 *
 *  - muscleGroup : 'Pecs', 'Lats', 'Triceps', etc. (17 valeurs au total dans
 *                  le starter pack)
 *  - bucket      : 'YYYY-MM-DD' (DAILY) ou 'YYYY-WW' (WEEKLY)
 *  - value       : volume kg pondere / nombre de sets ponderes / exercices distincts
 */
data class MuscleGroupBucketValueRow(
    @ColumnInfo(name = "muscleGroup") val muscleGroup: String,
    @ColumnInfo(name = "bucket") val bucket: String,
    @ColumnInfo(name = "value") val value: Float,
)
