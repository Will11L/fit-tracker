package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises WHERE uuid = :uuid LIMIT 1")
    fun observeByUUID(uuid: String): Flow<Exercise?>

    @Query("SELECT * FROM exercises")
    fun observeAll(): Flow<List<Exercise>>

    @RawQuery(observedEntities = [Exercise::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, Exercise>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<Exercise>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM exercises WHERE name = :name")
    fun observeByName(name: String): Flow<Exercise?>

    @Query("SELECT * FROM exercises WHERE uuid = :uuid")
    suspend fun getExerciseByUUID(uuid: String): Exercise?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: Exercise) {
        val now = getNowISO8601()
        insertInternal(exercise.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<Exercise>) {
        val now = getNowISO8601()
        insertAllInternal(exercises.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun updateExercise(exercise: Exercise) {
        val now = getNowISO8601()
        updateInternal(exercise.copy(updatedAt = now, synced = false))
    }

    @Update
    suspend fun updateDescription(uuid: String, description: String) {
        val now = getNowISO8601()
        val exercise = getExerciseByUUID(uuid)
        if (exercise != null) {
            updateInternal(exercise.copy(description = description, updatedAt = now, synced = false))
        }
    }

    @Delete
    suspend fun delete(exercise: Exercise)

    @Query("SELECT * FROM exercises WHERE pendingDeletion = 0")
    suspend fun getAllActiveExercises(): List<Exercise>

    @Query("UPDATE exercises SET is_favorite = NOT is_favorite, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun toggleFavorite(uuid: String, updatedAt: String = getNowISO8601())

    // 🔁 Synchronisation
    @Query("SELECT * FROM exercises WHERE synced = 0")
    suspend fun getAllUnsynced(): List<Exercise>

    @Query("UPDATE exercises SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE exercises SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM exercises WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE exercises SET pendingDeletion = 1, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM exercises WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<Exercise>

    @Query("DELETE FROM exercises")
    suspend fun clearAll()

    @Query("SELECT * FROM exercises")
    suspend fun getAllOnce(): List<Exercise>


    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(exercise: Exercise)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(exercises: List<Exercise>)

    @Update
    suspend fun updateInternal(exercise: Exercise)

    // --- Insertion depuis le serveur (respecte payload tel quel) ---
    suspend fun insertFromServer(exercise: Exercise) {
        insertInternal(exercise.copy(synced = true, pendingDeletion = false))
    }

    suspend fun insertAllFromServer(exercises: List<Exercise>) {
        insertAllInternal(exercises.map { it.copy(synced = true, pendingDeletion = false) })
    }

    suspend fun updateFromServer(exercise: Exercise) {
        updateInternal(exercise)
    }
}
