package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.Food
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {

    @Query("SELECT * FROM foods ORDER BY name ASC")
    fun observeAll(): Flow<List<Food>>

    @RawQuery(observedEntities = [Food::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, Food>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<Food>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM foods WHERE uuid = :uuid")
    suspend fun getByUUID(uuid: String): Food?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: Food) {
        val now = getNowISO8601()
        insertInternal(food.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<Food>) {
        val now = getNowISO8601()
        insertAllInternal(foods.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun update(food: Food) {
        val now = getNowISO8601()
        updateInternal(food.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(food: Food)

    // 🔁 Synchronisation
    @Query("SELECT * FROM foods WHERE synced = 0")
    suspend fun getAllUnsynced(): List<Food>

    @Query("UPDATE foods SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE foods SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM foods WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE foods SET pendingDeletion = 1, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM foods WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<Food>

    @Query("DELETE FROM foods")
    suspend fun clearAll()

    @Query("SELECT * FROM foods")
    suspend fun getAllOnce(): List<Food>

    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(food: Food)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(foods: List<Food>)

    @Update
    suspend fun updateInternal(food: Food)

    // --- depuis serveur (respect payload tel quel)
    suspend fun insertFromServer(food: Food) =
        insertInternal(food.copy(synced = true, pendingDeletion = false))
    suspend fun insertAllFromServer(foods: List<Food>) =
        insertAllInternal(foods.map { it.copy(synced = true, pendingDeletion = false) })
    suspend fun updateFromServer(food: Food) = updateInternal(food)
}
