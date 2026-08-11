package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.AvailableEquipment
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface AvailableEquipmentDao {

    @Query("SELECT * FROM available_equipments")
    fun observeAll(): Flow<List<AvailableEquipment>>

    @RawQuery(observedEntities = [AvailableEquipment::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, AvailableEquipment>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<AvailableEquipment>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM available_equipments WHERE uuid = :uuid")
    suspend fun getAvailableEquipmentByUUID(uuid: String): AvailableEquipment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(equipment: AvailableEquipment) {
        val now = getNowISO8601()
        insertInternal(equipment.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(equipments: List<AvailableEquipment>) {
        val now = getNowISO8601()
        insertAllInternal(equipments.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun updateAvailableEquipment(equipment: AvailableEquipment) {
        val now = getNowISO8601()
        updateAvailableEquipmentInternal(equipment.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(equipment: AvailableEquipment)


    // ---- internes : inchangés, juste utilisés par insert/update publics ci-dessus
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(equipment: AvailableEquipment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(equipments: List<AvailableEquipment>)

    @Update
    suspend fun updateAvailableEquipmentInternal(equipment: AvailableEquipment)


    // 🔁 Synchronisation
    @Query("SELECT * FROM available_equipments WHERE synced = 0")
    suspend fun getAllUnsynced(): List<AvailableEquipment>

    @Query("UPDATE available_equipments SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE available_equipments SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM available_equipments WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE available_equipments SET pendingDeletion = 1, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun markAsPendingDeletionInternal(uuid: String, updatedAt: String)

    suspend fun markAsPendingDeletion(uuid: String) {
        markAsPendingDeletionInternal(uuid, getNowISO8601())
    }

    @Query("SELECT * FROM available_equipments WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<AvailableEquipment>

    @Query("DELETE FROM available_equipments")
    suspend fun clearAll()

    @Query("SELECT * FROM available_equipments")
    suspend fun getAllOnce(): List<AvailableEquipment>

    // --- Insertion depuis le serveur (respecte payload tel quel) ---
    suspend fun insertFromServer(equipment: AvailableEquipment) {
        insertInternal(equipment.copy(synced = true, pendingDeletion = false))
    }

    suspend fun insertAllFromServer(equipments: List<AvailableEquipment>) {
        insertAllInternal(equipments.map { it.copy(synced = true, pendingDeletion = false) })
    }

    suspend fun updateFromServer(equipment: AvailableEquipment) {
        updateAvailableEquipmentInternal(equipment)
    }
}
