package com.example.sportapp.core.sync.syncables

import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.WaterIntakeDao
import com.example.sportapp.core.data.model.WaterIntake
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class WaterIntakeSyncable(
    private val dao: WaterIntakeDao
) : SyncableEntity<WaterIntake> {

    override val entityName = "WaterIntakes"
    override val displayName = "Water Intake"
    override val iconRes = R.drawable.ic_rounded_water_drop
    override val entityClass = WaterIntake::class

    override fun observeAll(): Flow<List<WaterIntake>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.waterIntakeApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: WaterIntake) { dao.insertFromServer(item) }
    override suspend fun bulkInsertFromServer(items: List<WaterIntake>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: WaterIntake) { RetrofitInstance.waterIntakeApi.upsert(item.uuid, item) }
    override suspend fun upsertBulk(items: List<WaterIntake>) { RetrofitInstance.waterIntakeApi.upsertAll(items) }
    override suspend fun deleteRemote(item: WaterIntake) { RetrofitInstance.waterIntakeApi.delete(item.uuid) }

    override suspend fun markAsSynced(item: WaterIntake) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: WaterIntake) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: WaterIntake) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: WaterIntake) = dao.delete(item)

    override fun keyOf(item: WaterIntake) = item.uuid
    override fun updatedAtOf(item: WaterIntake) = item.updatedAt
    override fun syncedOf(item: WaterIntake) = item.synced
    override fun pendingDeletionOf(item: WaterIntake) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
