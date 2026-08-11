package com.example.sportapp.feature.conversations.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.R
import com.example.sportapp.core.network.AgentChatMessage
import com.example.sportapp.core.network.AgentChatRequest
import com.example.sportapp.core.network.RetrofitInstance
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

/**
 * VM de l'agent IA conversationnel (écran Conversations).
 *
 * Persistance locale MVP : l'historique vit en mémoire dans ce VM (aucun
 * stockage Room/serveur — décision 2026-05-31). À chaque envoi, on POST
 * l'historique complet à /agent/chat ; le backend Pi orchestre la boucle
 * tool-use Claude et renvoie la réponse complète (pas de streaming au MVP,
 * d'où l'indicateur de chargement `isSending`).
 */
@HiltViewModel
class ConversationsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Un message affiché dans le fil (bulle). */
    data class ChatBubble(
        val role: Role,
        val text: String,
    ) {
        enum class Role { USER, ASSISTANT }
    }

    private val _messages = MutableStateFlow<List<ChatBubble>>(emptyList())
    val messages: StateFlow<List<ChatBubble>> = _messages.asStateFlow()

    /** True pendant que le serveur traite la requête (boucle tool-use). */
    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val api = RetrofitInstance.agentService

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isSending.value) return

        // Affiche tout de suite le message user (optimiste).
        _messages.value = _messages.value + ChatBubble(ChatBubble.Role.USER, trimmed)
        _isSending.value = true

        viewModelScope.launch {
            try {
                // Historique complet envoyé au serveur (rien stocké côté serveur).
                val wire = _messages.value.map {
                    AgentChatMessage(
                        role = if (it.role == ChatBubble.Role.USER) "user" else "assistant",
                        content = it.text,
                    )
                }
                val response = api.chat(AgentChatRequest(messages = wire))
                _messages.value = _messages.value +
                    ChatBubble(ChatBubble.Role.ASSISTANT, response.reply)
            } catch (e: HttpException) {
                _messages.value = _messages.value +
                    ChatBubble(ChatBubble.Role.ASSISTANT, httpErrorMessage(e))
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatBubble(
                    ChatBubble.Role.ASSISTANT,
                    context.getString(R.string.agent_error_network),
                )
            } finally {
                _isSending.value = false
            }
        }
    }

    /** Vide le fil (nouvelle conversation). L'historique n'étant pas persisté,
     *  un simple reset mémoire suffit. */
    fun clearConversation() {
        if (_isSending.value) return
        _messages.value = emptyList()
    }

    private fun httpErrorMessage(e: HttpException): String = when (e.code()) {
        503 -> context.getString(R.string.agent_error_unavailable)
        429 -> context.getString(R.string.agent_error_rate_limited)
        else -> context.getString(R.string.agent_error_generic, e.code())
    }
}
