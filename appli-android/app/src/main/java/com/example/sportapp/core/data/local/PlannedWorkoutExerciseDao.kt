package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.PlannedWorkoutExercise
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedWorkoutExerciseDao {

    @Query("SELECT * FROM planned_workout_exercises")
    fun observeAll(): Flow<List<PlannedWorkoutExercise>>

    @RawQuery(observedEntities = [PlannedWorkoutExercise::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, PlannedWorkoutExercise>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<PlannedWorkoutExercise>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM planned_workout_exercises WHERE planned_workout_uuid = :plannedWorkoutUUID")
    fun observeByPlannedWorkoutUUID(plannedWorkoutUUID: String): Flow<List<PlannedWorkoutExercise>>

    @Query("SELECT * FROM planned_workout_exercises WHERE planned_workout_uuid IN (:plannedWorkoutUUIDs)")
    fun observeForPlannedWorkouts(plannedWorkoutUUIDs: List<String>): Flow<List<PlannedWorkoutExercise>>

    @Query("SELECT * FROM planned_workout_exercises WHERE uuid = :uuid")
    suspend fun getPlannedWorkoutExerciseByUUID(uuid: String): PlannedWorkoutExercise?

    @Query("SELECT * FROM planned_workout_exercises WHERE planned_workout_uuid = :plannedWorkoutUUID")
    suspend fun getPlannedWorkoutExercisesByPlannedWorkoutUUID(plannedWorkoutUUID: String): List<PlannedWorkoutExercise>

    @Query("""
        SELECT * FROM planned_workout_exercises
        WHERE exercise_uuid = :exerciseUUID
        AND planned_workout_uuid = :plannedWorkoutUUID
        LIMIT 1
    """)
    suspend fun getPlannedWorkoutExerciseByExerciseAndWorkout(
        exerciseUUID: String,
        plannedWorkoutUUID: String
    ): PlannedWorkoutExercise?

    @Query("SELECT * FROM planned_workout_exercises WHERE planned_workout_uuid IN (:plannedWorkoutUUIDs)")
    suspend fun getPlannedWorkoutExercisesForPlannedWorkouts(plannedWorkoutUUIDs: List<String>): List<PlannedWorkoutExercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: PlannedWorkoutExercise) {
        val now = getNowISO8601()
        insertInternal(exercise.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<PlannedWorkoutExercise>) {
        val now = getNowISO8601()
        insertAllInternal(exercises.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun updatePlannedWorkoutExercise(exercise: PlannedWorkoutExercise) {
        val now = getNowISO8601()
        updateInternal(exercise.copy(updatedAt = now, synced = false))
    }

    @Query("UPDATE planned_workout_exercises SET status = :status, synced = 0, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun updateStatus(uuid: String, status: String, updatedAt: String = getNowISO8601())

    @Query("UPDATE planned_workout_exercises SET ignored = 1, synced = 0, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun markAsIgnored(uuid: String, updatedAt: String = getNowISO8601())

    @Query("UPDATE planned_workout_exercises SET ignored = 0, synced = 0, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun markAsNotIgnored(uuid: String, updatedAt: String = getNowISO8601())

    @Delete
    suspend fun delete(exercise: PlannedWorkoutExercise)


    // 🔁 Synchronisation
    @Query("SELECT * FROM planned_workout_exercises WHERE synced = 0")
    suspend fun getAllUnsynced(): List<PlannedWorkoutExercise>

    @Query("UPDATE planned_workout_exercises SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE planned_workout_exercises SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM planned_workout_exercises WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("""
        UPDATE planned_workout_exercises 
        SET pendingDeletion = 1, updated_at = :updatedAt
        WHERE planned_workout_uuid = :plannedWorkoutUUID AND exercise_uuid = :exerciseUUID
    """)
    suspend fun markAsPendingDeletionWithPlannedWorkoutUUIDAndExerciseUUID(
        plannedWorkoutUUID: String,
        exerciseUUID: String,
        updatedAt: String = getNowISO8601()
    )

    @Query("UPDATE planned_workout_exercises SET pendingDeletion = 1, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM planned_workout_exercises WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<PlannedWorkoutExercise>

    @Query("DELETE FROM planned_workout_exercises")
    suspend fun clearAll()

    @Query("SELECT * FROM planned_workout_exercises")
    suspend fun getAllOnce(): List<PlannedWorkoutExercise>


    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(exercise: PlannedWorkoutExercise)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(exercises: List<PlannedWorkoutExercise>)

    @Update
    suspend fun updateInternal(exercise: PlannedWorkoutExercise)

    // --- Insertion depuis le serveur (respecte payload tel quel) ---
    suspend fun insertFromServer(exercise: PlannedWorkoutExercise) {
        insertInternal(exercise.copy(synced = true, pendingDeletion = false))
    }

    suspend fun insertAllFromServer(exercises: List<PlannedWorkoutExercise>) {
        insertAllInternal(exercises.map { it.copy(synced = true, pendingDeletion = false) })
    }

    suspend fun updateFromServer(exercise: PlannedWorkoutExercise) {
        updateInternal(exercise)
    }
}
