package com.example.sportapp.core.sync.syncables

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.R
import com.example.sportapp.core.data.local.QuoteDao
import com.example.sportapp.core.data.model.Quote
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.base.SyncableEntity
import kotlinx.coroutines.flow.Flow

class QuoteSyncable(
    private val dao: QuoteDao
) : SyncableEntity<Quote> {

    override val entityName = "Quotes"
    override val displayName = "Quote"
    override val iconRes = R.drawable.ic_rounded_book
    override val entityClass = Quote::class

    override fun observeAll(): Flow<List<Quote>> = dao.observeAll()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun hasUnsynced() = dao.hasUnsynced()

    override suspend fun getRemote() = RetrofitInstance.quoteApi.getAll()

    override suspend fun clearLocal() = dao.clearAll()
    override suspend fun insertFromServer(item: Quote) { dao.insertFromServer(item) }
    override suspend fun bulkInsertFromServer(items: List<Quote>) = dao.insertAllFromServer(items)

    override suspend fun upsert(item: Quote) {
        RetrofitInstance.quoteApi.upsert(item.uuid, item)
    }
    override suspend fun upsertBulk(items: List<Quote>) {
        RetrofitInstance.quoteApi.upsertAll(items)
    }
    override suspend fun deleteRemote(item: Quote) {
        RetrofitInstance.quoteApi.delete(item.uuid)
    }

    override suspend fun markAsSynced(item: Quote) = dao.markAsSynced(item.uuid)
    override suspend fun markAsUnsynced(item: Quote) = dao.markAsUnsynced(item.uuid)
    override suspend fun markAsPendingDeletion(item: Quote) = dao.markAsPendingDeletion(item.uuid)
    override suspend fun deleteLocal(item: Quote) = dao.delete(item)

    override fun keyOf(item: Quote) = item.uuid
    override fun updatedAtOf(item: Quote) = item.updatedAt
    override fun syncedOf(item: Quote) = item.synced
    override fun pendingDeletionOf(item: Quote) = item.pendingDeletion

    override fun pagingSourceRaw(query: SupportSQLiteQuery) = dao.pagingSourceRaw(query)
    override suspend fun selectRowsRaw(query: SupportSQLiteQuery) = dao.selectRowsRaw(query)
    override suspend fun selectCountRaw(query: SupportSQLiteQuery) = dao.selectCountRaw(query)
}
