package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.Notification
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    // --------------------
    // OBSERVE
    // --------------------

    @Query("SELECT * FROM notifications WHERE uuid = :uuid LIMIT 1")
    fun observeByUUID(uuid: String): Flow<Notification?>

    @Query("SELECT * FROM notifications ORDER BY created_at DESC")
    fun observeAll(): Flow<List<Notification>>

    @RawQuery(observedEntities = [Notification::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, Notification>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<Notification>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM notifications WHERE read_at IS NULL ORDER BY created_at DESC")
    fun observeUnread(): Flow<List<Notification>>

    @Query("SELECT COUNT(*) FROM notifications WHERE read_at IS NULL AND pendingDeletion = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE synced = 0 AND pendingDeletion = 0")
    fun observeUnsyncedCount(): Flow<Int>

    // --------------------
    // GET (one-shot)
    // --------------------

    @Query("SELECT * FROM notifications WHERE uuid = :uuid")
    suspend fun getNotificationByUUID(uuid: String): Notification?

    @Query("SELECT * FROM notifications")
    suspend fun getAllOnce(): List<Notification>

    @Query("SELECT * FROM notifications WHERE pendingDeletion = 0")
    suspend fun getAllActiveNotifications(): List<Notification>


    // --------------------
    // INSERT / UPDATE (local)
    // --------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: Notification) {
        val now = getNowISO8601()
        insertInternal(notification.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notifications: List<Notification>) {
        val now = getNowISO8601()
        insertAllInternal(notifications.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun updateNotification(notification: Notification) {
        val now = getNowISO8601()
        updateInternal(notification.copy(updatedAt = now, synced = false))
    }


    // --------------------
    // DOMAIN ACTIONS
    // --------------------

    @Query("UPDATE notifications SET read_at = :readAt, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun markAsRead(
        uuid: String,
        readAt: String = getNowISO8601(),
        updatedAt: String = getNowISO8601()
    )

    @Query("UPDATE notifications SET read_at = :readAt, updated_at = :updatedAt, synced = 0 WHERE read_at IS NULL")
    suspend fun markAllAsRead(
        readAt: String = getNowISO8601(),
        updatedAt: String = getNowISO8601()
    )

    @Query("UPDATE notifications SET read_at = NULL, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnread(uuid: String, updatedAt: String = getNowISO8601())

    @Query("UPDATE notifications SET archived_at = :archivedAt, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun archive(
        uuid: String,
        archivedAt: String = getNowISO8601(),
        updatedAt: String = getNowISO8601()
    )

    @Query("UPDATE notifications SET archived_at = NULL, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun unarchive(uuid: String, updatedAt: String = getNowISO8601())


    // --------------------
    // DELETE
    // --------------------

    @Delete
    suspend fun delete(notification: Notification)

    @Query("UPDATE notifications SET pendingDeletion = 1, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM notifications WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<Notification>

    @Query("DELETE FROM notifications")
    suspend fun clearAll()


    // --------------------
    // SYNC
    // --------------------

    @Query("SELECT * FROM notifications WHERE synced = 0")
    suspend fun getAllUnsynced(): List<Notification>

    @Query("UPDATE notifications SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE notifications SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM notifications WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean


    // --------------------
    // Room internals
    // --------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(notification: Notification)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(notifications: List<Notification>)

    @Update
    suspend fun updateInternal(notification: Notification)


    // --------------------
    // From server (keep payload as-is)
    // --------------------

    suspend fun insertFromServer(notification: Notification) {
        insertInternal(notification.copy(synced = true, pendingDeletion = false))
    }

    suspend fun insertAllFromServer(notifications: List<Notification>) {
        insertAllInternal(notifications.map { it.copy(synced = true, pendingDeletion = false) })
    }

    suspend fun updateFromServer(notification: Notification) {
        updateInternal(notification)
    }
}
