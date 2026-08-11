package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.TrainingCycleDao
import com.example.sportapp.core.data.model.TrainingCycle
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class TrainingCycleSyncable(
    private val dao: TrainingCycleDao
) : SyncableEntity<TrainingCycle> {

    override val entityName = "TrainingCycles"
    override val displayName = "Training Cycle"
    override val iconRes = R.drawable.ic_rounded_repeat
    override val entityClass = TrainingCycle::class

    override fun observeAll(): Flow<List<TrainingCycle>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.trainingCycleApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: TrainingCycle) = dao.insertFromServer(item)
    override suspend fun bulkInsertFromServer(items: List<TrainingCycle>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: TrainingCycle) {
        RetrofitInstance.trainingCycleApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<TrainingCycle>) {
        RetrofitInstance.trainingCycleApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: TrainingCycle) {
        RetrofitInstance.trainingCycleApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: TrainingCycle) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: TrainingCycle) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: TrainingCycle) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: TrainingCycle) = dao.delete(item)

    override fun keyOf(item: TrainingCycle) = item.uuid
    override fun updatedAtOf(item: TrainingCycle) = item.updatedAt
    override fun syncedOf(item: TrainingCycle) = item.synced
    override fun pendingDeletionOf(item: TrainingCycle) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
