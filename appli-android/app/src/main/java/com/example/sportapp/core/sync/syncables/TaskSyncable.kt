package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.TaskDao
import com.example.sportapp.core.data.model.Task
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

/**
 * Phase 0 (2026-05-12) : remplace RoutineTaskSyncable. Modele unifie Task.
 */
class TaskSyncable(
    private val dao: TaskDao
) : SyncableEntity<Task> {

    override val entityName = "Tasks"
    override val displayName = "Task"
    override val iconRes = R.drawable.ic_rounded_list_alt
    override val entityClass = Task::class

    override fun observeAll(): Flow<List<Task>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.taskApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: Task) = dao.insertFromServer(item)
    override suspend fun bulkInsertFromServer(items: List<Task>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: Task) {
        RetrofitInstance.taskApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<Task>) {
        RetrofitInstance.taskApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: Task) {
        RetrofitInstance.taskApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: Task) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: Task) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: Task) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: Task) = dao.delete(item)

    override fun keyOf(item: Task) = item.uuid
    override fun updatedAtOf(item: Task) = item.updatedAt
    override fun syncedOf(item: Task) = item.synced
    override fun pendingDeletionOf(item: Task) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
