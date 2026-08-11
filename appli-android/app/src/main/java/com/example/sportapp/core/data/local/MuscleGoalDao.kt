package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.MuscleGoal
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface MuscleGoalDao {

    @Query("SELECT * FROM muscle_goals")
    fun observeAll(): Flow<List<MuscleGoal>>

    @RawQuery(observedEntities = [MuscleGoal::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, MuscleGoal>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<MuscleGoal>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM muscle_goals WHERE week_iso = :weekISO")
    fun observeGoalsForWeek(weekISO: String): Flow<List<MuscleGoal>>

    @Query("SELECT * FROM muscle_goals WHERE uuid = :uuid")
    suspend fun getByUUID(uuid: String): MuscleGoal?

    @Query("SELECT * FROM muscle_goals WHERE week_iso = :weekISO")
    suspend fun getGoalsForWeek(weekISO: String): List<MuscleGoal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: MuscleGoal): Long {
        val now = getNowISO8601()
        return insertInternal(goal.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<MuscleGoal>) {
        val now = getNowISO8601()
        insertAllInternal(goals.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun update(goal: MuscleGoal) {
        val now = getNowISO8601()
        updateInternal(goal.copy(updatedAt = now, synced = false))
    }

    @Query("""
        UPDATE muscle_goals 
        SET done = :done, status = :status, synced = 0, updated_at = :updatedAt
        WHERE uuid = :uuid
    """)
    suspend fun updateDoneCount(uuid: String, done: Int, status: String, updatedAt: String = getNowISO8601())

    @Query("""
        UPDATE muscle_goals 
        SET done = :done, target = :target, synced = :synced, updated_at = :updatedAt
        WHERE uuid = :uuid
    """)
    suspend fun updateFieldsByUUID(uuid: String, done: Int, target: String, synced: Boolean = false, updatedAt: String = getNowISO8601())

    @Delete
    suspend fun delete(goal: MuscleGoal)

    @Query("UPDATE muscle_goals SET status = :status, synced = 0, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun updateStatus(uuid: String, status: String, updatedAt: String = getNowISO8601())

    @Query("UPDATE muscle_goals SET priority = :newPriority, synced = 0, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun updatePriority(uuid: String, newPriority: String, updatedAt: String = getNowISO8601())

    @Query("UPDATE muscle_goals SET target = :newTarget, synced = 0, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun updateTarget(uuid: String, newTarget: String, updatedAt: String = getNowISO8601())

    @Query("UPDATE muscle_goals SET done = :newDone, synced = 0, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun updateDone(uuid: String, newDone: Int, updatedAt: String = getNowISO8601())


    // 🔁 Synchronisation
    @Query("SELECT * FROM muscle_goals WHERE synced = 0")
    suspend fun getAllUnsynced(): List<MuscleGoal>

    @Query("UPDATE muscle_goals SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE muscle_goals SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM muscle_goals WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE muscle_goals SET pendingDeletion = 1, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM muscle_goals WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<MuscleGoal>

    @Query("DELETE FROM muscle_goals")
    suspend fun clearAll()

    @Query("SELECT * FROM muscle_goals")
    suspend fun getAllOnce(): List<MuscleGoal>


    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(goal: MuscleGoal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(goals: List<MuscleGoal>)

    @Update
    suspend fun updateInternal(goal: MuscleGoal)

    // --- Insertion depuis le serveur (respecte payload tel quel) ---
    suspend fun insertFromServer(goal: MuscleGoal) : Long {
        return insertInternal(goal.copy(synced = true, pendingDeletion = false))
    }

    suspend fun insertAllFromServer(goals: List<MuscleGoal>) {
        insertAllInternal(goals.map { it.copy(synced = true, pendingDeletion = false) })
    }

    suspend fun updateFromServer(goal: MuscleGoal) {
        updateInternal(goal)
    }
}
