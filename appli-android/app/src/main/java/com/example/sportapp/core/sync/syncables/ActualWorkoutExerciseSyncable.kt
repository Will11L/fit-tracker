package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.ActualWorkoutExerciseDao
import com.example.sportapp.core.data.model.ActualWorkoutExercise
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class ActualWorkoutExerciseSyncable(
    private val dao: ActualWorkoutExerciseDao
) : SyncableEntity<ActualWorkoutExercise> {

    override val entityName = "ActualWorkoutExercises"
    override val displayName = "Workout Exercise"
    override val iconRes = R.drawable.ic_exercise
    override val entityClass = ActualWorkoutExercise::class

    override fun observeAll(): Flow<List<ActualWorkoutExercise>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.actualWorkoutExerciseApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: ActualWorkoutExercise) = dao.insertFromServer(item)
    override suspend fun bulkInsertFromServer(items: List<ActualWorkoutExercise>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: ActualWorkoutExercise) {
        RetrofitInstance.actualWorkoutExerciseApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<ActualWorkoutExercise>) {
        RetrofitInstance.actualWorkoutExerciseApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: ActualWorkoutExercise) {
        RetrofitInstance.actualWorkoutExerciseApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: ActualWorkoutExercise) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: ActualWorkoutExercise) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: ActualWorkoutExercise) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: ActualWorkoutExercise) = dao.delete(item)

    override fun keyOf(item: ActualWorkoutExercise) = item.uuid
    override fun updatedAtOf(item: ActualWorkoutExercise) = item.updatedAt
    override fun syncedOf(item: ActualWorkoutExercise) = item.synced
    override fun pendingDeletionOf(item: ActualWorkoutExercise) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
