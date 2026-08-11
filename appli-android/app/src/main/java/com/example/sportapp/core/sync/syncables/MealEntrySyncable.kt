package com.example.sportapp.core.sync.syncables

import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.MealEntryDao
import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class MealEntrySyncable(
    private val dao: MealEntryDao
) : SyncableEntity<MealEntry> {

    override val entityName = "MealEntries"
    override val displayName = "Meal Entry"
    override val iconRes = R.drawable.ic_rounded_notes
    override val entityClass = MealEntry::class

    override fun observeAll(): Flow<List<MealEntry>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.mealEntryApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: MealEntry) { dao.insertFromServer(item) }
    override suspend fun bulkInsertFromServer(items: List<MealEntry>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: MealEntry) { RetrofitInstance.mealEntryApi.upsert(item.uuid, item) }
    override suspend fun upsertBulk(items: List<MealEntry>) { RetrofitInstance.mealEntryApi.upsertAll(items) }
    override suspend fun deleteRemote(item: MealEntry) { RetrofitInstance.mealEntryApi.delete(item.uuid) }

    override suspend fun markAsSynced(item: MealEntry) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: MealEntry) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: MealEntry) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: MealEntry) = dao.delete(item)

    override fun keyOf(item: MealEntry) = item.uuid
    override fun updatedAtOf(item: MealEntry) = item.updatedAt
    override fun syncedOf(item: MealEntry) = item.synced
    override fun pendingDeletionOf(item: MealEntry) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
