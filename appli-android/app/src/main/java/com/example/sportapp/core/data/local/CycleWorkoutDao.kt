package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.CycleWorkout
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleWorkoutDao {

    @Query("SELECT * FROM cycle_workouts")
    fun observeAll(): Flow<List<CycleWorkout>>

    @RawQuery(observedEntities = [CycleWorkout::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, CycleWorkout>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<CycleWorkout>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("""
        SELECT * FROM cycle_workouts 
        WHERE training_cycle_uuid = :cycleUUID AND planned_workout_uuid = :workoutUUID
    """)
    suspend fun getCycleWorkoutByUUIDs(cycleUUID: String, workoutUUID: String): CycleWorkout?

    @Query("SELECT * FROM cycle_workouts WHERE uuid = :uuid")
    suspend fun getCycleWorkoutByUUID(uuid: String): CycleWorkout?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cycleWorkout: CycleWorkout) {
        val now = getNowISO8601()
        insertInternal(cycleWorkout.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cycleWorkouts: List<CycleWorkout>) {
        val now = getNowISO8601()
        insertAllInternal(cycleWorkouts.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun updateCycleWorkout(cycleWorkout: CycleWorkout) {
        val now = getNowISO8601()
        updateInternal(cycleWorkout.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(cycleWorkout: CycleWorkout)


    // 🔁 Synchronisation
    @Query("SELECT * FROM cycle_workouts WHERE synced = 0")
    suspend fun getAllUnsynced(): List<CycleWorkout>

    @Query("UPDATE cycle_workouts SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE cycle_workouts SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM cycle_workouts WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    suspend fun markAsPendingDeletion(uuid: String) {
        val now = getNowISO8601()
        markAsPendingDeletionInternal(uuid, now)
    }

    @Query("SELECT * FROM cycle_workouts WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<CycleWorkout>

    @Query("DELETE FROM cycle_workouts")
    suspend fun clearAll()

    @Query("SELECT * FROM cycle_workouts")
    suspend fun getAllOnce(): List<CycleWorkout>


    // -- internes (non modifiées par l'appelant, juste utilisées dans les surcharges ci-dessus)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(cycleWorkout: CycleWorkout)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(cycleWorkouts: List<CycleWorkout>)

    @Update
    suspend fun updateInternal(cycleWorkout: CycleWorkout)

    @Query("UPDATE cycle_workouts SET pendingDeletion = 1, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun markAsPendingDeletionInternal(uuid: String, updatedAt: String)

    // --- Insertion depuis le serveur (respecte payload tel quel) ---
    suspend fun insertFromServer(cycleWorkout: CycleWorkout) {
        insertInternal(cycleWorkout.copy(synced = true, pendingDeletion = false))
    }

    suspend fun insertAllFromServer(cycleWorkouts: List<CycleWorkout>) {
        insertAllInternal(cycleWorkouts.map { it.copy(synced = true, pendingDeletion = false) })
    }

    suspend fun updateFromServer(cycleWorkout: CycleWorkout) {
        updateInternal(cycleWorkout)
    }
}
