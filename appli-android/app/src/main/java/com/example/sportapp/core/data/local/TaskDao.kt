package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.Task
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

/**
 * Phase 0 (2026-05-12) : DAO unifie Task. Style A canonique (cf. T4.2 sync layer).
 *
 * Queries dediees :
 *   - observeAll, observeByUUID, observeDailyByPeriod (RoutineTasksScreen)
 *   - observeByDueDate, observeByDueDateRange (Phase 1 TasksCalendarScreen)
 *   - observeByRecurrenceKind (filtre par type)
 */
@Dao
interface TaskDao {

    // -------- observe --------
    @Query("SELECT * FROM tasks WHERE uuid = :uuid LIMIT 1")
    fun observeByUUID(uuid: String): Flow<Task?>

    @Query("SELECT * FROM tasks ORDER BY recurrence_kind ASC, order_index ASC, title ASC")
    fun observeAll(): Flow<List<Task>>

    @RawQuery(observedEntities = [Task::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, Task>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<Task>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("""
        SELECT * FROM tasks
        WHERE recurrence_kind = 'DAILY' AND period_uuid = :periodUUID
        ORDER BY order_index ASC
    """)
    fun observeDailyByPeriod(periodUUID: String): Flow<List<Task>>

    @Query("""
        SELECT * FROM tasks
        WHERE recurrence_kind = 'NONE' AND due_date = :date
        ORDER BY due_time ASC, title ASC
    """)
    fun observeByDueDate(date: String): Flow<List<Task>>

    @Query("""
        SELECT * FROM tasks
        WHERE recurrence_kind = 'NONE'
          AND due_date BETWEEN :startDate AND :endDate
        ORDER BY due_date ASC, due_time ASC
    """)
    fun observeByDueDateRange(startDate: String, endDate: String): Flow<List<Task>>

    // -------- suspend reads --------
    @Query("SELECT * FROM tasks WHERE uuid = :uuid")
    suspend fun getByUUID(uuid: String): Task?

    @Query("""
        SELECT * FROM tasks
        WHERE recurrence_kind = 'DAILY' AND period_uuid = :periodUUID AND pendingDeletion = 0
        ORDER BY order_index ASC
    """)
    suspend fun getActiveDailyByPeriod(periodUUID: String): List<Task>

    @Query("""
        SELECT MAX(order_index) FROM tasks
        WHERE recurrence_kind = 'DAILY' AND period_uuid = :periodUUID AND pendingDeletion = 0
    """)
    suspend fun getMaxOrderForPeriod(periodUUID: String): Int?

    // -------- writes (wrappers, force synced=false + updatedAt) --------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task) {
        val now = getNowISO8601()
        insertInternal(task.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<Task>) {
        val now = getNowISO8601()
        insertAllInternal(tasks.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun update(task: Task) {
        val now = getNowISO8601()
        updateInternal(task.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(task: Task)

    // -------- sync infra --------
    @Query("SELECT * FROM tasks WHERE synced = 0")
    suspend fun getAllUnsynced(): List<Task>

    @Query("UPDATE tasks SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE tasks SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM tasks WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE tasks SET pendingDeletion = 1, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM tasks WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<Task>

    @Query("DELETE FROM tasks")
    suspend fun clearAll()

    @Query("SELECT * FROM tasks")
    suspend fun getAllOnce(): List<Task>

    // -------- internal (Room) --------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(task: Task)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(tasks: List<Task>)

    @Update
    suspend fun updateInternal(task: Task)

    // -------- from server (force synced=true, pendingDeletion=false) --------
    suspend fun insertFromServer(task: Task) =
        insertInternal(task.copy(synced = true, pendingDeletion = false))

    suspend fun insertAllFromServer(tasks: List<Task>) =
        insertAllInternal(tasks.map { it.copy(synced = true, pendingDeletion = false) })

    suspend fun updateFromServer(task: Task) = updateInternal(task)
}
