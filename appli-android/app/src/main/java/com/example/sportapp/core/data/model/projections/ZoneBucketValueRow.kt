package com.example.sportapp.core.data.model.projections

import androidx.room.ColumnInfo

/**
 * Projection pour les queries Stats agregees au niveau `zone` (le niveau haut
 * de la hierarchie 3-niveaux : zone > muscle_group > muscle precis).
 * Refactor 2026-05-08, cf. CLAUDE.md historique.
 *
 *  - zoneName : 'Chest', 'Back', 'Shoulders', 'Arms', 'Legs', 'Core' (6 zones)
 *  - bucket   : 'YYYY-MM-DD' (DAILY) ou 'YYYY-WW' (WEEKLY)
 *  - value    : volume kg pondere / nombre de sets ponderes / exercices distincts
 *
 * Avant 2026-05-08, l'agregation au niveau zone se faisait cote Kotlin via le
 * mapping client `MuscleGroups.groupOf(muscleName)`. Depuis le refactor, la
 * colonne `muscles.zone` est lue directement via JOIN SQL (pas de mapping cote
 * client).
 */
data class ZoneBucketValueRow(
    @ColumnInfo(name = "zoneName") val zoneName: String,
    @ColumnInfo(name = "bucket") val bucket: String,
    @ColumnInfo(name = "value") val value: Float,
)
