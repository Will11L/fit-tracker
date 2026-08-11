package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.ExerciseMuscleDao
import com.example.sportapp.core.data.model.ExerciseMuscle
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class ExerciseMuscleSyncable(
    private val dao: ExerciseMuscleDao
) : SyncableEntity<ExerciseMuscle> {

    override val entityName = "ExerciseMuscles"
    override val displayName = "Exercise Muscle"
    override val iconRes = R.drawable.ic_rounded_neurology
    override val entityClass = ExerciseMuscle::class

    override fun observeAll(): Flow<List<ExerciseMuscle>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.exerciseMuscleApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: ExerciseMuscle) = dao.insertFromServer(item)
    override suspend fun bulkInsertFromServer(items: List<ExerciseMuscle>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: ExerciseMuscle) {
        RetrofitInstance.exerciseMuscleApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<ExerciseMuscle>) {
        RetrofitInstance.exerciseMuscleApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: ExerciseMuscle) {
        RetrofitInstance.exerciseMuscleApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: ExerciseMuscle) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: ExerciseMuscle) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: ExerciseMuscle) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: ExerciseMuscle) = dao.delete(item)

    override fun keyOf(item: ExerciseMuscle) = item.uuid
    override fun updatedAtOf(item: ExerciseMuscle) = item.updatedAt
    override fun syncedOf(item: ExerciseMuscle) = item.synced
    override fun pendingDeletionOf(item: ExerciseMuscle) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
