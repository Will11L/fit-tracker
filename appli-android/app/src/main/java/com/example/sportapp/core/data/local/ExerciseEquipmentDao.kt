package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.ExerciseEquipment
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseEquipmentDao {

    @Query("SELECT * FROM exercise_equipment")
    fun observeAll(): Flow<List<ExerciseEquipment>>

    @RawQuery(observedEntities = [ExerciseEquipment::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, ExerciseEquipment>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<ExerciseEquipment>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM exercise_equipment WHERE exercise_uuid = :exerciseUUID")
    fun observeByExerciseUUID(exerciseUUID: String): Flow<List<ExerciseEquipment>>

    @Query("SELECT * FROM exercise_equipment WHERE uuid = :uuid")
    suspend fun getExerciseEquipmentByUUID(uuid: String): ExerciseEquipment?

    @Query("SELECT * FROM exercise_equipment WHERE exercise_uuid = :exerciseUUID")
    suspend fun getEquipmentsByExerciseUUID(exerciseUUID: String): List<ExerciseEquipment>

    @Query("SELECT * FROM exercise_equipment WHERE exercise_uuid = :exerciseUUID")
    fun getEquipmentsByExerciseUUIDFlow(exerciseUUID: String): Flow<List<ExerciseEquipment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(equipment: ExerciseEquipment) {
        val now = getNowISO8601()
        insertInternal(equipment.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(equipments: List<ExerciseEquipment>) {
        val now = getNowISO8601()
        insertAllInternal(equipments.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun updateExerciseEquipment(equipment: ExerciseEquipment) {
        val now = getNowISO8601()
        updateInternal(equipment.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(equipment: ExerciseEquipment)


    // 🔁 Synchronisation
    @Query("SELECT * FROM exercise_equipment WHERE synced = 0")
    suspend fun getAllUnsynced(): List<ExerciseEquipment>

    @Query("UPDATE exercise_equipment SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE exercise_equipment SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM exercise_equipment WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE exercise_equipment SET pendingDeletion = 1, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun markAsPendingDeletionInternal(uuid: String, updatedAt: String)

    suspend fun markAsPendingDeletion(uuid: String) {
        val now = getNowISO8601()
        markAsPendingDeletionInternal(uuid, now)
    }

    @Query("SELECT * FROM exercise_equipment WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<ExerciseEquipment>

    @Query("DELETE FROM exercise_equipment")
    suspend fun clearAll()

    @Query("SELECT * FROM exercise_equipment")
    suspend fun getAllOnce(): List<ExerciseEquipment>


    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(equipment: ExerciseEquipment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(equipments: List<ExerciseEquipment>)

    @Update
    suspend fun updateInternal(equipment: ExerciseEquipment)

    // --- Insertion depuis le serveur (respecte payload tel quel) ---
    suspend fun insertFromServer(equipment: ExerciseEquipment) {
        insertInternal(equipment.copy(synced = true, pendingDeletion = false))
    }

    suspend fun insertAllFromServer(equipments: List<ExerciseEquipment>) {
        insertAllInternal(equipments.map { it.copy(synced = true, pendingDeletion = false) })
    }

    suspend fun updateFromServer(equipment: ExerciseEquipment) {
        updateInternal(equipment)
    }
}
