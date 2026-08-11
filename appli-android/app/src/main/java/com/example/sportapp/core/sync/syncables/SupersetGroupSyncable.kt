package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.SupersetGroupDao
import com.example.sportapp.core.data.model.SupersetGroup
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class SupersetGroupSyncable(
    private val dao: SupersetGroupDao
) : SyncableEntity<SupersetGroup> {

    override val entityName = "SupersetGroups"
    override val displayName = "Superset"
    override val iconRes = R.drawable.ic_rounded_format_list_numbered
    override val entityClass = SupersetGroup::class

    override fun observeAll(): Flow<List<SupersetGroup>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.supersetGroupApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: SupersetGroup) = dao.insertFromServer(item)
    override suspend fun bulkInsertFromServer(items: List<SupersetGroup>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: SupersetGroup) {
        RetrofitInstance.supersetGroupApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<SupersetGroup>) {
        RetrofitInstance.supersetGroupApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: SupersetGroup) {
        RetrofitInstance.supersetGroupApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: SupersetGroup) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: SupersetGroup) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: SupersetGroup) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: SupersetGroup) = dao.delete(item)

    override fun keyOf(item: SupersetGroup) = item.uuid
    override fun updatedAtOf(item: SupersetGroup) = item.updatedAt
    override fun syncedOf(item: SupersetGroup) = item.synced
    override fun pendingDeletionOf(item: SupersetGroup) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
