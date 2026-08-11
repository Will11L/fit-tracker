package com.example.sportapp.core.utils

import com.example.sportapp.core.utils.JsonUtils.getNullableFloat
import com.example.sportapp.core.utils.JsonUtils.getNullableString
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Nutrition A1 (2026-06-17) — verrouille le helper de parsing nullable utilisé par
 * les 8 *SyncHandler WS nutrition (commit 208f7cb).
 *
 * Pourquoi c'est load-bearing : un aliment CIQUAL n'a presque jamais TOUS les micros
 * renseignés. Le payload WS `food_updated` omet (ou met à `null`) les vitamines/minéraux
 * absents. `getNullableFloat` doit alors renvoyer `null` — surtout PAS `0f` (ce qui ferait
 * croire à un aliment dont le micro vaut zéro). Une "simplification" en `optDouble(key, 0.0)`
 * casserait silencieusement cette distinction sur tout le realtime nutrition.
 *
 * Robolectric fournit la vraie implémentation `org.json` (stubbée/throw en JVM nu).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class JsonUtilsTest {

    @Test
    fun `getNullableFloat returns null for absent and JSON-null keys, value otherwise`() {
        // Payload micros partiel : vitaminB12 présent, fer en JSON null, calcium absent.
        val payload = JSONObject(
            """{ "vitaminB12Per100g": 1.1, "ironPer100g": null }"""
        )

        assertEquals(1.1f, payload.getNullableFloat("vitaminB12Per100g"))
        assertNull("une clé JSON null doit donner null, pas 0f", payload.getNullableFloat("ironPer100g"))
        assertNull("une clé absente doit donner null, pas 0f", payload.getNullableFloat("calciumPer100g"))
    }

    @Test
    fun `getNullableFloat preserves an explicit zero`() {
        // Distinction critique : un micro réellement à 0 != micro non renseigné (null).
        val payload = JSONObject("""{ "saltPer100g": 0.0 }""")
        assertEquals(0f, payload.getNullableFloat("saltPer100g"))
    }

    @Test
    fun `getNullableString returns null for absent and JSON-null keys, value otherwise`() {
        val payload = JSONObject(
            """{ "brand": "Lustucru", "sourceRef": null }"""
        )

        assertEquals("Lustucru", payload.getNullableString("brand"))
        assertNull(payload.getNullableString("sourceRef"))
        assertNull(payload.getNullableString("foodGroup"))
    }
}
