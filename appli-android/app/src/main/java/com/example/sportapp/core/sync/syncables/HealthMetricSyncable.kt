package com.example.sportapp.core.sync.syncables

import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.HealthMetricDao
import com.example.sportapp.core.data.model.HealthMetric
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class HealthMetricSyncable(
    private val dao: HealthMetricDao
) : SyncableEntity<HealthMetric> {

    override val entityName = "HealthMetrics"
    override val displayName = "Health Metric"
    override val iconRes = R.drawable.ic_rounded_local_fire
    override val entityClass = HealthMetric::class

    override fun observeAll(): Flow<List<HealthMetric>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.healthMetricApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: HealthMetric) { dao.insertFromServer(item) }
    override suspend fun bulkInsertFromServer(items: List<HealthMetric>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: HealthMetric) { RetrofitInstance.healthMetricApi.upsert(item.uuid, item) }
    override suspend fun upsertBulk(items: List<HealthMetric>) { RetrofitInstance.healthMetricApi.upsertAll(items) }
    override suspend fun deleteRemote(item: HealthMetric) { RetrofitInstance.healthMetricApi.delete(item.uuid) }

    override suspend fun markAsSynced(item: HealthMetric) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: HealthMetric) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: HealthMetric) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: HealthMetric) = dao.delete(item)

    override fun keyOf(item: HealthMetric) = item.uuid
    override fun updatedAtOf(item: HealthMetric) = item.updatedAt
    override fun syncedOf(item: HealthMetric) = item.synced
    override fun pendingDeletionOf(item: HealthMetric) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
