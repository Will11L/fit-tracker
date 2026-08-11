package com.example.sportapp.feature.health

import com.example.sportapp.feature.health.wear.HealthLivePayload
import com.example.sportapp.feature.health.wear.WearLiveStatus
import com.example.sportapp.feature.health.wear.wearLiveStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests JVM purs de la machine d'états de la section « Montre — live » (v2). */
class WearLiveStatusTest {

    @Test
    fun `donnee fraiche est LIVE`() {
        assertEquals(WearLiveStatus.LIVE, wearLiveStatus(hasData = true, ageSeconds = 2, querying = false))
    }

    @Test
    fun `donnee ancienne est STALE`() {
        assertEquals(WearLiveStatus.STALE, wearLiveStatus(hasData = true, ageSeconds = 30, querying = false))
    }

    @Test
    fun `donnee presente prime sur l'interrogation`() {
        assertEquals(WearLiveStatus.LIVE, wearLiveStatus(hasData = true, ageSeconds = 1, querying = true))
    }

    @Test
    fun `pas de donnee mais interrogation en cours est QUERYING`() {
        assertEquals(WearLiveStatus.QUERYING, wearLiveStatus(hasData = false, ageSeconds = 0, querying = true))
    }

    @Test
    fun `pas de donnee ni interrogation est DISCONNECTED`() {
        assertEquals(WearLiveStatus.DISCONNECTED, wearLiveStatus(hasData = false, ageSeconds = 0, querying = false))
    }

    @Test
    fun `seuil de fraicheur inclusif`() {
        assertEquals(WearLiveStatus.LIVE, wearLiveStatus(hasData = true, ageSeconds = 10, querying = false, staleThreshold = 10))
        assertEquals(WearLiveStatus.STALE, wearLiveStatus(hasData = true, ageSeconds = 11, querying = false, staleThreshold = 10))
    }

    @Test
    fun `les paths requete phone et wire sont coherents`() {
        // Le path de requête doit rester synchrone avec le module :wear (WearLivePayload).
        assertEquals("/health/live/request", HealthLivePayload.REQUEST_PATH)
        assertEquals("/health/live", HealthLivePayload.MESSAGE_PATH)
    }
}
