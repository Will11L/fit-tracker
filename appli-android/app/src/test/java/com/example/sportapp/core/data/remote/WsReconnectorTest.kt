package com.example.sportapp.core.data.remote

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fix 2026-07-07 (diagnostic 2026-07-06) : reconnexion WS avec token expire.
 *
 * L'ancien `reconnectOnce()` retentait UNE fois avec `lastToken` perime -> 403
 * persistant apres expiration du JWT (30 min) + coupure (redeploy Pi, blip
 * reseau). Ces tests protegent le contrat du [WsReconnector] :
 *  1. Handshake 401/403 -> refresh du token PUIS reconnexion avec le NOUVEAU token.
 *  2. Coupure non-auth -> reconnexion avec le token FRAIS relu (pas le memorise).
 *  3. Echec de refresh -> retry borne avec backoff, pas de boucle folle.
 *  4. Backoff exponentiel base 3 s (pattern web ws.service.ts).
 *  5. reset() (onOpen / relance externe) redonne le budget de tentatives.
 *
 * Robolectric uniquement pour android.util.Log (pas de vraie socket : les
 * sources de token et le connect sont injectes en fakes).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class WsReconnectorTest {

    @Test
    fun `403 declenche refresh puis reconnect avec le nouveau token`() = runTest {
        val connected = mutableListOf<String>()
        val refreshedWith = mutableListOf<String?>()
        val reconnector = WsReconnector(
            scope = this,
            tokenProvider = { "stale-token" },
            tokenRefresher = { stale -> refreshedWith += stale; "fresh-token" },
            connect = { connected += it },
        )

        reconnector.onDisconnected(httpCode = 403, staleToken = "stale-token")
        advanceUntilIdle()

        assertEquals(listOf<String?>("stale-token"), refreshedWith) // refresh appele 1x avec le token rejete
        assertEquals(listOf("fresh-token"), connected) // reconnexion avec le NOUVEAU token
    }

    @Test
    fun `coupure non-auth relit le token frais sans declencher de refresh`() = runTest {
        val connected = mutableListOf<String>()
        var refreshCalls = 0
        val reconnector = WsReconnector(
            scope = this,
            // Simule un token deja rafraichi entre-temps par l'Authenticator REST.
            tokenProvider = { "fresh-from-token-manager" },
            tokenRefresher = { refreshCalls += 1; null },
            connect = { connected += it },
        )

        reconnector.onDisconnected(httpCode = null, staleToken = "old-token")
        advanceUntilIdle()

        assertEquals(0, refreshCalls) // pas d'auth error -> pas de refresh
        assertEquals(listOf("fresh-from-token-manager"), connected) // token FRAIS, pas lastToken
    }

    @Test
    fun `echec de refresh backoff borne sans boucle folle`() = runTest {
        val connected = mutableListOf<String>()
        var refreshCalls = 0
        val reconnector = WsReconnector(
            scope = this,
            tokenProvider = { null }, // pas de token de secours
            tokenRefresher = { refreshCalls += 1; null }, // refresh KO en boucle
            connect = { connected += it },
        )

        reconnector.onDisconnected(httpCode = 403, staleToken = "stale")
        advanceUntilIdle()

        assertEquals(WsReconnector.MAX_ATTEMPTS, refreshCalls) // borne, pas infini
        assertTrue(connected.isEmpty()) // jamais connecte sans token

        // Budget epuise : de nouveaux evenements ne relancent PAS la boucle.
        reconnector.onDisconnected(httpCode = 403, staleToken = "stale")
        advanceUntilIdle()
        assertEquals(WsReconnector.MAX_ATTEMPTS, refreshCalls)
    }

    @Test
    fun `backoff exponentiel 3s puis 6s`() = runTest {
        val connected = mutableListOf<String>()
        val reconnector = WsReconnector(
            scope = this,
            tokenProvider = { "token" },
            tokenRefresher = { null },
            connect = { connected += it },
        )

        // Tentative 1 : 3 s.
        reconnector.onDisconnected(httpCode = null, staleToken = null)
        advanceTimeBy(2_999); runCurrent()
        assertTrue(connected.isEmpty())
        advanceTimeBy(2); runCurrent()
        assertEquals(1, connected.size)

        // Le handshake echoue a nouveau -> tentative 2 : 6 s.
        reconnector.onDisconnected(httpCode = null, staleToken = null)
        advanceTimeBy(5_999); runCurrent()
        assertEquals(1, connected.size)
        advanceTimeBy(2); runCurrent()
        assertEquals(2, connected.size)
    }

    @Test
    fun `reset redonne le budget de tentatives`() = runTest {
        var refreshCalls = 0
        val reconnector = WsReconnector(
            scope = this,
            tokenProvider = { null },
            tokenRefresher = { refreshCalls += 1; null },
            connect = { },
        )

        reconnector.onDisconnected(httpCode = 401, staleToken = "stale")
        advanceUntilIdle()
        assertEquals(WsReconnector.MAX_ATTEMPTS, refreshCalls)

        // onOpen / start(resetRetry=true) -> reset -> le budget repart.
        reconnector.reset()
        reconnector.onDisconnected(httpCode = 401, staleToken = "stale")
        advanceUntilIdle()
        assertEquals(WsReconnector.MAX_ATTEMPTS * 2, refreshCalls)
    }

    @Test
    fun `onDisconnected est ignore si une reconnexion est deja programmee`() = runTest {
        val connected = mutableListOf<String>()
        val reconnector = WsReconnector(
            scope = this,
            tokenProvider = { "token" },
            tokenRefresher = { null },
            connect = { connected += it },
        )

        // onFailure + onClosed peuvent arriver rapproches : une seule boucle.
        reconnector.onDisconnected(httpCode = null, staleToken = null)
        reconnector.onDisconnected(httpCode = null, staleToken = null)
        advanceUntilIdle()

        assertEquals(1, connected.size)
    }
}
