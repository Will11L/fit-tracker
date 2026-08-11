package com.example.sportapp.core.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Cas C — Agent IA in-app (Phase 2 MCP).
 *
 * Appelle POST /api/v1/agent/chat : l'app envoie l'historique complet de la
 * conversation (persistance locale MVP, rien stocké côté serveur), le backend
 * Pi orchestre la boucle tool-use Claude (tools MCP read+write) et renvoie la
 * réponse complète (pas de streaming au MVP).
 *
 * Le JWT est injecté par l'authInterceptor de RetrofitInstance (client
 * authentifié). La clé Anthropic vit côté serveur, jamais dans l'APK.
 */

/** Un tour de conversation. role = "user" | "assistant". */
data class AgentChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String,
)

/** Body de POST /agent/chat : historique complet (doit finir par un tour user). */
data class AgentChatRequest(
    @SerializedName("messages") val messages: List<AgentChatMessage>,
)

/** Récap d'un tool MCP appelé pendant la boucle (transparence UX). */
data class AgentToolCall(
    @SerializedName("toolName") val toolName: String,
)

/** Réponse de l'agent : texte assistant final + tools appelés. */
data class AgentChatResponse(
    @SerializedName("reply") val reply: String,
    @SerializedName("toolCalls") val toolCalls: List<AgentToolCall> = emptyList(),
)

interface ApiAgentService {
    @POST("agent/chat")
    suspend fun chat(@Body body: AgentChatRequest): AgentChatResponse
}
