package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.Meal
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {

    @Query("SELECT * FROM meals ORDER BY date DESC, order_index ASC")
    fun observeAll(): Flow<List<Meal>>

    @RawQuery(observedEntities = [Meal::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, Meal>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<Meal>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM meals WHERE uuid = :uuid")
    suspend fun getByUUID(uuid: String): Meal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meal: Meal) {
        val now = getNowISO8601()
        insertInternal(meal.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(meals: List<Meal>) {
        val now = getNowISO8601()
        insertAllInternal(meals.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun update(meal: Meal) {
        val now = getNowISO8601()
        updateInternal(meal.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(meal: Meal)

    // 🔁 Synchronisation
    @Query("SELECT * FROM meals WHERE synced = 0")
    suspend fun getAllUnsynced(): List<Meal>

    @Query("UPDATE meals SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE meals SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM meals WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE meals SET pendingDeletion = 1, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM meals WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<Meal>

    @Query("DELETE FROM meals")
    suspend fun clearAll()

    @Query("SELECT * FROM meals")
    suspend fun getAllOnce(): List<Meal>

    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(meal: Meal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(meals: List<Meal>)

    @Update
    suspend fun updateInternal(meal: Meal)

    // --- depuis serveur (respect payload tel quel)
    suspend fun insertFromServer(meal: Meal) =
        insertInternal(meal.copy(synced = true, pendingDeletion = false))
    suspend fun insertAllFromServer(meals: List<Meal>) =
        insertAllInternal(meals.map { it.copy(synced = true, pendingDeletion = false) })
    suspend fun updateFromServer(meal: Meal) = updateInternal(meal)
}
