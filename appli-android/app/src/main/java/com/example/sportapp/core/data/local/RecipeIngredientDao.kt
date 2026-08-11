package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.RecipeIngredient
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeIngredientDao {

    @Query("SELECT * FROM recipe_ingredients ORDER BY order_index ASC")
    fun observeAll(): Flow<List<RecipeIngredient>>

    @RawQuery(observedEntities = [RecipeIngredient::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, RecipeIngredient>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<RecipeIngredient>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM recipe_ingredients WHERE uuid = :uuid")
    suspend fun getByUUID(uuid: String): RecipeIngredient?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ingredient: RecipeIngredient) {
        val now = getNowISO8601()
        insertInternal(ingredient.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(ingredients: List<RecipeIngredient>) {
        val now = getNowISO8601()
        insertAllInternal(ingredients.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun update(ingredient: RecipeIngredient) {
        val now = getNowISO8601()
        updateInternal(ingredient.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(ingredient: RecipeIngredient)

    // 🔁 Synchronisation
    @Query("SELECT * FROM recipe_ingredients WHERE synced = 0")
    suspend fun getAllUnsynced(): List<RecipeIngredient>

    @Query("UPDATE recipe_ingredients SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE recipe_ingredients SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM recipe_ingredients WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE recipe_ingredients SET pendingDeletion = 1, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM recipe_ingredients WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<RecipeIngredient>

    @Query("DELETE FROM recipe_ingredients")
    suspend fun clearAll()

    @Query("SELECT * FROM recipe_ingredients")
    suspend fun getAllOnce(): List<RecipeIngredient>

    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(ingredient: RecipeIngredient)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(ingredients: List<RecipeIngredient>)

    @Update
    suspend fun updateInternal(ingredient: RecipeIngredient)

    // --- depuis serveur (respect payload tel quel)
    suspend fun insertFromServer(ingredient: RecipeIngredient) =
        insertInternal(ingredient.copy(synced = true, pendingDeletion = false))
    suspend fun insertAllFromServer(ingredients: List<RecipeIngredient>) =
        insertAllInternal(ingredients.map { it.copy(synced = true, pendingDeletion = false) })
    suspend fun updateFromServer(ingredient: RecipeIngredient) = updateInternal(ingredient)
}
