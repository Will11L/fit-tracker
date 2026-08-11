package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.SupersetExercise
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface SupersetExerciseDao {

    @Query("SELECT * FROM superset_exercises")
    fun observeAll(): Flow<List<SupersetExercise>>

    @RawQuery(observedEntities = [SupersetExercise::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, SupersetExercise>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<SupersetExercise>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM superset_exercises WHERE uuid = :uuid")
    suspend fun getSupersetExerciseByUUID(uuid: String): SupersetExercise?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: SupersetExercise) {
        val now = getNowISO8601()
        insertInternal(exercise.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<SupersetExercise>) {
        val now = getNowISO8601()
        insertAllInternal(exercises.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun updateSupersetExercise(exercise: SupersetExercise) {
        val now = getNowISO8601()
        updateInternal(exercise.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(exercise: SupersetExercise)


    // 🔁 Synchronisation
    @Query("SELECT * FROM superset_exercises WHERE synced = 0")
    suspend fun getAllUnsynced(): List<SupersetExercise>

    @Query("UPDATE superset_exercises SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE superset_exercises SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM superset_exercises WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE superset_exercises SET pendingDeletion = 1, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM superset_exercises WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<SupersetExercise>

    @Query("DELETE FROM superset_exercises")
    suspend fun clearAll()

    @Query("SELECT * FROM superset_exercises")
    suspend fun getAllOnce(): List<SupersetExercise>


    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(exercise: SupersetExercise)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(exercises: List<SupersetExercise>)

    @Update
    suspend fun updateInternal(exercise: SupersetExercise)

    // --- Insertion depuis le serveur (respecte payload tel quel) ---
    suspend fun insertFromServer(exercise: SupersetExercise) {
        insertInternal(exercise.copy(synced = true, pendingDeletion = false))
    }

    suspend fun insertAllFromServer(exercises: List<SupersetExercise>) {
        insertAllInternal(exercises.map { it.copy(synced = true, pendingDeletion = false) })
    }

    suspend fun updateFromServer(exercise: SupersetExercise) {
        updateInternal(exercise)
    }
}
