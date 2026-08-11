package com.example.sportapp.core.data.local

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sportapp.core.data.model.Quote
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteDao {

    @Query("SELECT * FROM quotes ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<Quote>>

    @RawQuery(observedEntities = [Quote::class])
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, Quote>

    @RawQuery
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<Quote>

    @RawQuery
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM quotes WHERE uuid = :uuid")
    suspend fun getByUUID(uuid: String): Quote?

    /** Citations visibles (hors suppression en attente) -- pour le tirage SplashScreen. */
    @Query("SELECT * FROM quotes WHERE pendingDeletion = 0")
    suspend fun getActive(): List<Quote>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(quote: Quote) {
        val now = getNowISO8601()
        insertInternal(quote.copy(updatedAt = now, synced = false))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(quotes: List<Quote>) {
        val now = getNowISO8601()
        insertAllInternal(quotes.map { it.copy(updatedAt = now, synced = false) })
    }

    @Update
    suspend fun update(quote: Quote) {
        val now = getNowISO8601()
        updateInternal(quote.copy(updatedAt = now, synced = false))
    }

    @Delete
    suspend fun delete(quote: Quote)

    // 🔁 Synchronisation
    @Query("SELECT * FROM quotes WHERE synced = 0")
    suspend fun getAllUnsynced(): List<Quote>

    @Query("UPDATE quotes SET synced = 1 WHERE uuid = :uuid")
    suspend fun markAsSynced(uuid: String)

    @Query("UPDATE quotes SET synced = 0 WHERE uuid = :uuid")
    suspend fun markAsUnsynced(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM quotes WHERE synced = 0 LIMIT 1)")
    suspend fun hasUnsynced(): Boolean

    @Query("UPDATE quotes SET pendingDeletion = 1, updated_at = :updatedAt, synced = 0 WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())

    @Query("SELECT * FROM quotes WHERE pendingDeletion = 1")
    suspend fun getPendingDeletions(): List<Quote>

    @Query("DELETE FROM quotes")
    suspend fun clearAll()

    @Query("SELECT * FROM quotes")
    suspend fun getAllOnce(): List<Quote>

    // --- internes pour Room
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(quote: Quote)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(quotes: List<Quote>)

    @Update
    suspend fun updateInternal(quote: Quote)

    // --- depuis serveur (respect payload tel quel)
    suspend fun insertFromServer(quote: Quote) =
        insertInternal(quote.copy(synced = true, pendingDeletion = false))
    suspend fun insertAllFromServer(quotes: List<Quote>) =
        insertAllInternal(quotes.map { it.copy(synced = true, pendingDeletion = false) })
    suspend fun updateFromServer(quote: Quote) = updateInternal(quote)
}
