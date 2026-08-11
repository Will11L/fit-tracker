package com.example.sportapp.feature.quotes.data

import com.example.sportapp.core.data.local.QuoteDao
import com.example.sportapp.core.data.model.Quote
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.sync.SyncEngine
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestion locale des citations motivantes + push vers le serveur.
 *
 * Pattern aligne sur MuscleGoalsManager : on ecrit localement (Room) puis on
 * pousse l'entite via SyncEngine (silencieux). La realtime descendante (WS)
 * passe par QuoteSyncHandler.
 */
@Singleton
class QuotesRepository @Inject constructor(
    private val dao: QuoteDao,
    private val syncEngine: SyncEngine,
) {

    fun observeAll(): Flow<List<Quote>> = dao.observeAll()

    /** Citations visibles (hors suppression en attente). Tirage SplashScreen. */
    suspend fun getActive(): List<Quote> = dao.getActive()

    suspend fun addQuote(text: String, author: String?) {
        val userId = CurrentUserManager.userId ?: return
        val quote = Quote(
            uuid = UUID.randomUUID().toString(),
            userId = userId,
            text = text.trim(),
            author = author?.trim()?.takeIf { it.isNotEmpty() },
        )
        dao.insert(quote)
        syncEngine.pushEntityClass(Quote::class)
    }

    suspend fun deleteQuote(quote: Quote) {
        dao.markAsPendingDeletion(quote.uuid)
        syncEngine.pushEntityClass(Quote::class)
    }

    suspend fun updateQuote(quote: Quote, text: String, author: String?) {
        dao.update(
            quote.copy(
                text = text.trim(),
                author = author?.trim()?.takeIf { it.isNotEmpty() },
            )
        )
        syncEngine.pushEntityClass(Quote::class)
    }
}
