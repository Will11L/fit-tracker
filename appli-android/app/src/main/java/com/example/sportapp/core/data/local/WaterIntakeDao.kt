package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.WaterIntake
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterIntakeDao {

    @Query("SELECT * FROM water_intakes ORDER BY date DESC, created_at DESC")
    fun observeAll(): Flow<List<WaterIntake>>

    @RawQuery(observedEntities = [WaterIntake::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, WaterIntake>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<WaterIntake>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM water_intakes WHERE uuid = :uuid")
    suspend fun getByUUID(uuid: String): WaterIntake?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WaterIntake) {
        val now = getNowISO8601()
        insertInternal(item.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<WaterIntake>) {
        val now = getNowISO8601()
        insertAllInternal(items.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun update(item: WaterIntake) {
        val now = getNowISO8601()
        updateInternal(item.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(item: WaterIntake)

    // 🔁 Synchronisation
    @Query("SELECT * FROM water_intakes WHERE synced = 0")
    suspend fun getAllUnsynced(): List<WaterIntake>

    @Query("UPDATE water_intakes SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE water_intakes SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM water_intakes WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE water_intakes SET pendingDeletion = 1, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM water_intakes WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<WaterIntake>

    @Query("DELETE FROM water_intakes")
    suspend fun clearAll()

    @Query("SELECT * FROM water_intakes")
    suspend fun getAllOnce(): List<WaterIntake>

    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(item: WaterIntake)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(items: List<WaterIntake>)

    @Update
    suspend fun updateInternal(item: WaterIntake)

    // --- depuis serveur (respect payload tel quel)
    suspend fun insertFromServer(item: WaterIntake) =
        insertInternal(item.copy(synced = true, pendingDeletion = false))
    suspend fun insertAllFromServer(items: List<WaterIntake>) =
        insertAllInternal(items.map { it.copy(synced = true, pendingDeletion = false) })
    suspend fun updateFromServer(item: WaterIntake) = updateInternal(item)
}
