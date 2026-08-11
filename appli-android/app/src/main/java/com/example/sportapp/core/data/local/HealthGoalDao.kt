package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.HealthGoal
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthGoalDao {

    @Query("SELECT * FROM health_goals ORDER BY effective_from DESC")
    fun observeAll(): Flow<List<HealthGoal>>

    @RawQuery(observedEntities = [HealthGoal::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, HealthGoal>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<HealthGoal>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM health_goals WHERE uuid = :uuid")
    suspend fun getByUUID(uuid: String): HealthGoal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HealthGoal) {
        val now = getNowISO8601()
        insertInternal(item.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HealthGoal>) {
        val now = getNowISO8601()
        insertAllInternal(items.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun update(item: HealthGoal) {
        val now = getNowISO8601()
        updateInternal(item.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(item: HealthGoal)

    // 🔁 Synchronisation
    @Query("SELECT * FROM health_goals WHERE synced = 0")
    suspend fun getAllUnsynced(): List<HealthGoal>

    @Query("UPDATE health_goals SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE health_goals SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM health_goals WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE health_goals SET pendingDeletion = 1, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM health_goals WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<HealthGoal>

    @Query("DELETE FROM health_goals")
    suspend fun clearAll()

    @Query("SELECT * FROM health_goals")
    suspend fun getAllOnce(): List<HealthGoal>

    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(item: HealthGoal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(items: List<HealthGoal>)

    @Update
    suspend fun updateInternal(item: HealthGoal)

    // --- depuis serveur (respect payload tel quel)
    suspend fun insertFromServer(item: HealthGoal) =
        insertInternal(item.copy(synced = true, pendingDeletion = false))
    suspend fun insertAllFromServer(items: List<HealthGoal>) =
        insertAllInternal(items.map { it.copy(synced = true, pendingDeletion = false) })
    suspend fun updateFromServer(item: HealthGoal) = updateInternal(item)
}
