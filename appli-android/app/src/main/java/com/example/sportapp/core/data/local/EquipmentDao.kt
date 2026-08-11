package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.Equipment
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface EquipmentDao {

    @Query("SELECT * FROM equipments")
    fun observeAll(): Flow<List<Equipment>>

    @RawQuery(observedEntities = [Equipment::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, Equipment>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<Equipment>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM equipments WHERE uuid = :uuid")
    suspend fun getEquipmentByUUID(uuid: String): Equipment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(equipment: Equipment) {
        val now = getNowISO8601()
        insertInternal(equipment.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(equipments: List<Equipment>) {
        val now = getNowISO8601()
        insertAllInternal(equipments.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun updateEquipment(equipment: Equipment) {
        val now = getNowISO8601()
        updateInternal(equipment.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(equipment: Equipment)


    // 🔁 Synchronisation
    @Query("SELECT * FROM equipments WHERE synced = 0")
    suspend fun getAllUnsynced(): List<Equipment>

    @Query("UPDATE equipments SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE equipments SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM equipments WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE equipments SET pendingDeletion = 1, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun markAsPendingDeletionInternal(uuid: String, updatedAt: String)

    suspend fun markAsPendingDeletion(uuid: String) {
        val now = getNowISO8601()
        markAsPendingDeletionInternal(uuid, now)
    }

    @Query("SELECT * FROM equipments WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<Equipment>

    @Query("DELETE FROM equipments")
    suspend fun clearAll()

    @Query("SELECT * FROM equipments")
    suspend fun getAllOnce(): List<Equipment>


    // --- internes (utilisées par Room, appelées par les versions publiques)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(equipment: Equipment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(equipments: List<Equipment>)

    @Update
    suspend fun updateInternal(equipment: Equipment)

    // --- Insertion depuis le serveur (respecte payload tel quel) ---
    suspend fun insertFromServer(equipment: Equipment) {
        insertInternal(equipment.copy(synced = true, pendingDeletion = false))
    }

    suspend fun insertAllFromServer(equipments: List<Equipment>) {
        insertAllInternal(equipments.map { it.copy(synced = true, pendingDeletion = false) })
    }

    suspend fun updateFromServer(equipment: Equipment) {
        updateInternal(equipment)
    }
}
