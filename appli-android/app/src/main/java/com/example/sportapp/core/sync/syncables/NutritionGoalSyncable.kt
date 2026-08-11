package com.example.sportapp.core.sync.syncables

import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.NutritionGoalDao
import com.example.sportapp.core.data.model.NutritionGoal
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class NutritionGoalSyncable(
    private val dao: NutritionGoalDao
) : SyncableEntity<NutritionGoal> {

    override val entityName = "NutritionGoals"
    override val displayName = "Nutrition Goal"
    override val iconRes = R.drawable.ic_rounded_flag
    override val entityClass = NutritionGoal::class

    override fun observeAll(): Flow<List<NutritionGoal>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.nutritionGoalApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: NutritionGoal) { dao.insertFromServer(item) }
    override suspend fun bulkInsertFromServer(items: List<NutritionGoal>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: NutritionGoal) { RetrofitInstance.nutritionGoalApi.upsert(item.uuid, item) }
    override suspend fun upsertBulk(items: List<NutritionGoal>) { RetrofitInstance.nutritionGoalApi.upsertAll(items) }
    override suspend fun deleteRemote(item: NutritionGoal) { RetrofitInstance.nutritionGoalApi.delete(item.uuid) }

    override suspend fun markAsSynced(item: NutritionGoal) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: NutritionGoal) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: NutritionGoal) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: NutritionGoal) = dao.delete(item)

    override fun keyOf(item: NutritionGoal) = item.uuid
    override fun updatedAtOf(item: NutritionGoal) = item.updatedAt
    override fun syncedOf(item: NutritionGoal) = item.synced
    override fun pendingDeletionOf(item: NutritionGoal) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
