package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.NutritionGoal
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface NutritionGoalDao {

    @Query("SELECT * FROM nutrition_goals ORDER BY effective_from DESC")
    fun observeAll(): Flow<List<NutritionGoal>>

    @RawQuery(observedEntities = [NutritionGoal::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, NutritionGoal>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<NutritionGoal>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM nutrition_goals WHERE uuid = :uuid")
    suspend fun getByUUID(uuid: String): NutritionGoal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: NutritionGoal) {
        val now = getNowISO8601()
        insertInternal(goal.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<NutritionGoal>) {
        val now = getNowISO8601()
        insertAllInternal(goals.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun update(goal: NutritionGoal) {
        val now = getNowISO8601()
        updateInternal(goal.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(goal: NutritionGoal)

    // 🔁 Synchronisation
    @Query("SELECT * FROM nutrition_goals WHERE synced = 0")
    suspend fun getAllUnsynced(): List<NutritionGoal>

    @Query("UPDATE nutrition_goals SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE nutrition_goals SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM nutrition_goals WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE nutrition_goals SET pendingDeletion = 1, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM nutrition_goals WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<NutritionGoal>

    @Query("DELETE FROM nutrition_goals")
    suspend fun clearAll()

    @Query("SELECT * FROM nutrition_goals")
    suspend fun getAllOnce(): List<NutritionGoal>

    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(goal: NutritionGoal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(goals: List<NutritionGoal>)

    @Update
    suspend fun updateInternal(goal: NutritionGoal)

    // --- depuis serveur (respect payload tel quel)
    suspend fun insertFromServer(goal: NutritionGoal) =
        insertInternal(goal.copy(synced = true, pendingDeletion = false))
    suspend fun insertAllFromServer(goals: List<NutritionGoal>) =
        insertAllInternal(goals.map { it.copy(synced = true, pendingDeletion = false) })
    suspend fun updateFromServer(goal: NutritionGoal) = updateInternal(goal)
}
