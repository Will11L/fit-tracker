package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.ActualWorkout
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface ActualWorkoutDao {

    // --- RAW methods (utilisées en interne uniquement)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(actualWorkout: ActualWorkout)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(actualWorkouts: List<ActualWorkout>)

    @Update
    suspend fun updateInternal(actualWorkout: ActualWorkout)

    @Query("UPDATE actual_workouts SET pendingDeletion = 1, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun markAsPendingDeletionInternal(uuid: String, updatedAt: String)

    // --- Public methods (auto inject updatedAt)
    suspend fun insert(actualWorkout: ActualWorkout) {
        insertInternal(actualWorkout.copy(
            updatedAt = getNowISO8601(),
            synced = false
        ))
    }

    suspend fun insertAll(actualWorkouts: List<ActualWorkout>) {
        val now = getNowISO8601()
        insertAllInternal(actualWorkouts.map {
            it.copy(updatedAt = now, synced = false)
        })
    }

    suspend fun updateActualWorkout(actualWorkout: ActualWorkout) {
        updateInternal(actualWorkout.copy(
            updatedAt = getNowISO8601(),
            synced = false
        ))
    }

    suspend fun markAsPendingDeletion(uuid: String) {
        markAsPendingDeletionInternal(uuid, getNowISO8601())
    }

    // --- Lecture (inchangée)
    @Query("SELECT * FROM actual_workouts")
    fun observeAll(): Flow<List<ActualWorkout>>

    @RawQuery(observedEntities = [ActualWorkout::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, ActualWorkout>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<ActualWorkout>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM actual_workouts WHERE uuid = :uuid LIMIT 1")
    fun observeByUUID(uuid: String): Flow<ActualWorkout?>

    @Query("""
        SELECT * FROM actual_workouts
        WHERE date LIKE :day || '%'
          AND pendingDeletion = 0
        LIMIT 1
    """)
    fun observeActualWorkoutByDay(day: String): Flow<ActualWorkout?>

    @Query("""
        SELECT aw.* FROM actual_workouts aw
        WHERE substr(aw.date, 1, 10) BETWEEN :start AND :end
    """)
    fun observeActualWorkoutsForWeek(start: String, end: String): Flow<List<ActualWorkout>>

    @Query("""
        SELECT * FROM actual_workouts
        WHERE substr(date, 1, 7) = :yearMonth
        ORDER BY date ASC
    """)
    fun observeActualWorkoutsForMonth(yearMonth: String): Flow<List<ActualWorkout>>

    @Query("""
        SELECT aw.* FROM actual_workouts aw
        WHERE substr(aw.date, 1, 10) BETWEEN :start AND :end
        ORDER BY aw.date ASC
    """)
    fun observeActualWorkoutsForRange(start: String, end: String): Flow<List<ActualWorkout>>

    // B3-2 Stats : nombre de jours uniques avec au moins 1 seance is_done=1 dans le range.
    @Query("""
        SELECT COUNT(DISTINCT substr(date, 1, 10))
        FROM actual_workouts
        WHERE substr(date, 1, 10) BETWEEN :startDate AND :endDate
          AND pendingDeletion = 0
          AND is_done = 1
    """)
    fun observeActiveDaysCount(startDate: String, endDate: String): Flow<Int>

    @Query("SELECT * FROM actual_workouts WHERE uuid = :uuid")
    suspend fun getActualWorkoutByUUID(uuid: String): ActualWorkout?

    // Match sur la même journée (ignore l'heure si format datetime).
    // Robuste aux 2 formats stockables : "YYYY-MM-DD" strict et "YYYY-MM-DDTHH:..." ISO complet.
    @Query("SELECT * FROM actual_workouts WHERE substr(date, 1, 10) = :day LIMIT 1")
    suspend fun getActualWorkoutByDay(day: String): ActualWorkout?

    @Query("""
        SELECT * FROM actual_workouts
        WHERE substr(date, 1, 10) BETWEEN :start AND :end
    """)
    fun getActualWorkoutsForWeekFlow(start: String, end: String): Flow<List<ActualWorkout>>


    @Query("SELECT * FROM actual_workouts")
    suspend fun getAllOnce(): List<ActualWorkout>

    // --- Sync status (facultatif : pas besoin de toucher updatedAt ici si c’est purement technique)
    @Query("UPDATE actual_workouts SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE actual_workouts SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("UPDATE actual_workouts SET synced = :isSynced WHERE uuid = :uuid")
    suspend fun setSyncedStatus(uuid: String, isSynced: Boolean)

    @Query("SELECT * FROM actual_workouts WHERE synced = 0")
    suspend fun getAllUnsynced(): List<ActualWorkout>

    @Query("SELECT EXISTS(SELECT 1 FROM actual_workouts WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("SELECT * FROM actual_workouts WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<ActualWorkout>

    @Delete
    suspend fun delete(actualWorkout: ActualWorkout)

    @Query("DELETE FROM actual_workouts")
    suspend fun clearAll()

    /**
     * Cleanup des fake workouts insérés par SampleDataInserter (demo_tour).
     * UUIDs préfixés `sample-`. La FK CASCADE supprime exercises + sets enfants.
     */
    @Query("DELETE FROM actual_workouts WHERE uuid LIKE 'sample-%'")
    suspend fun deleteSampleWorkouts()

    // --- Insertion depuis le serveur (respecte payload tel quel) ---
    suspend fun insertFromServer(actualWorkout: ActualWorkout) {
        insertInternal(actualWorkout.copy(synced = true, pendingDeletion = false))
    }

    suspend fun insertAllFromServer(actualWorkouts: List<ActualWorkout>) {
        insertAllInternal(actualWorkouts.map { it.copy(synced = true, pendingDeletion = false) })
    }

    suspend fun updateFromServer(actualWorkout: ActualWorkout) {
        updateInternal(actualWorkout)
    }
}
