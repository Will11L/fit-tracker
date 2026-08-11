package com.example.sportapp.core.sync.syncables

import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.RecipeDao
import com.example.sportapp.core.data.model.Recipe
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class RecipeSyncable(
    private val dao: RecipeDao
) : SyncableEntity<Recipe> {

    override val entityName = "Recipes"
    override val displayName = "Recipe"
    override val iconRes = R.drawable.ic_rounded_book
    override val entityClass = Recipe::class

    override fun observeAll(): Flow<List<Recipe>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.recipeApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: Recipe) { dao.insertFromServer(item) }
    override suspend fun bulkInsertFromServer(items: List<Recipe>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: Recipe) { RetrofitInstance.recipeApi.upsert(item.uuid, item) }
    override suspend fun upsertBulk(items: List<Recipe>) { RetrofitInstance.recipeApi.upsertAll(items) }
    override suspend fun deleteRemote(item: Recipe) { RetrofitInstance.recipeApi.delete(item.uuid) }

    override suspend fun markAsSynced(item: Recipe) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: Recipe) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: Recipe) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: Recipe) = dao.delete(item)

    override fun keyOf(item: Recipe) = item.uuid
    override fun updatedAtOf(item: Recipe) = item.updatedAt
    override fun syncedOf(item: Recipe) = item.synced
    override fun pendingDeletionOf(item: Recipe) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
