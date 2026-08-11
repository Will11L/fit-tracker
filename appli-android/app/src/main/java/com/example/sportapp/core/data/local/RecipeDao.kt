package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.Recipe
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes ORDER BY name ASC")
    fun observeAll(): Flow<List<Recipe>>

    @RawQuery(observedEntities = [Recipe::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, Recipe>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<Recipe>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM recipes WHERE uuid = :uuid")
    suspend fun getByUUID(uuid: String): Recipe?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recipe: Recipe) {
        val now = getNowISO8601()
        insertInternal(recipe.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recipes: List<Recipe>) {
        val now = getNowISO8601()
        insertAllInternal(recipes.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun update(recipe: Recipe) {
        val now = getNowISO8601()
        updateInternal(recipe.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(recipe: Recipe)

    // 🔁 Synchronisation
    @Query("SELECT * FROM recipes WHERE synced = 0")
    suspend fun getAllUnsynced(): List<Recipe>

    @Query("UPDATE recipes SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE recipes SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM recipes WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE recipes SET pendingDeletion = 1, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM recipes WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<Recipe>

    @Query("DELETE FROM recipes")
    suspend fun clearAll()

    @Query("SELECT * FROM recipes")
    suspend fun getAllOnce(): List<Recipe>

    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(recipe: Recipe)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(recipes: List<Recipe>)

    @Update
    suspend fun updateInternal(recipe: Recipe)

    // --- depuis serveur (respect payload tel quel)
    suspend fun insertFromServer(recipe: Recipe) =
        insertInternal(recipe.copy(synced = true, pendingDeletion = false))
    suspend fun insertAllFromServer(recipes: List<Recipe>) =
        insertAllInternal(recipes.map { it.copy(synced = true, pendingDeletion = false) })
    suspend fun updateFromServer(recipe: Recipe) = updateInternal(recipe)
}
