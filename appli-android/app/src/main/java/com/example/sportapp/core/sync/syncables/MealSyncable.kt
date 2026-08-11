package com.example.sportapp.core.sync.syncables

import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.MealDao
import com.example.sportapp.core.data.model.Meal
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class MealSyncable(
    private val dao: MealDao
) : SyncableEntity<Meal> {

    override val entityName = "Meals"
    override val displayName = "Meal"
    override val iconRes = R.drawable.ic_rounded_calendar_view_day
    override val entityClass = Meal::class

    override fun observeAll(): Flow<List<Meal>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.mealApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: Meal) { dao.insertFromServer(item) }
    override suspend fun bulkInsertFromServer(items: List<Meal>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: Meal) { RetrofitInstance.mealApi.upsert(item.uuid, item) }
    override suspend fun upsertBulk(items: List<Meal>) { RetrofitInstance.mealApi.upsertAll(items) }
    override suspend fun deleteRemote(item: Meal) { RetrofitInstance.mealApi.delete(item.uuid) }

    override suspend fun markAsSynced(item: Meal) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: Meal) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: Meal) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: Meal) = dao.delete(item)

    override fun keyOf(item: Meal) = item.uuid
    override fun updatedAtOf(item: Meal) = item.updatedAt
    override fun syncedOf(item: Meal) = item.synced
    override fun pendingDeletionOf(item: Meal) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
