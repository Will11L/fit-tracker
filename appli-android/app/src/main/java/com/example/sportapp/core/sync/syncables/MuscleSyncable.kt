package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.MuscleDao
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class MuscleSyncable(
    private val dao: MuscleDao
) : SyncableEntity<Muscle> {

    override val entityName = "Muscles"
    override val displayName = "Muscle"
    override val iconRes = R.drawable.ic_rounded_neurology
    override val entityClass = Muscle::class

    override fun observeAll(): Flow<List<Muscle>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.muscleApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: Muscle) = dao.insertFromServer(item)
    override suspend fun bulkInsertFromServer(items: List<Muscle>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: Muscle) {
        RetrofitInstance.muscleApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<Muscle>) {
        RetrofitInstance.muscleApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: Muscle) {
        RetrofitInstance.muscleApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: Muscle) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: Muscle) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: Muscle) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: Muscle) = dao.delete(item)

    override fun keyOf(item: Muscle) = item.uuid
    override fun updatedAtOf(item: Muscle) = item.updatedAt
    override fun syncedOf(item: Muscle) = item.synced
    override fun pendingDeletionOf(item: Muscle) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
