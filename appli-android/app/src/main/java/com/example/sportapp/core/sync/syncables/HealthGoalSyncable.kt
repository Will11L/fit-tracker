package com.example.sportapp.core.sync.syncables

import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.HealthGoalDao
import com.example.sportapp.core.data.model.HealthGoal
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class HealthGoalSyncable(
    private val dao: HealthGoalDao
) : SyncableEntity<HealthGoal> {

    override val entityName = "HealthGoals"
    override val displayName = "Health Goal"
    override val iconRes = R.drawable.ic_rounded_flag
    override val entityClass = HealthGoal::class

    override fun observeAll(): Flow<List<HealthGoal>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.healthGoalApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: HealthGoal) { dao.insertFromServer(item) }
    override suspend fun bulkInsertFromServer(items: List<HealthGoal>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: HealthGoal) { RetrofitInstance.healthGoalApi.upsert(item.uuid, item) }
    override suspend fun upsertBulk(items: List<HealthGoal>) { RetrofitInstance.healthGoalApi.upsertAll(items) }
    override suspend fun deleteRemote(item: HealthGoal) { RetrofitInstance.healthGoalApi.delete(item.uuid) }

    override suspend fun markAsSynced(item: HealthGoal) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: HealthGoal) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: HealthGoal) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: HealthGoal) = dao.delete(item)

    override fun keyOf(item: HealthGoal) = item.uuid
    override fun updatedAtOf(item: HealthGoal) = item.updatedAt
    override fun syncedOf(item: HealthGoal) = item.synced
    override fun pendingDeletionOf(item: HealthGoal) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
