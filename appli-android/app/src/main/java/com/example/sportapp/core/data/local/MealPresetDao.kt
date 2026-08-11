package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.MealPreset
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface MealPresetDao {

    @Query("SELECT * FROM meal_presets ORDER BY order_index ASC")
    fun observeAll(): Flow<List<MealPreset>>

    @RawQuery(observedEntities = [MealPreset::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, MealPreset>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<MealPreset>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM meal_presets WHERE uuid = :uuid")
    suspend fun getByUUID(uuid: String): MealPreset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: MealPreset) {
        val now = getNowISO8601()
        insertInternal(preset.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(presets: List<MealPreset>) {
        val now = getNowISO8601()
        insertAllInternal(presets.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun update(preset: MealPreset) {
        val now = getNowISO8601()
        updateInternal(preset.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(preset: MealPreset)

    // 🔁 Synchronisation
    @Query("SELECT * FROM meal_presets WHERE synced = 0")
    suspend fun getAllUnsynced(): List<MealPreset>

    @Query("UPDATE meal_presets SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE meal_presets SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM meal_presets WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE meal_presets SET pendingDeletion = 1, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM meal_presets WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<MealPreset>

    @Query("DELETE FROM meal_presets")
    suspend fun clearAll()

    @Query("SELECT * FROM meal_presets")
    suspend fun getAllOnce(): List<MealPreset>

    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(preset: MealPreset)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(presets: List<MealPreset>)

    @Update
    suspend fun updateInternal(preset: MealPreset)

    // --- depuis serveur (respect payload tel quel)
    suspend fun insertFromServer(preset: MealPreset) =
        insertInternal(preset.copy(synced = true, pendingDeletion = false))
    suspend fun insertAllFromServer(presets: List<MealPreset>) =
        insertAllInternal(presets.map { it.copy(synced = true, pendingDeletion = false) })
    suspend fun updateFromServer(preset: MealPreset) = updateInternal(preset)
}
