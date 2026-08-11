package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.ActualWorkout
import com.example.sportapp.core.data.model.PlannedWorkout
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedWorkoutDao {

    @Query("SELECT * FROM planned_workouts")
    fun observeAll(): Flow<List<PlannedWorkout>>

    @RawQuery(observedEntities = [PlannedWorkout::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, PlannedWorkout>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<PlannedWorkout>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM planned_workouts WHERE uuid = :uuid")
    fun observeByUUID(uuid: String): Flow<PlannedWorkout?>

    @Query("SELECT * FROM planned_workouts WHERE day_of_week = :dayOfWeek")
    fun observeWorkoutForToday(dayOfWeek: String): Flow<PlannedWorkout?>

    @Query("SELECT * FROM planned_workouts WHERE uuid = :uuid")
    suspend fun getPlannedWorkoutByUUID(uuid: String): PlannedWorkout?

    @Query("SELECT * FROM planned_workouts WHERE day_of_week = :dayOfWeek")
    suspend fun getWorkoutByDay(dayOfWeek: String): PlannedWorkout?

    @Query("""
        SELECT * FROM planned_workouts
        WHERE user_id = :userId AND day_of_week = :dayOfWeek
        LIMIT 1
    """)
    suspend fun getPlannedWorkoutByUserAndDay(
        userId: Int,
        dayOfWeek: String
    ): PlannedWorkout?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workout: PlannedWorkout) {
        val now = getNowISO8601()
        insertInternal(workout.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(workouts: List<PlannedWorkout>) {
        val now = getNowISO8601()
        insertAllInternal(workouts.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun updatePlannedWorkout(workout: PlannedWorkout) {
        val now = getNowISO8601()
        updateInternal(workout.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(workout: PlannedWorkout)


    // 🔁 Synchronisation
    @Query("SELECT * FROM planned_workouts WHERE synced = 0")
    suspend fun getAllUnsynced(): List<PlannedWorkout>

    @Query("UPDATE planned_workouts SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE planned_workouts SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM planned_workouts WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE planned_workouts SET pendingDeletion = 1, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM planned_workouts WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<PlannedWorkout>

    @Query("DELETE FROM planned_workouts")
    suspend fun clearAll()

    @Query("SELECT * FROM planned_workouts")
    suspend fun getAllOnce(): List<PlannedWorkout>


    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(workout: PlannedWorkout)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(workouts: List<PlannedWorkout>)

    @Update
    suspend fun updateInternal(workout: PlannedWorkout)

    // --- Insertion depuis le serveur (respecte payload tel quel) ---
    suspend fun insertFromServer(workout: PlannedWorkout) {
        insertInternal(workout.copy(synced = true, pendingDeletion = false))
    }

    suspend fun insertAllFromServer(workouts: List<PlannedWorkout>) {
        insertAllInternal(workouts.map { it.copy(synced = true, pendingDeletion = false) })
    }

    suspend fun updateFromServer(workout: PlannedWorkout) {
        updateInternal(workout)
    }
}
