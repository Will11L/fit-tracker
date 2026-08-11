package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.TaskCheck
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

/**
 * Phase 0 (2026-05-12) : DAO unifie TaskCheck. Rename date -> occurrence_date.
 */
@Dao
interface TaskCheckDao {

    // -------- observe --------
    @Query("SELECT * FROM task_checks WHERE uuid = :uuid LIMIT 1")
    fun observeByUUID(uuid: String): Flow<TaskCheck?>

    @Query("SELECT * FROM task_checks ORDER BY occurrence_date DESC")
    fun observeAll(): Flow<List<TaskCheck>>

    @RawQuery(observedEntities = [TaskCheck::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, TaskCheck>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<TaskCheck>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM task_checks WHERE occurrence_date = :date ORDER BY checked_at DESC")
    fun observeByDate(date: String): Flow<List<TaskCheck>>

    @Query("SELECT * FROM task_checks WHERE task_uuid = :taskUUID ORDER BY occurrence_date DESC")
    fun observeByTask(taskUUID: String): Flow<List<TaskCheck>>

    // -------- suspend reads --------
    @Query("SELECT * FROM task_checks WHERE uuid = :uuid")
    suspend fun getByUUID(uuid: String): TaskCheck?

    @Query("""
        SELECT * FROM task_checks
        WHERE task_uuid = :taskUUID AND occurrence_date = :date LIMIT 1
    """)
    suspend fun getByTaskAndDate(taskUUID: String, date: String): TaskCheck?

    @Query("""
        SELECT uuid FROM task_checks
        WHERE task_uuid = :taskUUID AND pendingDeletion = 0
    """)
    suspend fun getActiveUUIDsByTask(taskUUID: String): List<String>

    // -------- writes (wrappers, force synced=false + updatedAt) --------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(check: TaskCheck) {
        val now = getNowISO8601()
        insertInternal(check.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(checks: List<TaskCheck>) {
        val now = getNowISO8601()
        insertAllInternal(checks.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun update(check: TaskCheck) {
        val now = getNowISO8601()
        updateInternal(check.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(check: TaskCheck)

    // -------- sync infra --------
    @Query("SELECT * FROM task_checks WHERE synced = 0")
    suspend fun getAllUnsynced(): List<TaskCheck>

    @Query("UPDATE task_checks SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE task_checks SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM task_checks WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE task_checks SET pendingDeletion = 1, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM task_checks WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<TaskCheck>

    @Query("DELETE FROM task_checks")
    suspend fun clearAll()

    @Query("SELECT * FROM task_checks")
    suspend fun getAllOnce(): List<TaskCheck>

    // -------- internal (Room) --------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(check: TaskCheck)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(checks: List<TaskCheck>)

    @Update
    suspend fun updateInternal(check: TaskCheck)

    // -------- from server (force synced=true, pendingDeletion=false) --------
    suspend fun insertFromServer(check: TaskCheck) =
        insertInternal(check.copy(synced = true, pendingDeletion = false))

    suspend fun insertAllFromServer(checks: List<TaskCheck>) =
        insertAllInternal(checks.map { it.copy(synced = true, pendingDeletion = false) })

    suspend fun updateFromServer(check: TaskCheck) = updateInternal(check)
}
