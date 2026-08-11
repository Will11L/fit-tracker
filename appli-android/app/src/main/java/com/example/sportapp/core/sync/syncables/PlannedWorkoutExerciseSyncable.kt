package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.PlannedWorkoutExerciseDao
import com.example.sportapp.core.data.model.PlannedWorkoutExercise
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class PlannedWorkoutExerciseSyncable(
    private val dao: PlannedWorkoutExerciseDao
) : SyncableEntity<PlannedWorkoutExercise> {

    override val entityName = "PlannedWorkoutExercises"
    override val displayName = "Planned Exercise"
    override val iconRes = R.drawable.ic_exercise
    override val entityClass = PlannedWorkoutExercise::class

    override fun observeAll(): Flow<List<PlannedWorkoutExercise>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.plannedWorkoutExerciseApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: PlannedWorkoutExercise) = dao.insertFromServer(item)
    override suspend fun bulkInsertFromServer(items: List<PlannedWorkoutExercise>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: PlannedWorkoutExercise) {
        RetrofitInstance.plannedWorkoutExerciseApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<PlannedWorkoutExercise>) {
        RetrofitInstance.plannedWorkoutExerciseApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: PlannedWorkoutExercise) {
        RetrofitInstance.plannedWorkoutExerciseApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: PlannedWorkoutExercise) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: PlannedWorkoutExercise) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: PlannedWorkoutExercise) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: PlannedWorkoutExercise) = dao.delete(item)

    override fun keyOf(item: PlannedWorkoutExercise) = item.uuid
    override fun updatedAtOf(item: PlannedWorkoutExercise) = item.updatedAt
    override fun syncedOf(item: PlannedWorkoutExercise) = item.synced
    override fun pendingDeletionOf(item: PlannedWorkoutExercise) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
