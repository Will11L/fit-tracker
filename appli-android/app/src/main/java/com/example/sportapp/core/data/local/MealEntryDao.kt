package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface MealEntryDao {

    @Query("SELECT * FROM meal_entries")
    fun observeAll(): Flow<List<MealEntry>>

    @RawQuery(observedEntities = [MealEntry::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, MealEntry>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<MealEntry>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM meal_entries WHERE uuid = :uuid")
    suspend fun getByUUID(uuid: String): MealEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MealEntry) {
        val now = getNowISO8601()
        insertInternal(entry.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<MealEntry>) {
        val now = getNowISO8601()
        insertAllInternal(entries.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun update(entry: MealEntry) {
        val now = getNowISO8601()
        updateInternal(entry.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(entry: MealEntry)

    // 🔁 Synchronisation
    @Query("SELECT * FROM meal_entries WHERE synced = 0")
    suspend fun getAllUnsynced(): List<MealEntry>

    @Query("UPDATE meal_entries SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE meal_entries SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM meal_entries WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE meal_entries SET pendingDeletion = 1, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM meal_entries WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<MealEntry>

    @Query("DELETE FROM meal_entries")
    suspend fun clearAll()

    @Query("SELECT * FROM meal_entries")
    suspend fun getAllOnce(): List<MealEntry>

    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(entry: MealEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(entries: List<MealEntry>)

    @Update
    suspend fun updateInternal(entry: MealEntry)

    // --- depuis serveur (respect payload tel quel)
    suspend fun insertFromServer(entry: MealEntry) =
        insertInternal(entry.copy(synced = true, pendingDeletion = false))
    suspend fun insertAllFromServer(entries: List<MealEntry>) =
        insertAllInternal(entries.map { it.copy(synced = true, pendingDeletion = false) })
    suspend fun updateFromServer(entry: MealEntry) = updateInternal(entry)
}
