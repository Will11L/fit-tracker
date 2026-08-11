package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface MuscleDao {

    @Query("SELECT * FROM muscles")
    fun observeAll(): Flow<List<Muscle>>

    @RawQuery(observedEntities = [Muscle::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, Muscle>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<Muscle>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM muscles WHERE uuid = :uuid LIMIT 1")
    fun observeMuscleByUUID(uuid: String): Flow<Muscle?>

    @Query("SELECT * FROM muscles WHERE uuid = :uuid")
    suspend fun getMuscleByUUID(uuid: String): Muscle?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(muscle: Muscle) {
        val now = getNowISO8601()
        insertInternal(muscle.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(muscles: List<Muscle>) {
        val now = getNowISO8601()
        insertAllInternal(muscles.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun updateMuscle(muscle: Muscle) {
        val now = getNowISO8601()
        updateInternal(muscle.copy(updatedAt = now, synced = false))
    }

    @Query("UPDATE muscles SET is_favorite = :isFavorite, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun updateFavorite(uuid: String, isFavorite: Boolean, updatedAt: String = getNowISO8601())

    @Delete
    suspend fun delete(muscle: Muscle)

    // 🔁 Synchronisation
    @Query("SELECT * FROM muscles WHERE synced = 0")
    suspend fun getAllUnsynced(): List<Muscle>

    @Query("UPDATE muscles SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE muscles SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM muscles WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE muscles SET pendingDeletion = 1, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM muscles WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<Muscle>

    @Query("DELETE FROM muscles")
    suspend fun clearAll()

    @Query("SELECT * FROM muscles")
    suspend fun getAllOnce(): List<Muscle>


    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(muscle: Muscle)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(muscles: List<Muscle>)

    @Update
    suspend fun updateInternal(muscle: Muscle)

    // --- Insertion depuis le serveur (respecte payload tel quel) ---
    suspend fun insertFromServer(muscle: Muscle) {
        insertInternal(muscle.copy(synced = true, pendingDeletion = false))
    }

    suspend fun insertAllFromServer(muscles: List<Muscle>) {
        insertAllInternal(muscles.map { it.copy(synced = true, pendingDeletion = false) })
    }

    suspend fun updateFromServer(muscle: Muscle) {
        updateInternal(muscle)
    }
}
