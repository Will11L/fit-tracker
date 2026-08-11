package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.SupersetGroup
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface SupersetGroupDao {

    @Query("SELECT * FROM superset_groups")
    fun observeAll(): Flow<List<SupersetGroup>>

    @RawQuery(observedEntities = [SupersetGroup::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, SupersetGroup>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<SupersetGroup>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM superset_groups WHERE uuid = :uuid")
    suspend fun getSupersetGroupByUUID(uuid: String): SupersetGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: SupersetGroup) {
        val now = getNowISO8601()
        insertInternal(group.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(groups: List<SupersetGroup>) {
        val now = getNowISO8601()
        insertAllInternal(groups.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun updateSupersetGroup(group: SupersetGroup) {
        val now = getNowISO8601()
        updateInternal(group.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(group: SupersetGroup)


    // 🔁 Synchronisation
    @Query("SELECT * FROM superset_groups WHERE synced = 0")
    suspend fun getAllUnsynced(): List<SupersetGroup>

    @Query("UPDATE superset_groups SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE superset_groups SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM superset_groups WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE superset_groups SET pendingDeletion = 1, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM superset_groups WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<SupersetGroup>

    @Query("DELETE FROM superset_groups")
    suspend fun clearAll()

    @Query("SELECT * FROM superset_groups")
    suspend fun getAllOnce(): List<SupersetGroup>


    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(group: SupersetGroup)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(groups: List<SupersetGroup>)

    @Update
    suspend fun updateInternal(group: SupersetGroup)

    // --- Insertion depuis le serveur (respecte payload tel quel) ---
    suspend fun insertFromServer(group: SupersetGroup) {
        insertInternal(group.copy(synced = true, pendingDeletion = false))
    }

    suspend fun insertAllFromServer(groups: List<SupersetGroup>) {
        insertAllInternal(groups.map { it.copy(synced = true, pendingDeletion = false) })
    }

    suspend fun updateFromServer(group: SupersetGroup) {
        updateInternal(group)
    }
}
