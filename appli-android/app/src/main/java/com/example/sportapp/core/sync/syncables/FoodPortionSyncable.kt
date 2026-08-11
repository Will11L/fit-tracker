package com.example.sportapp.core.sync.syncables

import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.FoodPortionDao
import com.example.sportapp.core.data.model.FoodPortion
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class FoodPortionSyncable(
    private val dao: FoodPortionDao
) : SyncableEntity<FoodPortion> {

    override val entityName = "FoodPortions"
    override val displayName = "Food Portion"
    override val iconRes = R.drawable.ic_rounded_list_alt
    override val entityClass = FoodPortion::class

    override fun observeAll(): Flow<List<FoodPortion>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.foodPortionApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: FoodPortion) { dao.insertFromServer(item) }
    override suspend fun bulkInsertFromServer(items: List<FoodPortion>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: FoodPortion) { RetrofitInstance.foodPortionApi.upsert(item.uuid, item) }
    override suspend fun upsertBulk(items: List<FoodPortion>) { RetrofitInstance.foodPortionApi.upsertAll(items) }
    override suspend fun deleteRemote(item: FoodPortion) { RetrofitInstance.foodPortionApi.delete(item.uuid) }

    override suspend fun markAsSynced(item: FoodPortion) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: FoodPortion) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: FoodPortion) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: FoodPortion) = dao.delete(item)

    override fun keyOf(item: FoodPortion) = item.uuid
    override fun updatedAtOf(item: FoodPortion) = item.updatedAt
    override fun syncedOf(item: FoodPortion) = item.synced
    override fun pendingDeletionOf(item: FoodPortion) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
