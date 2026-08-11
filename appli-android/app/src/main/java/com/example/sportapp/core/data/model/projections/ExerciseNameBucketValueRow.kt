package com.example.sportapp.core.data.model.projections

import androidx.room.ColumnInfo

/**
 * Projection generique pour les queries 'X / Exercise' multi-metriques :
 *  - exerciseName : nom de l'exercise (Bench Press, Squat, etc.)
 *  - bucket : 'YYYY-MM-DD' (DAILY) ou 'YYYY-WW' (WEEKLY) selon la query
 *  - value  : volume kg / nombre de sets / nombre de sessions distinctes
 *
 * Couvre les 6 queries (2 granularites x 3 metriques). Pour metric EXERCISES
 * au niveau Exercise, value = COUNT(DISTINCT aw.uuid) (= sessions ou
 * l'exercise est utilise) car COUNT(DISTINCT exercise_uuid) = 1 trivialement.
 */
data class ExerciseNameBucketValueRow(
    @ColumnInfo(name = "exerciseName") val exerciseName: String,
    @ColumnInfo(name = "bucket") val bucket: String,
    @ColumnInfo(name = "value") val value: Float,
)
