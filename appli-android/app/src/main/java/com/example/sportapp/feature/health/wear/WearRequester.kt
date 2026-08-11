package com.example.sportapp.feature.health.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Déclenche un pull téléphone → montre : envoie un message vide sur
 * [HealthLivePayload.REQUEST_PATH] à tous les nodes connectés (réveille la montre
 * même app fermée). La montre répond ensuite sur `/health/live`, reçu par
 * [PhoneWearListenerService]. Retourne `true` si au moins un node a été sollicité.
 *
 * Logs `WearPull` : observabilité du canal inter-devices (conservés).
 */
@Singleton
class WearRequester @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    suspend fun requestLive(): Boolean {
        val nodes = runCatching { nodeClient.connectedNodes.await() }
            .onFailure { Log.w(TAG, "connectedNodes failed", it) }
            .getOrNull().orEmpty()
        Log.d(TAG, "connectedNodes count=${nodes.size}")
        if (nodes.isEmpty()) return false
        var sent = false
        for (node in nodes) {
            Log.d(TAG, "node id=${node.id} name=${node.displayName} nearby=${node.isNearby}")
            runCatching {
                messageClient.sendMessage(node.id, HealthLivePayload.REQUEST_PATH, ByteArray(0)).await()
            }.onSuccess {
                sent = true
                Log.d(TAG, "sendMessage OK to ${node.id} (result=$it)")
            }.onFailure {
                Log.w(TAG, "sendMessage FAILED to ${node.id}", it)
            }
        }
        Log.d(TAG, "requestLive sent=$sent")
        return sent
    }

    private companion object {
        const val TAG = "WearPull"
    }
}
