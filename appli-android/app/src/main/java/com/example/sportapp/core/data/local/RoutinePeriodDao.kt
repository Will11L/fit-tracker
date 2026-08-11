package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.RoutinePeriod
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutinePeriodDao {

    @Query("SELECT * FROM routine_periods WHERE uuid = :uuid LIMIT 1")
    fun observeByUUID(uuid: String): Flow<RoutinePeriod?>

    @Query("SELECT * FROM routine_periods ORDER BY order_index ASC, start_time ASC")
    fun observeAll(): Flow<List<RoutinePeriod>>

    @RawQuery(observedEntities = [RoutinePeriod::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, RoutinePeriod>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<RoutinePeriod>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM routine_periods WHERE uuid = :uuid")
    suspend fun getByUUID(uuid: String): RoutinePeriod?

    @Query("SELECT * FROM routine_periods WHERE pendingDeletion = 0 ORDER BY order_index ASC, start_time ASC")
    suspend fun getAllActive(): List<RoutinePeriod>

    @Query("SELECT * FROM routine_periods WHERE pendingDeletion = 0 ORDER BY start_time ASC")
    suspend fun getActiveOrderByStartTime(): List<RoutinePeriod>

    @Query("SELECT MAX(order_index) FROM routine_periods WHERE pendingDeletion = 0")
    suspend fun getMaxOrderIndex(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(period: RoutinePeriod) {
        val now = getNowISO8601()
        insertInternal(period.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(periods: List<RoutinePeriod>) {
        val now = getNowISO8601()
        insertAllInternal(periods.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun update(period: RoutinePeriod) {
        val now = getNowISO8601()
        updateInternal(period.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(period: RoutinePeriod)

    // 🔁 Synchronisation
    @Query("SELECT * FROM routine_periods WHERE synced = 0")
    suspend fun getAllUnsynced(): List<RoutinePeriod>

    @Query("UPDATE routine_periods SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE routine_periods SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM routine_periods WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE routine_periods SET pendingDeletion = 1, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM routine_periods WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<RoutinePeriod>

    @Query("DELETE FROM routine_periods")
    suspend fun clearAll()

    @Query("SELECT * FROM routine_periods")
    suspend fun getAllOnce(): List<RoutinePeriod>

    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(period: RoutinePeriod)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(periods: List<RoutinePeriod>)

    @Update
    suspend fun updateInternal(period: RoutinePeriod)

    // --- depuis serveur (respect payload tel quel)
    suspend fun insertFromServer(period: RoutinePeriod) =
        insertInternal(period.copy(synced = true, pendingDeletion = false))
    suspend fun insertAllFromServer(periods: List<RoutinePeriod>) =
        insertAllInternal(periods.map { it.copy(synced = true, pendingDeletion = false) })
    suspend fun updateFromServer(period: RoutinePeriod) = updateInternal(period)
}
