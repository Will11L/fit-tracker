package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.RoutinePeriodDao
import com.example.sportapp.core.data.model.RoutinePeriod
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class RoutinePeriodSyncable(
    private val dao: RoutinePeriodDao
) : SyncableEntity<RoutinePeriod> {

    override val entityName = "RoutinePeriods"
    override val displayName = "Routine Period"
    override val iconRes = R.drawable.ic_rounded_list_alt
    override val entityClass = RoutinePeriod::class

    override fun observeAll(): Flow<List<RoutinePeriod>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.routinePeriodApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: RoutinePeriod) = dao.insertFromServer(item)
    override suspend fun bulkInsertFromServer(items: List<RoutinePeriod>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: RoutinePeriod) {
        RetrofitInstance.routinePeriodApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<RoutinePeriod>) {
        RetrofitInstance.routinePeriodApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: RoutinePeriod) {
        RetrofitInstance.routinePeriodApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: RoutinePeriod) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: RoutinePeriod) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: RoutinePeriod) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: RoutinePeriod) = dao.delete(item)

    override fun keyOf(item: RoutinePeriod) = item.uuid
    override fun updatedAtOf(item: RoutinePeriod) = item.updatedAt
    override fun syncedOf(item: RoutinePeriod) = item.synced
    override fun pendingDeletionOf(item: RoutinePeriod) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
