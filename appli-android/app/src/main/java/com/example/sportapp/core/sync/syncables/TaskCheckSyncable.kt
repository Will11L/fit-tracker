package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.TaskCheckDao
import com.example.sportapp.core.data.model.TaskCheck
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

/**
 * Phase 0 (2026-05-12) : remplace RoutineTaskCheckSyncable. Modele unifie TaskCheck.
 */
class TaskCheckSyncable(
    private val dao: TaskCheckDao
) : SyncableEntity<TaskCheck> {

    override val entityName = "TaskChecks"
    override val displayName = "Task Check"
    override val iconRes = R.drawable.ic_rounded_list_alt
    override val entityClass = TaskCheck::class

    override fun observeAll(): Flow<List<TaskCheck>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.taskCheckApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: TaskCheck) = dao.insertFromServer(item)
    override suspend fun bulkInsertFromServer(items: List<TaskCheck>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: TaskCheck) {
        RetrofitInstance.taskCheckApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<TaskCheck>) {
        RetrofitInstance.taskCheckApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: TaskCheck) {
        RetrofitInstance.taskCheckApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: TaskCheck) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: TaskCheck) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: TaskCheck) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: TaskCheck) = dao.delete(item)

    override fun keyOf(item: TaskCheck) = item.uuid
    override fun updatedAtOf(item: TaskCheck) = item.updatedAt
    override fun syncedOf(item: TaskCheck) = item.synced
    override fun pendingDeletionOf(item: TaskCheck) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
