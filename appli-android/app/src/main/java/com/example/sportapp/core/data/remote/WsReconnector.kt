package com.example.sportapp.core.data.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Politique de reconnexion du WebSocket (fix 2026-07-07, diagnostic 2026-07-06 :
 * "403 persistant apres expiration du JWT"). Extraite de [WebSocketManager] pour
 * etre testable en JVM (sources de token injectables, pas de vraie socket).
 *
 * Remplace l'ancien `reconnectOnce()` (retry unique avec `lastToken` memorise
 * au dernier start(), donc perime apres 30 min) par :
 *  1. Token FRAIS relu a chaque tentative via [tokenProvider] -- capte le
 *     refresh deja fait par l'Authenticator REST entre-temps.
 *  2. Si l'echec du handshake est un 401/403 (token expire/invalide) : refresh
 *     proactif via [tokenRefresher] AVANT de reconnecter. Necessaire parce que
 *     le client OkHttp du WS n'a pas d'Authenticator : le handshake ne
 *     beneficie jamais du refresh du flux REST.
 *  3. Retry borne avec backoff exponentiel (base 3 s comme le reconnect du web
 *     `ws.service.ts`, cap 30 s, [maxAttempts] tentatives) au lieu du one-shot.
 *
 * Le compteur de tentatives est remis a zero par [reset] (handshake reussi, ou
 * relance externe start(resetRetry=true) : login, retour reseau, bouton
 * "Relancer WS"). Une fois le budget epuise, la reconnexion attend un de ces
 * declencheurs externes (pas de boucle infinie).
 */
class WsReconnector(
    private val scope: CoroutineScope,
    /** Lit le token courant (frais). Prod : TokenManager.token ?: lastToken. */
    private val tokenProvider: () -> String?,
    /**
     * Refresh le JWT (staleToken = token qui vient d'etre rejete, pour le
     * short-circuit "deja rafraichi par le REST"). Retourne le nouveau access
     * token ou null si echec. Prod : RetrofitInstance.refreshAccessToken.
     */
    private val tokenRefresher: suspend (staleToken: String?) -> String?,
    /** Reconnecte le WS avec ce token. Prod : start(token, resetRetry=false). */
    private val connect: (token: String) -> Unit,
    private val maxAttempts: Int = MAX_ATTEMPTS,
    private val baseDelayMillis: Long = BASE_DELAY_MS,
) {
    companion object {
        private const val TAG = "WebSocket"
        const val MAX_ATTEMPTS = 5
        const val BASE_DELAY_MS = 3_000L
        const val MAX_DELAY_MS = 30_000L
    }

    @Volatile
    private var attempts = 0
    private var job: Job? = null

    /**
     * Connexion etablie (onOpen) ou relance externe (start resetRetry=true) :
     * repart de zero et annule toute reconnexion en attente.
     */
    @Synchronized
    fun reset() {
        attempts = 0
        job?.cancel()
        job = null
    }

    /**
     * A appeler sur onFailure/onClosed d'une WS courante. [httpCode] = code
     * HTTP du handshake si disponible (401/403 = token rejete), null sinon
     * (coupure reseau, close serveur). [staleToken] = token utilise par la
     * connexion qui vient de tomber.
     */
    @Synchronized
    fun onDisconnected(httpCode: Int?, staleToken: String?) {
        if (job?.isActive == true) return // reconnexion deja programmee
        job = scope.launch { retryLoop(httpCode, staleToken) }
    }

    private suspend fun retryLoop(httpCode: Int?, staleToken: String?) {
        val needsRefresh = httpCode == 401 || httpCode == 403
        while (attempts < maxAttempts) {
            attempts += 1
            val delayMs = (baseDelayMillis shl (attempts - 1)).coerceAtMost(MAX_DELAY_MS)
            Log.i(TAG, "🔄 Reconnexion dans ${delayMs}ms (tentative $attempts/$maxAttempts, http=$httpCode)")
            delay(delayMs)

            val token = if (needsRefresh) {
                Log.i(TAG, "🔑 Handshake rejete ($httpCode) -> refresh du token avant reconnexion")
                tokenRefresher(staleToken) ?: tokenProvider()
            } else {
                tokenProvider()
            }

            if (!token.isNullOrBlank()) {
                connect(token)
                return // le sort du handshake decide : onOpen -> reset(), sinon onDisconnected -> reprise
            }
            Log.w(TAG, "⚠️ Pas de token disponible (refresh KO ?) -> nouvelle tentative")
        }
        Log.w(TAG, "⏹ Reconnexion abandonnee apres $maxAttempts tentatives (attente d'un declencheur externe)")
    }
}
