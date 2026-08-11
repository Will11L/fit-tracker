package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.NotificationDao
import com.example.sportapp.core.data.model.Notification
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class NotificationSyncable(
    private val dao: NotificationDao
) : SyncableEntity<Notification> {

    override val entityName = "Notifications"
    override val displayName = "Notification"
    override val iconRes = R.drawable.ic_notifications
    override val entityClass = Notification::class
    override val silent = true  // pas de snackbar (UX-noise)

    override fun observeAll(): Flow<List<Notification>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.notificationApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: Notification) = dao.insertFromServer(item)
    override suspend fun bulkInsertFromServer(items: List<Notification>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: Notification) {
        RetrofitInstance.notificationApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<Notification>) {
        RetrofitInstance.notificationApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: Notification) {
        RetrofitInstance.notificationApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: Notification) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: Notification) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: Notification) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: Notification) = dao.delete(item)

    override fun keyOf(item: Notification) = item.uuid
    override fun updatedAtOf(item: Notification) = item.updatedAt
    override fun syncedOf(item: Notification) = item.synced
    override fun pendingDeletionOf(item: Notification) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
