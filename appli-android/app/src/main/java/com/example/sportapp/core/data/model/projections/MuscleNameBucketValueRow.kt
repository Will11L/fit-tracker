package com.example.sportapp.core.data.model.projections

import androidx.room.ColumnInfo

/**
 * Projection generique pour les queries 'Volume by muscle group' multi-metriques :
 *  - bucket : 'YYYY-MM-DD' (DAILY) ou 'YYYY-WW' (WEEKLY) selon la query
 *  - value  : volume kg / nombre de sets / nombre d'exercices distincts
 *
 * Une seule projection couvre les 6 queries (2 granularites x 3 metriques).
 * Les queries existantes Volume Weekly/Daily Row gardent leur propre projection
 * pour ne pas casser les appels existants.
 */
data class MuscleNameBucketValueRow(
    @ColumnInfo(name = "muscleName") val muscleName: String,
    @ColumnInfo(name = "bucket") val bucket: String,
    @ColumnInfo(name = "value") val value: Float,
)
