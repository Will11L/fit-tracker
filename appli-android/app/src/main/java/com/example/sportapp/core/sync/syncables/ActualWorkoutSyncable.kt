package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.ActualWorkoutDao
import com.example.sportapp.core.data.model.ActualWorkout
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class ActualWorkoutSyncable(
    private val dao: ActualWorkoutDao
) : SyncableEntity<ActualWorkout> {

    override val entityName = "ActualWorkouts"
    override val displayName = "Workout"
    override val iconRes = R.drawable.ic_rounded_list_alt_check
    override val entityClass = ActualWorkout::class

    override fun observeAll(): Flow<List<ActualWorkout>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.actualWorkoutApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: ActualWorkout) = dao.insertFromServer(item)
    override suspend fun bulkInsertFromServer(items: List<ActualWorkout>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: ActualWorkout) {
        RetrofitInstance.actualWorkoutApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<ActualWorkout>) {
        RetrofitInstance.actualWorkoutApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: ActualWorkout) {
        RetrofitInstance.actualWorkoutApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: ActualWorkout) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: ActualWorkout) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: ActualWorkout) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: ActualWorkout) = dao.delete(item)

    override fun keyOf(item: ActualWorkout) = item.uuid
    override fun updatedAtOf(item: ActualWorkout) = item.updatedAt
    override fun syncedOf(item: ActualWorkout) = item.synced
    override fun pendingDeletionOf(item: ActualWorkout) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
