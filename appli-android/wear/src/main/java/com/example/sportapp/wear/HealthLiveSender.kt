package com.example.sportapp.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Envoie le payload live aux nœuds (téléphones) connectés via `MessageClient`
 * (fire-and-forget, faible latence — adapté à un flux éphémère seconde-par-seconde ;
 * un message perdu si le téléphone est injoignable n'est pas un problème pour du
 * live). Retourne `true` si au moins un nœud a reçu le message.
 */
class HealthLiveSender(context: Context) {

    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    suspend fun send(payload: String): Boolean {
        val nodes = runCatching { nodeClient.connectedNodes.await() }.getOrNull().orEmpty()
        if (nodes.isEmpty()) return false
        var delivered = false
        val bytes = payload.toByteArray(Charsets.UTF_8)
        for (node in nodes) {
            runCatching { messageClient.sendMessage(node.id, WearLivePayload.PATH, bytes).await() }
                .onSuccess { delivered = true }
        }
        return delivered
    }
}
