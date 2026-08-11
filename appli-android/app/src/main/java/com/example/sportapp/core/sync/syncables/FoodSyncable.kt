package com.example.sportapp.core.sync.syncables

import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.FoodDao
import com.example.sportapp.core.data.model.Food
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class FoodSyncable(
    private val dao: FoodDao
) : SyncableEntity<Food> {

    override val entityName = "Foods"
    override val displayName = "Food"
    override val iconRes = R.drawable.ic_rounded_local_fire
    override val entityClass = Food::class

    override fun observeAll(): Flow<List<Food>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.foodApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: Food) { dao.insertFromServer(item) }
    override suspend fun bulkInsertFromServer(items: List<Food>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: Food) { RetrofitInstance.foodApi.upsert(item.uuid, item) }
    override suspend fun upsertBulk(items: List<Food>) { RetrofitInstance.foodApi.upsertAll(items) }
    override suspend fun deleteRemote(item: Food) { RetrofitInstance.foodApi.delete(item.uuid) }

    override suspend fun markAsSynced(item: Food) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: Food) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: Food) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: Food) = dao.delete(item)

    override fun keyOf(item: Food) = item.uuid
    override fun updatedAtOf(item: Food) = item.updatedAt
    override fun syncedOf(item: Food) = item.synced
    override fun pendingDeletionOf(item: Food) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
