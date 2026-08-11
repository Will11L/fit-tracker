package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.PlannedWorkoutDao
import com.example.sportapp.core.data.model.PlannedWorkout
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class PlannedWorkoutSyncable(
    private val dao: PlannedWorkoutDao
) : SyncableEntity<PlannedWorkout> {

    override val entityName = "PlannedWorkouts"
    override val displayName = "Planned Workout"
    override val iconRes = R.drawable.ic_rounded_list_alt
    override val entityClass = PlannedWorkout::class

    override fun observeAll(): Flow<List<PlannedWorkout>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.plannedWorkoutApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: PlannedWorkout) = dao.insertFromServer(item)
    override suspend fun bulkInsertFromServer(items: List<PlannedWorkout>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: PlannedWorkout) {
        RetrofitInstance.plannedWorkoutApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<PlannedWorkout>) {
        RetrofitInstance.plannedWorkoutApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: PlannedWorkout) {
        RetrofitInstance.plannedWorkoutApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: PlannedWorkout) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: PlannedWorkout) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: PlannedWorkout) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: PlannedWorkout) = dao.delete(item)

    override fun keyOf(item: PlannedWorkout) = item.uuid
    override fun updatedAtOf(item: PlannedWorkout) = item.updatedAt
    override fun syncedOf(item: PlannedWorkout) = item.synced
    override fun pendingDeletionOf(item: PlannedWorkout) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
