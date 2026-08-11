package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.TrainingCycle
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingCycleDao {

    @Query("SELECT * FROM training_cycles")
    fun observeAll(): Flow<List<TrainingCycle>>

    @RawQuery(observedEntities = [TrainingCycle::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, TrainingCycle>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<TrainingCycle>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM training_cycles WHERE uuid = :uuid")
    suspend fun getTrainingCycleByUUID(uuid: String): TrainingCycle?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cycle: TrainingCycle) {
        val now = getNowISO8601()
        insertInternal(cycle.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cycles: List<TrainingCycle>) {
        val now = getNowISO8601()
        insertAllInternal(cycles.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun updateTrainingCycle(cycle: TrainingCycle) {
        val now = getNowISO8601()
        updateInternal(cycle.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(cycle: TrainingCycle)


    // 🔁 Synchronisation
    @Query("SELECT * FROM training_cycles WHERE synced = 0")
    suspend fun getAllUnsynced(): List<TrainingCycle>

    @Query("UPDATE training_cycles SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE training_cycles SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM training_cycles WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE training_cycles SET pendingDeletion = 1, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM training_cycles WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<TrainingCycle>

    @Query("DELETE FROM training_cycles")
    suspend fun clearAll()

    @Query("SELECT * FROM training_cycles")
    suspend fun getAllOnce(): List<TrainingCycle>


    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(cycle: TrainingCycle)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(cycles: List<TrainingCycle>)

    @Update
    suspend fun updateInternal(cycle: TrainingCycle)

    // --- Insertion depuis le serveur (respecte payload tel quel) ---
    suspend fun insertFromServer(cycle: TrainingCycle) {
        insertInternal(cycle.copy(synced = true, pendingDeletion = false))
    }

    suspend fun insertAllFromServer(cycles: List<TrainingCycle>) {
        insertAllInternal(cycles.map { it.copy(synced = true, pendingDeletion = false) })
    }

    suspend fun updateFromServer(cycle: TrainingCycle) {
        updateInternal(cycle)
    }
}
