package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.ActualWorkoutSet
import com.example.sportapp.core.data.model.projections.ExerciseAllTimeStatsRow
import com.example.sportapp.core.data.model.projections.ExerciseDailyStatsRow
import com.example.sportapp.core.data.model.projections.ExerciseNameBucketValueRow
import com.example.sportapp.core.data.model.projections.MuscleGroupBucketValueRow
import com.example.sportapp.core.data.model.projections.MuscleNameBucketValueRow
import com.example.sportapp.core.data.model.projections.MuscleNameDailyVolumeRow
import com.example.sportapp.core.data.model.projections.MuscleNameWeeklyVolumeRow
import com.example.sportapp.core.data.model.projections.MuscleWeeklyVolumeRow
import com.example.sportapp.core.data.model.projections.ZoneBucketValueRow
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface ActualWorkoutSetDao {

    @Query("SELECT * FROM actual_workout_sets")
    fun observeAll(): Flow<List<ActualWorkoutSet>>

    @RawQuery(observedEntities = [ActualWorkoutSet::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, ActualWorkoutSet>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<ActualWorkoutSet>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM actual_workout_sets WHERE actual_workout_exercise_uuid = :exerciseUUID ORDER BY set_order")
    fun observeByExerciseUUID(exerciseUUID: String): Flow<List<ActualWorkoutSet>>

    @Query("SELECT * FROM actual_workout_sets WHERE uuid = :uuid LIMIT 1")
    fun observeByUUID(uuid: String): Flow<ActualWorkoutSet?>

    @Query("SELECT * FROM actual_workout_sets WHERE actual_workout_exercise_uuid IN (:exerciseUUIDs)")
    fun observeSetsForExercises(exerciseUUIDs: List<String>): Flow<List<ActualWorkoutSet>>

    @Query("""
        SELECT aws.* FROM actual_workout_sets aws
        INNER JOIN actual_workout_exercises awe ON aws.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw ON awe.actual_workout_uuid = aw.uuid
        WHERE aw.date BETWEEN :start AND :end
    """)
    fun observeActualWorkoutSetsForWeek(start: String, end: String): Flow<List<ActualWorkoutSet>>

    @Query("SELECT * FROM actual_workout_sets WHERE uuid = :uuid")
    suspend fun getActualWorkoutSetByUUID(uuid: String): ActualWorkoutSet?

    @Query("SELECT * FROM actual_workout_sets WHERE actual_workout_exercise_uuid = :exerciseUUID")
    suspend fun getSetsByExerciseUUID(exerciseUUID: String): List<ActualWorkoutSet>

    @Query("SELECT * FROM actual_workout_sets WHERE actual_workout_exercise_uuid IN (:actualWorkoutExerciseUUIDs)")
    fun getActualWorkoutSetsByExerciseUUIDs(actualWorkoutExerciseUUIDs: List<String>): Flow<List<ActualWorkoutSet>>

    @Query("SELECT * FROM actual_workout_sets WHERE actual_workout_exercise_uuid = :uuid")
    suspend fun getByActualWorkoutExerciseUUID(uuid: String): List<ActualWorkoutSet>

    @Query("SELECT * FROM actual_workout_sets WHERE uuid = :uuid")
    suspend fun getByUUID(uuid: String): ActualWorkoutSet?

    @Query("""
        SELECT COUNT(*)
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe
            ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw
            ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN exercise_muscles em
            ON awe.exercise_uuid = em.exercise_uuid
        WHERE em.muscle_uuid = :muscleUUID
          AND substr(aw.date, 1, 10) BETWEEN :startOfWeek AND :endOfWeek
          AND s.status = 'DONE'
    """)
    suspend fun getDoneSetsForMuscleInWeek(
        muscleUUID: String,
        startOfWeek: String,
        endOfWeek: String
    ): Int

    // --- B3-2 Stats : queries agregees (Flow reactif, perf SQL via indexes v14)

    @Query("""
        SELECT
          substr(aw.date, 1, 10) AS dayIso,
          MAX(s.weight) AS maxWeight,
          COUNT(s.uuid) AS setCount,
          IFNULL(SUM(s.weight * s.reps), 0) AS volume
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe
            ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw
            ON awe.actual_workout_uuid = aw.uuid
        WHERE awe.exercise_uuid = :exerciseUUID
          AND substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND s.pendingDeletion = 0
          AND awe.pendingDeletion = 0
          AND aw.pendingDeletion = 0
        GROUP BY substr(aw.date, 1, 10)
        ORDER BY dayIso ASC
    """)
    fun observeExerciseDailyStats(
        exerciseUUID: String,
        startDate: String,
        endDate: String
    ): Flow<List<ExerciseDailyStatsRow>>

    @Query("""
        SELECT
          strftime('%Y-%W', substr(aw.date, 1, 10)) AS weekIso,
          IFNULL(SUM(s.weight * s.reps), 0) AS volume,
          COUNT(s.uuid) AS setCount
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe
            ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw
            ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN exercise_muscles em
            ON awe.exercise_uuid = em.exercise_uuid
        WHERE em.muscle_uuid = :muscleUUID
          AND substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND s.pendingDeletion = 0
          AND awe.pendingDeletion = 0
          AND aw.pendingDeletion = 0
        GROUP BY weekIso
        ORDER BY weekIso ASC
    """)
    fun observeMuscleWeeklyVolume(
        muscleUUID: String,
        startDate: String,
        endDate: String
    ): Flow<List<MuscleWeeklyVolumeRow>>

    @Query("""
        SELECT
          IFNULL(MAX(s.weight), 0) AS maxWeight,
          COUNT(s.uuid) AS totalSets,
          IFNULL(SUM(s.weight * s.reps), 0) AS totalVolume
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe
            ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw
            ON awe.actual_workout_uuid = aw.uuid
        WHERE awe.exercise_uuid = :exerciseUUID
          AND s.pendingDeletion = 0
          AND awe.pendingDeletion = 0
          AND aw.pendingDeletion = 0
    """)
    fun observeExerciseAllTimeStats(exerciseUUID: String): Flow<ExerciseAllTimeStatsRow?>

    /**
     * B3-2 Stats overview : volume hebdo par muscle (par nom) sur le range.
     * Pondere par exercise_muscles.coefficient (un set qui cible 100% le pec
     * compte +reps*weight ; cible 50% les triceps compte +0.5*reps*weight).
     * Les muscles sans aucun set sur la periode ne renvoient pas de row.
     */
    @Query("""
        SELECT
          m.name AS muscleName,
          strftime('%Y-%W', substr(aw.date, 1, 10)) AS weekIso,
          IFNULL(SUM(s.weight * s.reps * em.coefficient), 0) AS volume
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe
            ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw
            ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN exercise_muscles em
            ON awe.exercise_uuid = em.exercise_uuid
        INNER JOIN muscles m
            ON em.muscle_uuid = m.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND s.pendingDeletion = 0
          AND awe.pendingDeletion = 0
          AND aw.pendingDeletion = 0
          AND em.pendingDeletion = 0
          AND m.pendingDeletion = 0
          AND s.status = 'DONE'
        GROUP BY m.name, weekIso
        ORDER BY weekIso ASC
    """)
    fun observeAllMusclesWeeklyVolume(
        startDate: String,
        endDate: String,
    ): Flow<List<MuscleNameWeeklyVolumeRow>>

    /**
     * Variante DAILY : volume agrege par jour (au lieu de semaine). Utilise
     * pour les ranges courts (<= 14 jours) ou la granularite hebdo donnerait
     * 1-2 points seulement. Meme JOIN structure que weekly, group sur dayIso.
     */
    @Query("""
        SELECT
          m.name AS muscleName,
          substr(aw.date, 1, 10) AS dayIso,
          IFNULL(SUM(s.weight * s.reps * em.coefficient), 0) AS volume
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe
            ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw
            ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN exercise_muscles em
            ON awe.exercise_uuid = em.exercise_uuid
        INNER JOIN muscles m
            ON em.muscle_uuid = m.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND s.pendingDeletion = 0
          AND awe.pendingDeletion = 0
          AND aw.pendingDeletion = 0
          AND em.pendingDeletion = 0
          AND m.pendingDeletion = 0
          AND s.status = 'DONE'
        GROUP BY m.name, dayIso
        ORDER BY dayIso ASC
    """)
    fun observeAllMusclesDailyVolume(
        startDate: String,
        endDate: String,
    ): Flow<List<MuscleNameDailyVolumeRow>>

    // ─── B3-2 metriques alternatives : SETS et EXERCISES par zone ───
    // Memes JOIN que volume, juste l'agregat change (COUNT au lieu de SUM).
    // Projection commune MuscleNameBucketValueRow.

    /** Sets count par muscle, par semaine (pondere par exercise_muscles.coefficient). */
    @Query("""
        SELECT
          m.name AS muscleName,
          strftime('%Y-%W', substr(aw.date, 1, 10)) AS bucket,
          IFNULL(SUM(em.coefficient), 0) AS value
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe
            ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw
            ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN exercise_muscles em
            ON awe.exercise_uuid = em.exercise_uuid
        INNER JOIN muscles m
            ON em.muscle_uuid = m.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND s.pendingDeletion = 0
          AND awe.pendingDeletion = 0
          AND aw.pendingDeletion = 0
          AND em.pendingDeletion = 0
          AND m.pendingDeletion = 0
          AND s.status = 'DONE'
        GROUP BY m.name, bucket
        ORDER BY bucket ASC
    """)
    fun observeAllMusclesWeeklySetCount(
        startDate: String,
        endDate: String,
    ): Flow<List<MuscleNameBucketValueRow>>

    /** Sets count par muscle, par jour. */
    @Query("""
        SELECT
          m.name AS muscleName,
          substr(aw.date, 1, 10) AS bucket,
          IFNULL(SUM(em.coefficient), 0) AS value
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe
            ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw
            ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN exercise_muscles em
            ON awe.exercise_uuid = em.exercise_uuid
        INNER JOIN muscles m
            ON em.muscle_uuid = m.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND s.pendingDeletion = 0
          AND awe.pendingDeletion = 0
          AND aw.pendingDeletion = 0
          AND em.pendingDeletion = 0
          AND m.pendingDeletion = 0
          AND s.status = 'DONE'
        GROUP BY m.name, bucket
        ORDER BY bucket ASC
    """)
    fun observeAllMusclesDailySetCount(
        startDate: String,
        endDate: String,
    ): Flow<List<MuscleNameBucketValueRow>>

    /** Nombre d'exercices distincts par muscle (avec >=1 set DONE), par semaine. */
    @Query("""
        SELECT
          m.name AS muscleName,
          strftime('%Y-%W', substr(aw.date, 1, 10)) AS bucket,
          CAST(COUNT(DISTINCT awe.exercise_uuid) AS REAL) AS value
        FROM actual_workout_exercises awe
        INNER JOIN actual_workouts aw
            ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN actual_workout_sets s
            ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN exercise_muscles em
            ON awe.exercise_uuid = em.exercise_uuid
        INNER JOIN muscles m
            ON em.muscle_uuid = m.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND awe.pendingDeletion = 0
          AND aw.pendingDeletion = 0
          AND em.pendingDeletion = 0
          AND m.pendingDeletion = 0
          AND s.pendingDeletion = 0
          AND s.status = 'DONE'
        GROUP BY m.name, bucket
        ORDER BY bucket ASC
    """)
    fun observeAllMusclesWeeklyExerciseCount(
        startDate: String,
        endDate: String,
    ): Flow<List<MuscleNameBucketValueRow>>

    /** Nombre d'exercices distincts par muscle (avec >=1 set DONE), par jour. */
    @Query("""
        SELECT
          m.name AS muscleName,
          substr(aw.date, 1, 10) AS bucket,
          CAST(COUNT(DISTINCT awe.exercise_uuid) AS REAL) AS value
        FROM actual_workout_exercises awe
        INNER JOIN actual_workouts aw
            ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN actual_workout_sets s
            ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN exercise_muscles em
            ON awe.exercise_uuid = em.exercise_uuid
        INNER JOIN muscles m
            ON em.muscle_uuid = m.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND awe.pendingDeletion = 0
          AND aw.pendingDeletion = 0
          AND em.pendingDeletion = 0
          AND m.pendingDeletion = 0
          AND s.pendingDeletion = 0
          AND s.status = 'DONE'
        GROUP BY m.name, bucket
        ORDER BY bucket ASC
    """)
    fun observeAllMusclesDailyExerciseCount(
        startDate: String,
        endDate: String,
    ): Flow<List<MuscleNameBucketValueRow>>

    // ─── Refactor 3-level (2026-05-08) : 6 queries au niveau muscle_group ───
    // Memes JOIN que Muscle queries mais GROUP BY COALESCE(m.muscle_group, 'Other').
    // Le COALESCE protege contre les rows pre-refactor avec muscle_group=NULL.

    /** Volume kg par muscle_group, par semaine (pondere par exercise_muscles.coefficient). */
    @Query("""
        SELECT
          COALESCE(m.muscle_group, 'Other') AS muscleGroup,
          strftime('%Y-%W', substr(aw.date, 1, 10)) AS bucket,
          IFNULL(SUM(s.weight * s.reps * em.coefficient), 0) AS value
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN exercise_muscles em ON awe.exercise_uuid = em.exercise_uuid
        INNER JOIN muscles m ON em.muscle_uuid = m.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND s.pendingDeletion = 0 AND awe.pendingDeletion = 0 AND aw.pendingDeletion = 0
          AND em.pendingDeletion = 0 AND m.pendingDeletion = 0 AND s.status = 'DONE'
        GROUP BY COALESCE(m.muscle_group, 'Other'), bucket
        ORDER BY bucket ASC
    """)
    fun observeAllGroupsWeeklyVolume(startDate: String, endDate: String): Flow<List<MuscleGroupBucketValueRow>>

    /** Volume kg par muscle_group, par jour. */
    @Query("""
        SELECT
          COALESCE(m.muscle_group, 'Other') AS muscleGroup,
          substr(aw.date, 1, 10) AS bucket,
          IFNULL(SUM(s.weight * s.reps * em.coefficient), 0) AS value
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN exercise_muscles em ON awe.exercise_uuid = em.exercise_uuid
        INNER JOIN muscles m ON em.muscle_uuid = m.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND s.pendingDeletion = 0 AND awe.pendingDeletion = 0 AND aw.pendingDeletion = 0
          AND em.pendingDeletion = 0 AND m.pendingDeletion = 0 AND s.status = 'DONE'
        GROUP BY COALESCE(m.muscle_group, 'Other'), bucket
        ORDER BY bucket ASC
    """)
    fun observeAllGroupsDailyVolume(startDate: String, endDate: String): Flow<List<MuscleGroupBucketValueRow>>

    /** Sets count par muscle_group, par semaine (pondere par coefficient). */
    @Query("""
        SELECT
          COALESCE(m.muscle_group, 'Other') AS muscleGroup,
          strftime('%Y-%W', substr(aw.date, 1, 10)) AS bucket,
          IFNULL(SUM(em.coefficient), 0) AS value
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN exercise_muscles em ON awe.exercise_uuid = em.exercise_uuid
        INNER JOIN muscles m ON em.muscle_uuid = m.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND s.pendingDeletion = 0 AND awe.pendingDeletion = 0 AND aw.pendingDeletion = 0
          AND em.pendingDeletion = 0 AND m.pendingDeletion = 0 AND s.status = 'DONE'
        GROUP BY COALESCE(m.muscle_group, 'Other'), bucket
        ORDER BY bucket ASC
    """)
    fun observeAllGroupsWeeklySetCount(startDate: String, endDate: String): Flow<List<MuscleGroupBucketValueRow>>

    /** Sets count par muscle_group, par jour. */
    @Query("""
        SELECT
          COALESCE(m.muscle_group, 'Other') AS muscleGroup,
          substr(aw.date, 1, 10) AS bucket,
          IFNULL(SUM(em.coefficient), 0) AS value
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN exercise_muscles em ON awe.exercise_uuid = em.exercise_uuid
        INNER JOIN muscles m ON em.muscle_uuid = m.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND s.pendingDeletion = 0 AND awe.pendingDeletion = 0 AND aw.pendingDeletion = 0
          AND em.pendingDeletion = 0 AND m.pendingDeletion = 0 AND s.status = 'DONE'
        GROUP BY COALESCE(m.muscle_group, 'Other'), bucket
        ORDER BY bucket ASC
    """)
    fun observeAllGroupsDailySetCount(startDate: String, endDate: String): Flow<List<MuscleGroupBucketValueRow>>

    /** Nombre d'exercices distincts par muscle_group (avec >=1 set DONE), par semaine. */
    @Query("""
        SELECT
          COALESCE(m.muscle_group, 'Other') AS muscleGroup,
          strftime('%Y-%W', substr(aw.date, 1, 10)) AS bucket,
          CAST(COUNT(DISTINCT awe.exercise_uuid) AS REAL) AS value
        FROM actual_workout_exercises awe
        INNER JOIN actual_workouts aw ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN actual_workout_sets s ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN exercise_muscles em ON awe.exercise_uuid = em.exercise_uuid
        INNER JOIN muscles m ON em.muscle_uuid = m.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND awe.pendingDeletion = 0 AND aw.pendingDeletion = 0
          AND em.pendingDeletion = 0 AND m.pendingDeletion = 0
          AND s.pendingDeletion = 0 AND s.status = 'DONE'
        GROUP BY COALESCE(m.muscle_group, 'Other'), bucket
        ORDER BY bucket ASC
    """)
    fun observeAllGroupsWeeklyExerciseCount(startDate: String, endDate: String): Flow<List<MuscleGroupBucketValueRow>>

    /** Nombre d'exercices distincts par muscle_group (avec >=1 set DONE), par jour. */
    @Query("""
        SELECT
          COALESCE(m.muscle_group, 'Other') AS muscleGroup,
          substr(aw.date, 1, 10) AS bucket,
          CAST(COUNT(DISTINCT awe.exercise_uuid) AS REAL) AS value
        FROM actual_workout_exercises awe
        INNER JOIN actual_workouts aw ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN actual_workout_sets s ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN exercise_muscles em ON awe.exercise_uuid = em.exercise_uuid
        INNER JOIN muscles m ON em.muscle_uuid = m.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND awe.pendingDeletion = 0 AND aw.pendingDeletion = 0
          AND em.pendingDeletion = 0 AND m.pendingDeletion = 0
          AND s.pendingDeletion = 0 AND s.status = 'DONE'
        GROUP BY COALESCE(m.muscle_group, 'Other'), bucket
        ORDER BY bucket ASC
    """)
    fun observeAllGroupsDailyExerciseCount(startDate: String, endDate: String): Flow<List<MuscleGroupBucketValueRow>>

    // ─── Refactor 3-level (2026-05-08) : 6 queries au niveau zone ───
    // Lit la colonne `muscles.zone` directement (pas de mapping client).

    /** Volume kg par zone, par semaine. */
    @Query("""
        SELECT
          COALESCE(m.zone, 'Other') AS zoneName,
          strftime('%Y-%W', substr(aw.date, 1, 10)) AS bucket,
          IFNULL(SUM(s.weight * s.reps * em.coefficient), 0) AS value
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN exercise_muscles em ON awe.exercise_uuid = em.exercise_uuid
        INNER JOIN muscles m ON em.muscle_uuid = m.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND s.pendingDeletion = 0 AND awe.pendingDeletion = 0 AND aw.pendingDeletion = 0
          AND em.pendingDeletion = 0 AND m.pendingDeletion = 0 AND s.status = 'DONE'
        GROUP BY COALESCE(m.zone, 'Other'), bucket
        ORDER BY bucket ASC
    """)
    fun observeAllZonesWeeklyVolume(startDate: String, endDate: String): Flow<List<ZoneBucketValueRow>>

    /** Volume kg par zone, par jour. */
    @Query("""
        SELECT
          COALESCE(m.zone, 'Other') AS zoneName,
          substr(aw.date, 1, 10) AS bucket,
          IFNULL(SUM(s.weight * s.reps * em.coefficient), 0) AS value
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN exercise_muscles em ON awe.exercise_uuid = em.exercise_uuid
        INNER JOIN muscles m ON em.muscle_uuid = m.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND s.pendingDeletion = 0 AND awe.pendingDeletion = 0 AND aw.pendingDeletion = 0
          AND em.pendingDeletion = 0 AND m.pendingDeletion = 0 AND s.status = 'DONE'
        GROUP BY COALESCE(m.zone, 'Other'), bucket
        ORDER BY bucket ASC
    """)
    fun observeAllZonesDailyVolume(startDate: String, endDate: String): Flow<List<ZoneBucketValueRow>>

    /** Sets count par zone, par semaine (pondere par coefficient). */
    @Query("""
        SELECT
          COALESCE(m.zone, 'Other') AS zoneName,
          strftime('%Y-%W', substr(aw.date, 1, 10)) AS bucket,
          IFNULL(SUM(em.coefficient), 0) AS value
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN exercise_muscles em ON awe.exercise_uuid = em.exercise_uuid
        INNER JOIN muscles m ON em.muscle_uuid = m.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND s.pendingDeletion = 0 AND awe.pendingDeletion = 0 AND aw.pendingDeletion = 0
          AND em.pendingDeletion = 0 AND m.pendingDeletion = 0 AND s.status = 'DONE'
        GROUP BY COALESCE(m.zone, 'Other'), bucket
        ORDER BY bucket ASC
    """)
    fun observeAllZonesWeeklySetCount(startDate: String, endDate: String): Flow<List<ZoneBucketValueRow>>

    /** Sets count par zone, par jour. */
    @Query("""
        SELECT
          COALESCE(m.zone, 'Other') AS zoneName,
          substr(aw.date, 1, 10) AS bucket,
          IFNULL(SUM(em.coefficient), 0) AS value
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN exercise_muscles em ON awe.exercise_uuid = em.exercise_uuid
        INNER JOIN muscles m ON em.muscle_uuid = m.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND s.pendingDeletion = 0 AND awe.pendingDeletion = 0 AND aw.pendingDeletion = 0
          AND em.pendingDeletion = 0 AND m.pendingDeletion = 0 AND s.status = 'DONE'
        GROUP BY COALESCE(m.zone, 'Other'), bucket
        ORDER BY bucket ASC
    """)
    fun observeAllZonesDailySetCount(startDate: String, endDate: String): Flow<List<ZoneBucketValueRow>>

    /** Nombre d'exercices distincts par zone (avec >=1 set DONE), par semaine. */
    @Query("""
        SELECT
          COALESCE(m.zone, 'Other') AS zoneName,
          strftime('%Y-%W', substr(aw.date, 1, 10)) AS bucket,
          CAST(COUNT(DISTINCT awe.exercise_uuid) AS REAL) AS value
        FROM actual_workout_exercises awe
        INNER JOIN actual_workouts aw ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN actual_workout_sets s ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN exercise_muscles em ON awe.exercise_uuid = em.exercise_uuid
        INNER JOIN muscles m ON em.muscle_uuid = m.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND awe.pendingDeletion = 0 AND aw.pendingDeletion = 0
          AND em.pendingDeletion = 0 AND m.pendingDeletion = 0
          AND s.pendingDeletion = 0 AND s.status = 'DONE'
        GROUP BY COALESCE(m.zone, 'Other'), bucket
        ORDER BY bucket ASC
    """)
    fun observeAllZonesWeeklyExerciseCount(startDate: String, endDate: String): Flow<List<ZoneBucketValueRow>>

    /** Nombre d'exercices distincts par zone (avec >=1 set DONE), par jour. */
    @Query("""
        SELECT
          COALESCE(m.zone, 'Other') AS zoneName,
          substr(aw.date, 1, 10) AS bucket,
          CAST(COUNT(DISTINCT awe.exercise_uuid) AS REAL) AS value
        FROM actual_workout_exercises awe
        INNER JOIN actual_workouts aw ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN actual_workout_sets s ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN exercise_muscles em ON awe.exercise_uuid = em.exercise_uuid
        INNER JOIN muscles m ON em.muscle_uuid = m.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND awe.pendingDeletion = 0 AND aw.pendingDeletion = 0
          AND em.pendingDeletion = 0 AND m.pendingDeletion = 0
          AND s.pendingDeletion = 0 AND s.status = 'DONE'
        GROUP BY COALESCE(m.zone, 'Other'), bucket
        ORDER BY bucket ASC
    """)
    fun observeAllZonesDailyExerciseCount(startDate: String, endDate: String): Flow<List<ZoneBucketValueRow>>

    // ─── B3-2 section 'X / Exercise' : 6 queries (3 metriques x 2 granularites) ───
    // Memes JOIN que Muscle queries mais GROUP BY e.name au lieu de m.name.
    // Pas de pendingDeletion sur exercise_muscles ni muscles ici (pas de JOIN
    // sur muscles, on agrege au niveau exercise).

    /** Volume kg par exercise, par semaine (sets * weight, sans coefficient muscle). */
    @Query("""
        SELECT
          e.name AS exerciseName,
          strftime('%Y-%W', substr(aw.date, 1, 10)) AS bucket,
          IFNULL(SUM(s.weight * s.reps), 0) AS value
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe
            ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw
            ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN exercises e
            ON awe.exercise_uuid = e.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND s.pendingDeletion = 0
          AND awe.pendingDeletion = 0
          AND aw.pendingDeletion = 0
          AND e.pendingDeletion = 0
          AND s.status = 'DONE'
        GROUP BY e.name, bucket
        ORDER BY bucket ASC
    """)
    fun observeAllExercisesWeeklyVolume(
        startDate: String,
        endDate: String,
    ): Flow<List<ExerciseNameBucketValueRow>>

    /** Volume kg par exercise, par jour. */
    @Query("""
        SELECT
          e.name AS exerciseName,
          substr(aw.date, 1, 10) AS bucket,
          IFNULL(SUM(s.weight * s.reps), 0) AS value
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe
            ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw
            ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN exercises e
            ON awe.exercise_uuid = e.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND s.pendingDeletion = 0
          AND awe.pendingDeletion = 0
          AND aw.pendingDeletion = 0
          AND e.pendingDeletion = 0
          AND s.status = 'DONE'
        GROUP BY e.name, bucket
        ORDER BY bucket ASC
    """)
    fun observeAllExercisesDailyVolume(
        startDate: String,
        endDate: String,
    ): Flow<List<ExerciseNameBucketValueRow>>

    /** Sets count par exercise, par semaine. */
    @Query("""
        SELECT
          e.name AS exerciseName,
          strftime('%Y-%W', substr(aw.date, 1, 10)) AS bucket,
          CAST(COUNT(s.uuid) AS REAL) AS value
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe
            ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw
            ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN exercises e
            ON awe.exercise_uuid = e.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND s.pendingDeletion = 0
          AND awe.pendingDeletion = 0
          AND aw.pendingDeletion = 0
          AND e.pendingDeletion = 0
          AND s.status = 'DONE'
        GROUP BY e.name, bucket
        ORDER BY bucket ASC
    """)
    fun observeAllExercisesWeeklySetCount(
        startDate: String,
        endDate: String,
    ): Flow<List<ExerciseNameBucketValueRow>>

    /** Sets count par exercise, par jour. */
    @Query("""
        SELECT
          e.name AS exerciseName,
          substr(aw.date, 1, 10) AS bucket,
          CAST(COUNT(s.uuid) AS REAL) AS value
        FROM actual_workout_sets s
        INNER JOIN actual_workout_exercises awe
            ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN actual_workouts aw
            ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN exercises e
            ON awe.exercise_uuid = e.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND s.pendingDeletion = 0
          AND awe.pendingDeletion = 0
          AND aw.pendingDeletion = 0
          AND e.pendingDeletion = 0
          AND s.status = 'DONE'
        GROUP BY e.name, bucket
        ORDER BY bucket ASC
    """)
    fun observeAllExercisesDailySetCount(
        startDate: String,
        endDate: String,
    ): Flow<List<ExerciseNameBucketValueRow>>

    /**
     * Sessions count par exercise (avec >=1 set DONE), par semaine.
     * Reinterprete metric EXERCISES au niveau Exercise = sessions distinctes
     * ou l'exercise a effectivement ete fait (≥1 set DONE).
     */
    @Query("""
        SELECT
          e.name AS exerciseName,
          strftime('%Y-%W', substr(aw.date, 1, 10)) AS bucket,
          CAST(COUNT(DISTINCT aw.uuid) AS REAL) AS value
        FROM actual_workout_exercises awe
        INNER JOIN actual_workouts aw
            ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN actual_workout_sets s
            ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN exercises e
            ON awe.exercise_uuid = e.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND awe.pendingDeletion = 0
          AND aw.pendingDeletion = 0
          AND e.pendingDeletion = 0
          AND s.pendingDeletion = 0
          AND s.status = 'DONE'
        GROUP BY e.name, bucket
        ORDER BY bucket ASC
    """)
    fun observeAllExercisesWeeklySessionCount(
        startDate: String,
        endDate: String,
    ): Flow<List<ExerciseNameBucketValueRow>>

    /** Sessions count par exercise (avec >=1 set DONE), par jour. */
    @Query("""
        SELECT
          e.name AS exerciseName,
          substr(aw.date, 1, 10) AS bucket,
          CAST(COUNT(DISTINCT aw.uuid) AS REAL) AS value
        FROM actual_workout_exercises awe
        INNER JOIN actual_workouts aw
            ON awe.actual_workout_uuid = aw.uuid
        INNER JOIN actual_workout_sets s
            ON s.actual_workout_exercise_uuid = awe.uuid
        INNER JOIN exercises e
            ON awe.exercise_uuid = e.uuid
        WHERE substr(aw.date, 1, 10) BETWEEN :startDate AND :endDate
          AND awe.pendingDeletion = 0
          AND aw.pendingDeletion = 0
          AND e.pendingDeletion = 0
          AND s.pendingDeletion = 0
          AND s.status = 'DONE'
        GROUP BY e.name, bucket
        ORDER BY bucket ASC
    """)
    fun observeAllExercisesDailySessionCount(
        startDate: String,
        endDate: String,
    ): Flow<List<ExerciseNameBucketValueRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(actualWorkoutSet: ActualWorkoutSet) {
        val now = getNowISO8601()
        insertInternal(actualWorkoutSet.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(actualWorkoutSets: List<ActualWorkoutSet>) {
        val now = getNowISO8601()
        insertAllInternal(actualWorkoutSets.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun update(actualWorkoutSet: ActualWorkoutSet) {
        val now = getNowISO8601()
        updateInternal(actualWorkoutSet.copy(updatedAt = now, synced = false))
    }

    @Query("UPDATE actual_workout_sets SET reps = :newReps, synced = 0 WHERE uuid = :uuid")
    suspend fun updateReps(uuid: String, newReps: Int)

    @Query("UPDATE actual_workout_sets SET weight = :newWeight, synced = 0 WHERE uuid = :uuid")
    suspend fun updateWeight(uuid: String, newWeight: Float)

    @Query("UPDATE actual_workout_sets SET set_order = :newOrder, synced = 0 WHERE uuid = :uuid")
    suspend fun updateOrder(uuid: String, newOrder: Int)

    @Query("UPDATE actual_workout_sets SET status = :newStatus, synced = 0 WHERE uuid = :uuid")
    suspend fun updateStatus(uuid: String, newStatus: String)

    @Query("UPDATE actual_workout_sets SET notes = :newNotes, synced = 0 WHERE uuid = :uuid")
    suspend fun updateNotes(uuid: String, newNotes: String)


    @Query("DELETE FROM actual_workout_sets WHERE actual_workout_exercise_uuid = :actualWorkoutExerciseUUID")
    suspend fun deleteByActualWorkoutExerciseUUID(actualWorkoutExerciseUUID: String)

    @Delete
    suspend fun delete(actualWorkoutSet: ActualWorkoutSet)

    // 🔁 Synchronisation
    @Query("SELECT * FROM actual_workout_sets WHERE synced = 0")
    suspend fun getAllUnsynced(): List<ActualWorkoutSet>

    @Query("UPDATE actual_workout_sets SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE actual_workout_sets SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM actual_workout_sets WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE actual_workout_sets SET pendingDeletion = 1 WHERE actual_workout_exercise_uuid = :actualWorkoutExerciseUUID")
    suspend fun markSetsAsPendingDeletionWithExerciseUUID(actualWorkoutExerciseUUID: String)

    @Query("UPDATE actual_workout_sets SET pendingDeletion = 1 WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String)

    @Query("SELECT * FROM actual_workout_sets WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<ActualWorkoutSet>

    @Query("DELETE FROM actual_workout_sets")
    suspend fun clearAll()

    @Query("SELECT * FROM actual_workout_sets")
    suspend fun getAllOnce(): List<ActualWorkoutSet>

    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(actualWorkoutSet: ActualWorkoutSet)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(actualWorkoutSets: List<ActualWorkoutSet>)

    @Update
    suspend fun updateInternal(actualWorkoutSet: ActualWorkoutSet)

    // --- Insertion depuis le serveur (respecte payload tel quel) ---
    suspend fun insertFromServer(actualWorkoutSet: ActualWorkoutSet) =
        insertInternal(actualWorkoutSet.copy(synced = true, pendingDeletion = false))
    suspend fun insertAllFromServer(actualWorkoutSets: List<ActualWorkoutSet>) =
        insertAllInternal(actualWorkoutSets.map { it.copy(synced = true, pendingDeletion = false) })
    suspend fun updateFromServer(actualWorkoutSet: ActualWorkoutSet) = updateInternal(actualWorkoutSet)

}
