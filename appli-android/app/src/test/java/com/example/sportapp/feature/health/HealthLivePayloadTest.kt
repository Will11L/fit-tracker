package com.example.sportapp.feature.health

import com.example.sportapp.feature.health.wear.HealthLivePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests JVM purs du format du canal live montre → téléphone (Data Layer). */
class HealthLivePayloadTest {

    @Test
    fun `encode puis decode preserve les valeurs (distance et calories incluses)`() {
        val p = HealthLivePayload(steps = 4231, hr = 72, timestampMillis = 1_720_000_000_000, distanceM = 3200, caloriesKcal = 1850)
        val decoded = HealthLivePayload.decode(p.encode())
        assertEquals(p, decoded)
    }

    @Test
    fun `hr distance calories null encodent en tiret et redecodent en null`() {
        val p = HealthLivePayload(steps = 10, hr = null, timestampMillis = 42)
        assertEquals("steps=10;hr=-;dist=-;cal=-;ts=42", p.encode())
        val decoded = HealthLivePayload.decode(p.encode())
        assertEquals(p, decoded)
        assertNull(decoded!!.hr)
        assertNull(decoded.distanceM)
        assertNull(decoded.caloriesKcal)
    }

    @Test
    fun `decode tolere un ancien message sans distance ni calories (retro-compat)`() {
        val decoded = HealthLivePayload.decode("steps=500;hr=80;ts=99")
        assertEquals(HealthLivePayload(steps = 500, hr = 80, timestampMillis = 99), decoded)
        assertNull(decoded!!.distanceM)
        assertNull(decoded.caloriesKcal)
    }

    @Test
    fun `decode tolere l'ordre des champs`() {
        val decoded = HealthLivePayload.decode("ts=99;cal=1200;dist=1500;hr=80;steps=500")
        assertEquals(
            HealthLivePayload(steps = 500, hr = 80, timestampMillis = 99, distanceM = 1500, caloriesKcal = 1200),
            decoded,
        )
    }

    @Test
    fun `decode renvoie null si steps manquant`() {
        assertNull(HealthLivePayload.decode("hr=70;ts=1"))
    }

    @Test
    fun `decode renvoie null si ts manquant ou invalide`() {
        assertNull(HealthLivePayload.decode("steps=1;hr=70"))
        assertNull(HealthLivePayload.decode("steps=1;hr=70;ts=abc"))
    }

    @Test
    fun `decode renvoie null sur chaine vide`() {
        assertNull(HealthLivePayload.decode(""))
    }
}
