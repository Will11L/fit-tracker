package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.ActualWorkoutExercise
import com.example.sportapp.core.data.model.projections.ActualWorkoutExerciseWithWorkoutDateAndSets
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface ActualWorkoutExerciseDao {

    // --- RAW methods (usage interne uniquement)
    @Insert(onConflict = OnConflictStrategy.REPLACE)        // ATTENTION : replace = DELETE + INSERT = active les Delete CASCADE
    suspend fun insertInternal(exercise: ActualWorkoutExercise)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(exercises: List<ActualWorkoutExercise>)

    @Update
    suspend fun updateInternal(exercise: ActualWorkoutExercise)

    @Query("UPDATE actual_workout_exercises SET pendingDeletion = 1, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun markAsPendingDeletionInternal(uuid: String, updatedAt: String)

    // --- Public methods (ajout automatique de updatedAt + synced = false)
    suspend fun insert(exercise: ActualWorkoutExercise) {
        insertInternal(exercise.copy(
            updatedAt = getNowISO8601(),
            synced = false
        ))
    }

    suspend fun insertAll(exercises: List<ActualWorkoutExercise>) {
        val now = getNowISO8601()
        insertAllInternal(exercises.map {
            it.copy(updatedAt = now, synced = false)
        })
    }

    suspend fun update(exercise: ActualWorkoutExercise) {
        updateInternal(exercise.copy(
            updatedAt = getNowISO8601(),
            synced = false
        ))
    }

    suspend fun markAsPendingDeletion(uuid: String) {
        markAsPendingDeletionInternal(uuid, getNowISO8601())
    }

    // --- Lecture
    @Query("SELECT * FROM actual_workout_exercises WHERE actual_workout_uuid = :actualWorkoutUUID ORDER BY `order` ASC")
    suspend fun getExercisesForWorkoutOnce(actualWorkoutUUID: String): List<ActualWorkoutExercise>

    @Query("SELECT * FROM actual_workout_exercises WHERE actual_workout_uuid IN (:actualWorkoutUUIDs)")
    suspend fun getExercisesForWorkouts(actualWorkoutUUIDs: List<String>): List<ActualWorkoutExercise>

    @Query("SELECT * FROM actual_workout_exercises WHERE uuid = :uuid")
    suspend fun getActualWorkoutExerciseByUUID(uuid: String): ActualWorkoutExercise?

    @Query("SELECT * FROM actual_workout_exercises WHERE actual_workout_uuid = :actualWorkoutUUID AND exercise_uuid = :exerciseUUID")
    suspend fun getExerciseByWorkoutAndExerciseUUID(actualWorkoutUUID: String, exerciseUUID: String): ActualWorkoutExercise?

    @Query("SELECT * FROM actual_workout_exercises WHERE actual_workout_uuid = :actualWorkoutUUID AND phase = :phase ORDER BY `order` ASC")
    suspend fun getExercisesByPhase(actualWorkoutUUID: String, phase: String): List<ActualWorkoutExercise>

    @Query("""
        SELECT awe.*,
               aw.date AS workoutDate,
               COUNT(aws.uuid) AS setsCount,
               IFNULL(SUM(aws.reps), 0) AS totalReps,
               IFNULL(SUM(CASE WHEN aws.status = 'DONE' THEN 1 ELSE 0 END), 0) AS doneSetsCount,
               IFNULL(SUM(CASE WHEN aws.status = 'DONE' THEN aws.reps ELSE 0 END), 0) AS doneReps
        FROM actual_workout_exercises AS awe
        INNER JOIN actual_workouts AS aw 
            ON awe.actual_workout_uuid = aw.uuid
        LEFT JOIN actual_workout_sets AS aws
            ON aws.actual_workout_exercise_uuid = awe.uuid
        WHERE awe.exercise_uuid = :exerciseUUID
        GROUP BY awe.uuid
        ORDER BY aw.date DESC
        LIMIT 3
    """)
    suspend fun getLast3SessionsForExercise(exerciseUUID: String): List<ActualWorkoutExerciseWithWorkoutDateAndSets>

    @Query("SELECT * FROM actual_workout_exercises")
    fun observeAll(): Flow<List<ActualWorkoutExercise>>

    @RawQuery(observedEntities = [ActualWorkoutExercise::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, ActualWorkoutExercise>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<ActualWorkoutExercise>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM actual_workout_exercises WHERE uuid = :uuid LIMIT 1")
    fun observeByUUID(uuid: String): Flow<ActualWorkoutExercise?>

    @Query("SELECT * FROM actual_workout_exercises WHERE actual_workout_uuid = :uuid")
    fun observeByWorkout(uuid: String): Flow<List<ActualWorkoutExercise>>

    @Query("""
        SELECT awe.* FROM actual_workout_exercises awe
        INNER JOIN actual_workouts aw ON awe.actual_workout_uuid = aw.uuid
        WHERE aw.date BETWEEN :start AND :end
    """)
    fun observeActualWorkoutExercisesForWeek(start: String, end: String): Flow<List<ActualWorkoutExercise>>

    @Query("""
        SELECT awe.*,
               aw.date AS workoutDate,
               COUNT(aws.uuid) AS setsCount,
               IFNULL(SUM(aws.reps), 0) AS totalReps,
               IFNULL(SUM(CASE WHEN aws.status = 'DONE' THEN 1 ELSE 0 END), 0) AS doneSetsCount,
               IFNULL(SUM(CASE WHEN aws.status = 'DONE' THEN aws.reps ELSE 0 END), 0) AS doneReps
        FROM actual_workout_exercises AS awe
        INNER JOIN actual_workouts AS aw 
            ON awe.actual_workout_uuid = aw.uuid
        LEFT JOIN actual_workout_sets AS aws
            ON aws.actual_workout_exercise_uuid = awe.uuid
        WHERE awe.exercise_uuid = :exerciseUUID
        GROUP BY awe.uuid
        ORDER BY aw.date DESC
        LIMIT 3
    """)
    fun observeLast3SessionsForExercise(exerciseUUID: String): Flow<List<ActualWorkoutExerciseWithWorkoutDateAndSets>>

    // --- Synchronisation (technique)
    @Query("SELECT * FROM actual_workout_exercises WHERE synced = 0")
    suspend fun getAllUnsynced(): List<ActualWorkoutExercise>

    @Query("UPDATE actual_workout_exercises SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE actual_workout_exercises SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("UPDATE actual_workout_exercises SET synced = :isSynced WHERE uuid = :uuid")
    suspend fun setSyncedStatus(uuid: String, isSynced: Boolean)

    @Query("SELECT EXISTS(SELECT 1 FROM actual_workout_exercises WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("SELECT * FROM actual_workout_exercises WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<ActualWorkoutExercise>

    @Delete
    suspend fun delete(exercise: ActualWorkoutExercise)

    @Query("DELETE FROM actual_workout_exercises")
    suspend fun clearAll()

    @Query("SELECT * FROM actual_workout_exercises")
    suspend fun getAll(): List<ActualWorkoutExercise>

    @Query("SELECT * FROM actual_workout_exercises")
    suspend fun getAllOnce(): List<ActualWorkoutExercise>

    // --- Insertion depuis le serveur (respecte payload tel quel) ---
    suspend fun insertFromServer(exercise: ActualWorkoutExercise) {
        insertInternal(exercise.copy(synced = true, pendingDeletion = false))
    }

    suspend fun insertAllFromServer(exercises: List<ActualWorkoutExercise>) {
        insertAllInternal(exercises.map { it.copy(synced = true, pendingDeletion = false) })
    }

    suspend fun updateFromServer(exercise: ActualWorkoutExercise) {
        updateInternal(exercise)
    }
}
