package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.ExerciseDao
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class ExerciseSyncable(
    private val dao: ExerciseDao
) : SyncableEntity<Exercise> {

    override val entityName = "Exercises"
    override val displayName = "Exercise"
    override val iconRes = R.drawable.ic_exercise
    override val entityClass = Exercise::class

    override fun observeAll(): Flow<List<Exercise>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.exerciseApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: Exercise) = dao.insertFromServer(item)
    override suspend fun bulkInsertFromServer(items: List<Exercise>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: Exercise) {
        RetrofitInstance.exerciseApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<Exercise>) {
        RetrofitInstance.exerciseApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: Exercise) {
        RetrofitInstance.exerciseApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: Exercise) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: Exercise) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: Exercise) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: Exercise) = dao.delete(item)

    override fun keyOf(item: Exercise) = item.uuid
    override fun updatedAtOf(item: Exercise) = item.updatedAt
    override fun syncedOf(item: Exercise) = item.synced
    override fun pendingDeletionOf(item: Exercise) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
