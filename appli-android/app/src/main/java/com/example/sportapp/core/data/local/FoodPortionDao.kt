package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.FoodPortion
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodPortionDao {

    @Query("SELECT * FROM food_portions")
    fun observeAll(): Flow<List<FoodPortion>>

    @RawQuery(observedEntities = [FoodPortion::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, FoodPortion>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<FoodPortion>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM food_portions WHERE uuid = :uuid")
    suspend fun getByUUID(uuid: String): FoodPortion?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(portion: FoodPortion) {
        val now = getNowISO8601()
        insertInternal(portion.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(portions: List<FoodPortion>) {
        val now = getNowISO8601()
        insertAllInternal(portions.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun update(portion: FoodPortion) {
        val now = getNowISO8601()
        updateInternal(portion.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(portion: FoodPortion)

    // 🔁 Synchronisation
    @Query("SELECT * FROM food_portions WHERE synced = 0")
    suspend fun getAllUnsynced(): List<FoodPortion>

    @Query("UPDATE food_portions SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE food_portions SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM food_portions WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE food_portions SET pendingDeletion = 1, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM food_portions WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<FoodPortion>

    @Query("DELETE FROM food_portions")
    suspend fun clearAll()

    @Query("SELECT * FROM food_portions")
    suspend fun getAllOnce(): List<FoodPortion>

    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(portion: FoodPortion)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(portions: List<FoodPortion>)

    @Update
    suspend fun updateInternal(portion: FoodPortion)

    // --- depuis serveur (respect payload tel quel)
    suspend fun insertFromServer(portion: FoodPortion) =
        insertInternal(portion.copy(synced = true, pendingDeletion = false))
    suspend fun insertAllFromServer(portions: List<FoodPortion>) =
        insertAllInternal(portions.map { it.copy(synced = true, pendingDeletion = false) })
    suspend fun updateFromServer(portion: FoodPortion) = updateInternal(portion)
}
