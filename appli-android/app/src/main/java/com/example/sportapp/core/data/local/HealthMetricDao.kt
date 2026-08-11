package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.HealthMetric
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthMetricDao {

    @Query("SELECT * FROM health_metrics ORDER BY date DESC")
    fun observeAll(): Flow<List<HealthMetric>>

    @RawQuery(observedEntities = [HealthMetric::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, HealthMetric>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<HealthMetric>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM health_metrics WHERE uuid = :uuid")
    suspend fun getByUUID(uuid: String): HealthMetric?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HealthMetric) {
        val now = getNowISO8601()
        insertInternal(item.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HealthMetric>) {
        val now = getNowISO8601()
        insertAllInternal(items.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun update(item: HealthMetric) {
        val now = getNowISO8601()
        updateInternal(item.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(item: HealthMetric)

    // 🔁 Synchronisation
    @Query("SELECT * FROM health_metrics WHERE synced = 0")
    suspend fun getAllUnsynced(): List<HealthMetric>

    @Query("UPDATE health_metrics SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE health_metrics SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM health_metrics WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE health_metrics SET pendingDeletion = 1, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM health_metrics WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<HealthMetric>

    @Query("DELETE FROM health_metrics")
    suspend fun clearAll()

    @Query("SELECT * FROM health_metrics")
    suspend fun getAllOnce(): List<HealthMetric>

    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(item: HealthMetric)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(items: List<HealthMetric>)

    @Update
    suspend fun updateInternal(item: HealthMetric)

    // --- depuis serveur (respect payload tel quel)
    suspend fun insertFromServer(item: HealthMetric) =
        insertInternal(item.copy(synced = true, pendingDeletion = false))
    suspend fun insertAllFromServer(items: List<HealthMetric>) =
        insertAllInternal(items.map { it.copy(synced = true, pendingDeletion = false) })
    suspend fun updateFromServer(item: HealthMetric) = updateInternal(item)
}
