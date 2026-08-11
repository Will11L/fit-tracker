package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.AvailableEquipmentDao
import com.example.sportapp.core.data.model.AvailableEquipment
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class AvailableEquipmentSyncable(
    private val dao: AvailableEquipmentDao
) : SyncableEntity<AvailableEquipment> {

    override val entityName = "AvailableEquipments"
    override val displayName = "Available Equipment"
    override val iconRes = R.drawable.ic_exercise
    override val entityClass = AvailableEquipment::class

    override fun observeAll(): Flow<List<AvailableEquipment>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.availableEquipmentApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: AvailableEquipment) = dao.insertFromServer(item)
    override suspend fun bulkInsertFromServer(items: List<AvailableEquipment>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: AvailableEquipment) {
        RetrofitInstance.availableEquipmentApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<AvailableEquipment>) {
        RetrofitInstance.availableEquipmentApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: AvailableEquipment) {
        RetrofitInstance.availableEquipmentApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: AvailableEquipment) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: AvailableEquipment) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: AvailableEquipment) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: AvailableEquipment) = dao.delete(item)

    override fun keyOf(item: AvailableEquipment) = item.uuid
    override fun updatedAtOf(item: AvailableEquipment) = item.updatedAt
    override fun syncedOf(item: AvailableEquipment) = item.synced
    override fun pendingDeletionOf(item: AvailableEquipment) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
