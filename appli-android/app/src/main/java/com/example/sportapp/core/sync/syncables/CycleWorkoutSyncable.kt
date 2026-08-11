package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.CycleWorkoutDao
import com.example.sportapp.core.data.model.CycleWorkout
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class CycleWorkoutSyncable(
    private val dao: CycleWorkoutDao
) : SyncableEntity<CycleWorkout> {

    override val entityName = "CycleWorkouts"
    override val displayName = "Cycle Workout"
    override val iconRes = R.drawable.ic_rounded_list_alt
    override val entityClass = CycleWorkout::class

    override fun observeAll(): Flow<List<CycleWorkout>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.cycleWorkoutApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: CycleWorkout) = dao.insertFromServer(item)
    override suspend fun bulkInsertFromServer(items: List<CycleWorkout>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: CycleWorkout) {
        RetrofitInstance.cycleWorkoutApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<CycleWorkout>) {
        RetrofitInstance.cycleWorkoutApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: CycleWorkout) {
        RetrofitInstance.cycleWorkoutApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: CycleWorkout) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: CycleWorkout) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: CycleWorkout) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: CycleWorkout) = dao.delete(item)

    override fun keyOf(item: CycleWorkout) = item.uuid
    override fun updatedAtOf(item: CycleWorkout) = item.updatedAt
    override fun syncedOf(item: CycleWorkout) = item.synced
    override fun pendingDeletionOf(item: CycleWorkout) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
