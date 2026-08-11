package com.example.sportapp.feature.auth.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.model.Quote
import com.example.sportapp.feature.auth.AuthManager
import com.example.sportapp.feature.quotes.data.QuotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogoutScreenViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val quotesRepository: QuotesRepository,
) : ViewModel() {

    // Citation motivante tirée au hasard (locale Room) pour l'écran de déconnexion.
    // Tirée AVANT stopAuth() (qui peut purger Room) ; le state garde le Quote.
    val motivationalQuote = mutableStateOf<Quote?>(null)

    fun logout() {
        viewModelScope.launch {
            runCatching {
                motivationalQuote.value = quotesRepository.getActive().randomOrNull()
            }
            authManager.stopAuth()
        }
    }
}
