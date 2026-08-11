package com.example.sportapp.feature.quotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.model.Quote
import com.example.sportapp.feature.quotes.data.QuotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * VM de l'ecran de gestion des citations motivantes.
 * Observe Room (source de verite locale) ; add/delete delegues au repository
 * (ecriture locale + push SyncEngine). La realtime WS hydrate Room en arriere-plan.
 */
@HiltViewModel
class QuotesViewModel @Inject constructor(
    private val repository: QuotesRepository,
) : ViewModel() {

    /** Citations visibles (les pendingDeletion sont masquees de la liste). */
    val quotes: StateFlow<List<Quote>> = repository.observeAll()
        .map { list -> list.filter { !it.pendingDeletion } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addQuote(text: String, author: String?) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.addQuote(text, author)
        }
    }

    fun deleteQuote(quote: Quote) {
        viewModelScope.launch {
            repository.deleteQuote(quote)
        }
    }

    fun updateQuote(quote: Quote, text: String, author: String?) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.updateQuote(quote, text, author)
        }
    }
}
