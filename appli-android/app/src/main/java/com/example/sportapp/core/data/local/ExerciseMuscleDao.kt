package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.data.model.ExerciseMuscle
import com.example.sportapp.core.data.model.projections.ExerciseMuscleSimple
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseMuscleDao {

    @Query("SELECT * FROM exercise_muscles")
    fun observeAll(): Flow<List<ExerciseMuscle>>

    @RawQuery(observedEntities = [ExerciseMuscle::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, ExerciseMuscle>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<ExerciseMuscle>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM exercise_muscles")
    fun observeAllLinks(): Flow<List<ExerciseMuscle>>

    @Query("SELECT * FROM exercise_muscles WHERE exercise_uuid = :exerciseUUID")
    fun observeByExerciseUUID(exerciseUUID: String): Flow<List<ExerciseMuscle>>

    @Query("""
        SELECT * FROM exercise_muscles 
        WHERE exercise_uuid = :exerciseUUID AND muscle_uuid = :muscleUUID
    """)
    suspend fun getExerciseMuscleByUUIDs(exerciseUUID: String, muscleUUID: String): ExerciseMuscle?

    @Query("SELECT exercise_uuid, muscle_uuid FROM exercise_muscles WHERE exercise_uuid IN (:exerciseUUIDs)")
    suspend fun getMusclesForExercises(exerciseUUIDs: List<String>): List<ExerciseMuscleSimple>

    @Query("SELECT * FROM exercise_muscles WHERE exercise_uuid = :exerciseUUID")
    suspend fun getExerciseMusclesByExerciseUUID(exerciseUUID: String): List<ExerciseMuscle>

    @Query("SELECT * FROM exercise_muscles WHERE uuid = :uuid")
    suspend fun getExerciseMuscleByUUID(uuid: String): ExerciseMuscle?

    @Query("SELECT * FROM exercise_muscles WHERE exercise_uuid = :exerciseUUID")
    fun getMusclesByExerciseUUIDFlow(exerciseUUID: String): Flow<List<ExerciseMuscle>>

    @Query("""
        SELECT m.*
        FROM exercise_muscles em
        INNER JOIN muscles m ON em.muscle_uuid = m.uuid
        WHERE em.exercise_uuid = :exerciseUUID
    """)
    suspend fun getMusclesByExerciseUUID(exerciseUUID: String): List<Muscle>

    // B3-2 Etape 6 : exercises lies a un muscle (Flow reactif).
    @Query("""
        SELECT e.* FROM exercises e
        INNER JOIN exercise_muscles em ON em.exercise_uuid = e.uuid
        WHERE em.muscle_uuid = :muscleUUID
          AND em.pendingDeletion = 0
          AND e.pendingDeletion = 0
        ORDER BY e.name ASC
    """)
    fun observeExercisesByMuscle(muscleUUID: String): Flow<List<Exercise>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exerciseMuscle: ExerciseMuscle) {
        val now = getNowISO8601()
        insertInternal(exerciseMuscle.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exerciseMuscles: List<ExerciseMuscle>) {
        val now = getNowISO8601()
        insertAllInternal(exerciseMuscles.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun updateExerciseMuscle(exerciseMuscle: ExerciseMuscle) {
        val now = getNowISO8601()
        updateInternal(exerciseMuscle.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(exerciseMuscle: ExerciseMuscle)


    // 🔁 Synchronisation
    @Query("SELECT * FROM exercise_muscles WHERE synced = 0")
    suspend fun getAllUnsynced(): List<ExerciseMuscle>

    @Query("UPDATE exercise_muscles SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE exercise_muscles SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM exercise_muscles WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE exercise_muscles SET pendingDeletion = 1, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun markAsPendingDeletionInternal(uuid: String, updatedAt: String)

    suspend fun markAsPendingDeletion(uuid: String) {
        val now = getNowISO8601()
        markAsPendingDeletionInternal(uuid, now)
    }

    @Query("SELECT * FROM exercise_muscles WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<ExerciseMuscle>

    @Query("DELETE FROM exercise_muscles")
    suspend fun clearAll()

    @Query("SELECT * FROM exercise_muscles")
    suspend fun getAllOnce(): List<ExerciseMuscle>


    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(exerciseMuscle: ExerciseMuscle)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(exerciseMuscles: List<ExerciseMuscle>)

    @Update
    suspend fun updateInternal(exerciseMuscle: ExerciseMuscle)

    // --- Insertion depuis le serveur (respecte payload tel quel) ---
    suspend fun insertFromServer(exerciseMuscle: ExerciseMuscle) {
        insertInternal(exerciseMuscle.copy(synced = true, pendingDeletion = false))
    }

    suspend fun insertAllFromServer(exerciseMuscles: List<ExerciseMuscle>) {
        insertAllInternal(exerciseMuscles.map { it.copy(synced = true, pendingDeletion = false) })
    }

    suspend fun updateFromServer(exerciseMuscle: ExerciseMuscle) {
        updateInternal(exerciseMuscle)
    }
}
