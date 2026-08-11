package com.example.sportapp.core.sync.syncables

import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.HealthStepCountDao
import com.example.sportapp.core.data.model.HealthStepCount
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class HealthStepCountSyncable(
    private val dao: HealthStepCountDao
) : SyncableEntity<HealthStepCount> {

    override val entityName = "HealthStepCounts"
    override val displayName = "Health Step Count"
    override val iconRes = R.drawable.ic_rounded_monitoring
    override val entityClass = HealthStepCount::class

    override fun observeAll(): Flow<List<HealthStepCount>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.healthStepCountApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: HealthStepCount) { dao.insertFromServer(item) }
    override suspend fun bulkInsertFromServer(items: List<HealthStepCount>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: HealthStepCount) { RetrofitInstance.healthStepCountApi.upsert(item.uuid, item) }
    override suspend fun upsertBulk(items: List<HealthStepCount>) { RetrofitInstance.healthStepCountApi.upsertAll(items) }
    override suspend fun deleteRemote(item: HealthStepCount) { RetrofitInstance.healthStepCountApi.delete(item.uuid) }

    override suspend fun markAsSynced(item: HealthStepCount) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: HealthStepCount) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: HealthStepCount) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: HealthStepCount) = dao.delete(item)

    override fun keyOf(item: HealthStepCount) = item.uuid
    override fun updatedAtOf(item: HealthStepCount) = item.updatedAt
    override fun syncedOf(item: HealthStepCount) = item.synced
    override fun pendingDeletionOf(item: HealthStepCount) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
