package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.SupersetExerciseDao
import com.example.sportapp.core.data.model.SupersetExercise
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class SupersetExerciseSyncable(
    private val dao: SupersetExerciseDao
) : SyncableEntity<SupersetExercise> {

    override val entityName = "SupersetExercises"
    override val displayName = "Superset Exercise"
    override val iconRes = R.drawable.ic_exercise
    override val entityClass = SupersetExercise::class

    override fun observeAll(): Flow<List<SupersetExercise>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.supersetExerciseApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: SupersetExercise) = dao.insertFromServer(item)
    override suspend fun bulkInsertFromServer(items: List<SupersetExercise>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: SupersetExercise) {
        RetrofitInstance.supersetExerciseApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<SupersetExercise>) {
        RetrofitInstance.supersetExerciseApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: SupersetExercise) {
        RetrofitInstance.supersetExerciseApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: SupersetExercise) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: SupersetExercise) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: SupersetExercise) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: SupersetExercise) = dao.delete(item)

    override fun keyOf(item: SupersetExercise) = item.uuid
    override fun updatedAtOf(item: SupersetExercise) = item.updatedAt
    override fun syncedOf(item: SupersetExercise) = item.synced
    override fun pendingDeletionOf(item: SupersetExercise) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
