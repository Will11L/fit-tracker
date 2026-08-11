package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.HealthStepCount
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthStepCountDao {

    @Query("SELECT * FROM health_step_counts ORDER BY date DESC, bucket_start DESC")
    fun observeAll(): Flow<List<HealthStepCount>>

    @RawQuery(observedEntities = [HealthStepCount::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, HealthStepCount>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<HealthStepCount>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM health_step_counts WHERE uuid = :uuid")
    suspend fun getByUUID(uuid: String): HealthStepCount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HealthStepCount) {
        val now = getNowISO8601()
        insertInternal(item.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HealthStepCount>) {
        val now = getNowISO8601()
        insertAllInternal(items.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun update(item: HealthStepCount) {
        val now = getNowISO8601()
        updateInternal(item.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(item: HealthStepCount)

    // 🔁 Synchronisation
    @Query("SELECT * FROM health_step_counts WHERE synced = 0")
    suspend fun getAllUnsynced(): List<HealthStepCount>

    @Query("UPDATE health_step_counts SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE health_step_counts SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM health_step_counts WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE health_step_counts SET pendingDeletion = 1, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM health_step_counts WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<HealthStepCount>

    @Query("DELETE FROM health_step_counts")
    suspend fun clearAll()

    @Query("SELECT * FROM health_step_counts")
    suspend fun getAllOnce(): List<HealthStepCount>

    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(item: HealthStepCount)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(items: List<HealthStepCount>)

    @Update
    suspend fun updateInternal(item: HealthStepCount)

    // --- depuis serveur (respect payload tel quel)
    suspend fun insertFromServer(item: HealthStepCount) =
        insertInternal(item.copy(synced = true, pendingDeletion = false))
    suspend fun insertAllFromServer(items: List<HealthStepCount>) =
        insertAllInternal(items.map { it.copy(synced = true, pendingDeletion = false) })
    suspend fun updateFromServer(item: HealthStepCount) = updateInternal(item)
}
