package com.example.sportapp.core.sync.syncables

import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.RecipeIngredientDao
import com.example.sportapp.core.data.model.RecipeIngredient
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class RecipeIngredientSyncable(
    private val dao: RecipeIngredientDao
) : SyncableEntity<RecipeIngredient> {

    override val entityName = "RecipeIngredients"
    override val displayName = "Recipe Ingredient"
    override val iconRes = R.drawable.ic_rounded_format_list_numbered
    override val entityClass = RecipeIngredient::class

    override fun observeAll(): Flow<List<RecipeIngredient>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.recipeIngredientApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: RecipeIngredient) { dao.insertFromServer(item) }
    override suspend fun bulkInsertFromServer(items: List<RecipeIngredient>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: RecipeIngredient) { RetrofitInstance.recipeIngredientApi.upsert(item.uuid, item) }
    override suspend fun upsertBulk(items: List<RecipeIngredient>) { RetrofitInstance.recipeIngredientApi.upsertAll(items) }
    override suspend fun deleteRemote(item: RecipeIngredient) { RetrofitInstance.recipeIngredientApi.delete(item.uuid) }

    override suspend fun markAsSynced(item: RecipeIngredient) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: RecipeIngredient) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: RecipeIngredient) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: RecipeIngredient) = dao.delete(item)

    override fun keyOf(item: RecipeIngredient) = item.uuid
    override fun updatedAtOf(item: RecipeIngredient) = item.updatedAt
    override fun syncedOf(item: RecipeIngredient) = item.synced
    override fun pendingDeletionOf(item: RecipeIngredient) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
