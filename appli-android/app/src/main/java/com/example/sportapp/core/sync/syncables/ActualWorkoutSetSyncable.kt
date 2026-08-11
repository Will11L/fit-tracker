package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.ActualWorkoutSetDao
import com.example.sportapp.core.data.model.ActualWorkoutSet
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class ActualWorkoutSetSyncable(
    private val dao: ActualWorkoutSetDao
) : SyncableEntity<ActualWorkoutSet> {

    override val entityName = "ActualWorkoutSets"
    override val displayName = "Workout Set"
    override val iconRes = R.drawable.ic_rounded_list_alt
    override val entityClass = ActualWorkoutSet::class

    override fun observeAll(): Flow<List<ActualWorkoutSet>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.actualWorkoutSetApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: ActualWorkoutSet) = dao.insertFromServer(item)
    override suspend fun bulkInsertFromServer(items: List<ActualWorkoutSet>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: ActualWorkoutSet) {
        RetrofitInstance.actualWorkoutSetApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<ActualWorkoutSet>) {
        RetrofitInstance.actualWorkoutSetApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: ActualWorkoutSet) {
        RetrofitInstance.actualWorkoutSetApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: ActualWorkoutSet) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: ActualWorkoutSet) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: ActualWorkoutSet) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: ActualWorkoutSet) = dao.delete(item)

    override fun keyOf(item: ActualWorkoutSet) = item.uuid
    override fun updatedAtOf(item: ActualWorkoutSet) = item.updatedAt
    override fun syncedOf(item: ActualWorkoutSet) = item.synced
    override fun pendingDeletionOf(item: ActualWorkoutSet) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
