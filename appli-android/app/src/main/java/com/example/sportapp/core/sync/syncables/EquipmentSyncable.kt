package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.EquipmentDao
import com.example.sportapp.core.data.model.Equipment
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class EquipmentSyncable(
    private val dao: EquipmentDao
) : SyncableEntity<Equipment> {

    override val entityName = "Equipments"
    override val displayName = "Equipment"
    override val iconRes = R.drawable.ic_exercise
    override val entityClass = Equipment::class

    override fun observeAll(): Flow<List<Equipment>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.equipmentApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: Equipment) = dao.insertFromServer(item)
    override suspend fun bulkInsertFromServer(items: List<Equipment>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: Equipment) {
        RetrofitInstance.equipmentApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<Equipment>) {
        RetrofitInstance.equipmentApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: Equipment) {
        RetrofitInstance.equipmentApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: Equipment) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: Equipment) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: Equipment) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: Equipment) = dao.delete(item)

    override fun keyOf(item: Equipment) = item.uuid
    override fun updatedAtOf(item: Equipment) = item.updatedAt
    override fun syncedOf(item: Equipment) = item.synced
    override fun pendingDeletionOf(item: Equipment) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
