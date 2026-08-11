package com.example.sportapp.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

/**
 * Reçoit la requête pull du téléphone (path [WearLivePayload.REQUEST_PATH]) et y
 * répond via [SpotHealthReader], **même app fermée** (le système réveille le
 * service sur le message Data Layer). `runBlocking` maintient le service vivant le
 * temps de la lecture spot (thread worker, hors main thread). Aucun service
 * persistant ni polling → réveil uniquement sur requête.
 *
 * Logs `WearPull` : observabilité du canal inter-devices (conservés).
 */
class WearRequestListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        Log.d("WearPull", "onMessageReceived path=${event.path} from=${event.sourceNodeId}")
        if (event.path != WearLivePayload.REQUEST_PATH) return
        runBlocking { SpotHealthReader(applicationContext).respondTo(event.sourceNodeId) }
    }
}
