package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.ExerciseEquipmentDao
import com.example.sportapp.core.data.model.ExerciseEquipment
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class ExerciseEquipmentSyncable(
    private val dao: ExerciseEquipmentDao
) : SyncableEntity<ExerciseEquipment> {

    override val entityName = "ExerciseEquipment"
    override val displayName = "Exercise Equipment"
    override val iconRes = R.drawable.ic_rounded_add_link
    override val entityClass = ExerciseEquipment::class

    override fun observeAll(): Flow<List<ExerciseEquipment>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.exerciseEquipmentApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: ExerciseEquipment) = dao.insertFromServer(item)
    override suspend fun bulkInsertFromServer(items: List<ExerciseEquipment>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: ExerciseEquipment) {
        RetrofitInstance.exerciseEquipmentApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<ExerciseEquipment>) {
        RetrofitInstance.exerciseEquipmentApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: ExerciseEquipment) {
        RetrofitInstance.exerciseEquipmentApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: ExerciseEquipment) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: ExerciseEquipment) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: ExerciseEquipment) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: ExerciseEquipment) = dao.delete(item)

    override fun keyOf(item: ExerciseEquipment) = item.uuid
    override fun updatedAtOf(item: ExerciseEquipment) = item.updatedAt
    override fun syncedOf(item: ExerciseEquipment) = item.synced
    override fun pendingDeletionOf(item: ExerciseEquipment) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
