package com.example.sportapp.viewmodel

import com.example.sportapp.core.stats.ZoneVolumeDatum
import com.example.sportapp.core.stats.buildZoneVolumeRadar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM du builder pur du radar « Équilibre par zone (volume) » (Stats).
 * Miroir de `appli-web/src/app/features/stats/zone-radar-data.spec.ts` :
 * ordre conserve, placeholder si tout a 0, axe a 0 conserve si une zone non nulle.
 */
class ZoneRadarDataTest {

    private val zones = listOf(
        ZoneVolumeDatum("Chest", 1200f),
        ZoneVolumeDatum("Back", 900f),
        ZoneVolumeDatum("Legs", 1500f),
    )

    @Test
    fun `un axe par zone, ordre d'entree conserve`() {
        val result = buildZoneVolumeRadar(zones)
        assertEquals(listOf("Chest", "Back", "Legs"), result.map { it.zone })
        assertEquals(listOf(1200f, 900f, 1500f), result.map { it.volume })
    }

    @Test
    fun `liste vide retourne vide`() {
        assertTrue(buildZoneVolumeRadar(emptyList()).isEmpty())
    }

    @Test
    fun `tout a zero retourne vide (placeholder)`() {
        val allZero = zones.map { it.copy(volume = 0f) }
        assertTrue(buildZoneVolumeRadar(allZero).isEmpty())
    }

    @Test
    fun `une zone a zero parmi des zones non nulles garde son axe`() {
        val mixed = listOf(
            ZoneVolumeDatum("Chest", 1200f),
            ZoneVolumeDatum("Back", 0f),
            ZoneVolumeDatum("Legs", 1500f),
        )
        val result = buildZoneVolumeRadar(mixed)
        assertEquals(listOf("Chest", "Back", "Legs"), result.map { it.zone })
        assertEquals(listOf(1200f, 0f, 1500f), result.map { it.volume })
    }
}
