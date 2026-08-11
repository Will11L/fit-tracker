package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.MuscleGoalDao
import com.example.sportapp.core.data.model.MuscleGoal
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class MuscleGoalSyncable(
    private val dao: MuscleGoalDao
) : SyncableEntity<MuscleGoal> {

    override val entityName = "MuscleGoals"
    override val displayName = "Muscle Goal"
    override val iconRes = R.drawable.ic_arrow_progress
    override val entityClass = MuscleGoal::class

    override fun observeAll(): Flow<List<MuscleGoal>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.muscleGoalApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: MuscleGoal) { dao.insertFromServer(item) }
    override suspend fun bulkInsertFromServer(items: List<MuscleGoal>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: MuscleGoal) {
        // MuscleGoal indexé par muscleUUID côté serveur (1 goal par muscle/user).
        RetrofitInstance.muscleGoalApi.upsert(item.muscleUUID, item)
    }
    override suspend fun upsertBulk(items: List<MuscleGoal>) {
        RetrofitInstance.muscleGoalApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: MuscleGoal) {
        RetrofitInstance.muscleGoalApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: MuscleGoal) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: MuscleGoal) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: MuscleGoal) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: MuscleGoal) = dao.delete(item)

    override fun keyOf(item: MuscleGoal) = item.uuid
    override fun updatedAtOf(item: MuscleGoal) = item.updatedAt
    override fun syncedOf(item: MuscleGoal) = item.synced
    override fun pendingDeletionOf(item: MuscleGoal) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
