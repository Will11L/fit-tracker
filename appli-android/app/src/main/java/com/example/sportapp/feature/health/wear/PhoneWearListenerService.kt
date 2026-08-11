package com.example.sportapp.feature.health.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Reçoit le canal live de la montre (Wearable Data Layer, `MessageClient`) et
 * met à jour [WearLiveState]. Enregistré dans le manifest avec un intent-filter
 * `MESSAGE_RECEIVED` sur le path [HealthLivePayload.MESSAGE_PATH]. Instancié par
 * le système à la réception (pas d'injection Hilt) → passe par le singleton.
 */
class PhoneWearListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != HealthLivePayload.MESSAGE_PATH) return
        HealthLivePayload.decode(String(event.data, Charsets.UTF_8))?.let(WearLiveState::update)
    }
}
