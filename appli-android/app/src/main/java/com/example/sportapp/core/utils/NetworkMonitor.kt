package com.example.sportapp.core.utils

import android.content.Context
import android.net.*
import android.util.Log
import androidx.compose.material3.SnackbarDuration
import com.example.sportapp.app.SnackbarController
import com.example.sportapp.core.di.SyncEntryPoint
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.network.RetrofitInstance.userService
import com.example.sportapp.core.network.TokenManager
import com.example.sportapp.core.sync.SyncEvents
import com.example.sportapp.core.data.remote.WebSocketManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NetworkMonitor(
    private val context: Context,
    private val onReconnect: () -> Unit
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    // Stocke l'id de la snackbar offline pour la dismisser auto a la
    // reconnexion (user feedback : pas besoin de Close manuel si le reseau
    // revient).
    private var offlineSnackbarId: String? = null

    // Fix (2026-06-04) : on suit uniquement les reseaux PHYSIQUES via un
    // NetworkRequest exigeant NET_CAPABILITY_NOT_VPN + NET_CAPABILITY_INTERNET.
    //
    // Pourquoi NOT_VPN : l'utilisateur a Tailscale (interface tun0). Verifie via
    // `dumpsys connectivity` : quand on coupe wifi+data, le reseau VPN garde
    // INTERNET & VALIDATED (UnderlyingNetworks: []) -> impossible de distinguer
    // online/offline sur la presence d'un reseau ou sur VALIDATED, le VPN reste
    // toujours "la". En excluant le transport VPN, on ne compte que le reseau
    // reel (wifi / data cellulaire) ; couper tout => plus aucun reseau physique
    // => offline. L'IMS (appels VoLTE) est aussi exclu car il n'a pas INTERNET.
    //
    // `availableNetworks` = reseaux physiques actuellement disponibles.
    // `isOnline` = etat CONFIRME (debounce, cf. plus bas) reellement affiche.
    private val availableNetworks = mutableSetOf<Network>()
    private var isOnline = false

    // Debounce du passage offline. Lors d'une bascule (wifi <-> cellulaire) ou
    // d'un bref clignotement du dernier reseau, on recoit onLost suivi de peu
    // d'un onAvailable. Sans debounce on flapperait offline->online, ce qui
    // ferait clignoter l'icone signal ET redeclencherait onReconnected (sync +
    // WS) plusieurs fois (bug "double resynchronisation"). On attend donc
    // OFFLINE_DEBOUNCE_MS avant de confirmer offline ; un onAvailable entre-temps
    // l'annule.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var offlineJob: Job? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Un reseau physique devient disponible : annule un passage offline
            // en attente (cas d'une bascule, pas une vraie perte).
            offlineJob?.cancel()
            offlineJob = null

            availableNetworks.add(network)
            val wasOffline = !isOnline
            isOnline = true
            SyncEvents.isNetworkAvailable.value = true

            if (!wasOffline) {
                Log.d("NetworkMonitor", "physical network available (already online), n=${availableNetworks.size}")
                return
            }

            Log.d("NetworkMonitor", "physical network available (offline -> online)")
            onReconnected()
        }

        override fun onLost(network: Network) {
            availableNetworks.remove(network)
            if (availableNetworks.isNotEmpty()) {
                // Il reste un reseau physique (ex. wifi coupe mais data active).
                Log.d("NetworkMonitor", "physical network lost but still online, n=${availableNetworks.size}")
                return
            }

            // Plus aucun reseau physique : on confirme offline apres le debounce
            // (sauf si un reseau reprend le relais entre-temps -> onAvailable
            // annule ce job).
            offlineJob?.cancel()
            offlineJob = scope.launch {
                delay(OFFLINE_DEBOUNCE_MS)
                isOnline = false
                SyncEvents.isNetworkAvailable.value = false
                Log.d("NetworkMonitor", "physical network lost (online -> offline)")
                // Persistante (Indefinite) avec bouton "Close" auto-injecte +
                // auto-dismiss a la reconnexion (cf. onReconnected).
                offlineSnackbarId = showSnackbar(
                    message = context.getString(com.example.sportapp.R.string.network_offline),
                    type = SnackbarType.WARNING,
                    duration = SnackbarDuration.Indefinite
                )
                // Ferme le WebSocket tout de suite : sans reseau physique il est
                // de toute facon mort, mais OkHttp ne le detecterait qu'au ping
                // timeout (~30s) -> l'icone WS resterait verte alors que le signal
                // est rouge. La reconnexion est relancee par onReconnected() au
                // retour du reseau.
                EntryPointAccessors
                    .fromApplication(context, SyncEntryPoint::class.java)
                    .webSocketManager()
                    .stop()
            }
        }
    }

    /**
     * Declenche a la transition reelle offline -> online : dismisse la snackbar
     * offline, revalide l'userId, relance la sync (SyncCoordinator) + le WebSocket.
     */
    private fun onReconnected() {
        // Auto-dismiss la snackbar offline si elle etait encore visible
        offlineSnackbarId?.let { id ->
            scope.launch { SnackbarController.dismissSnackbarById(id) }
            offlineSnackbarId = null
        }

        CoroutineScope(Dispatchers.IO).launch {
            // Le reseau physique vient de revenir : purge les sockets morts du
            // pool OkHttp pour que la 1re requete (re-validation userId, sync, WS)
            // ne reutilise pas une connexion liee a l'ancien reseau -- sinon elle
            // echoue puis ne remarche qu'au 2e essai (login intermittent apres
            // reconnexion reseau).
            RetrofitInstance.evictConnections()

            // 🔄 revalider userId
            if (CurrentUserManager.userId == null) {
                try {
                    val userInfo = userService.getUserInfo()
                    CurrentUserManager.setUserId(context, userInfo.id)
                    CurrentUserManager.setUserAdmin(context, userInfo.isAdmin)
                    CurrentUserManager.setProfile(context, userInfo)
                    Log.d("NetworkMonitor", "userId recovered on reconnect: ${userInfo.id}")
                } catch (e: Exception) {
                    Log.w("NetworkMonitor", "Failed to fetch user info", e)
                }
            }

            // 🔄 relancer la sync classique via SyncCoordinator (T4.2 Phase 4.1).
            // Ordre push-puis-merge (V4.4-B3) preserve dans SyncCoordinator.
            // onNetworkAvailable + retry exponentiel sur echec (3 tentatives).
            val entryPoint = EntryPointAccessors
                .fromApplication(context, SyncEntryPoint::class.java)

            entryPoint.syncCoordinator().onNetworkAvailable()

            SyncEvents.onReconnected.emit(Unit)

            // 🔄 relancer le WebSocket
            val wsManager: WebSocketManager = entryPoint.webSocketManager()
            val token = TokenManager.token
            if (token != null) {
                Log.d("NetworkMonitor", "restart WebSocket with fresh token")
                wsManager.start(token) // ← reset la connexion WS
            } else {
                Log.w("NetworkMonitor", "no token, cannot restart WebSocket")
                showSnackbar(
                    message = context.getString(com.example.sportapp.R.string.network_no_token_ws),
                    type = SnackbarType.WARNING,
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

    fun start() {
        // On ne suit que les reseaux physiques offrant un acces internet :
        // INTERNET + NOT_VPN (exclut Tailscale tun0, cf. commentaire en tete).
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        // Etat initial reel : pre-peuple le set avec les reseaux physiques deja
        // presents. Au registerNetworkCallback, Android re-fire onAvailable pour
        // chacun ; comme le Set est idempotent et isOnline deja a true, ces
        // re-fire sont vus "already online" -> pas de reconnexion redondante au
        // boot (la sync initiale est geree par le flow login/Splash).
        seedAvailableNetworks()
        isOnline = availableNetworks.isNotEmpty()
        SyncEvents.isNetworkAvailable.value = isOnline
        Log.d("NetworkMonitor", "Initial network state = $isOnline (n=${availableNetworks.size})")

        cm.registerNetworkCallback(request, callback)
    }

    fun stop() {
        cm.unregisterNetworkCallback(callback)
        offlineJob?.cancel()
        availableNetworks.clear()
    }

    /**
     * Pre-peuple [availableNetworks] avec les reseaux physiques actuellement
     * disponibles (INTERNET + NOT_VPN, memes capabilities que le [NetworkRequest]).
     */
    private fun seedAvailableNetworks() {
        availableNetworks.clear()
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            ) {
                availableNetworks.add(network)
            }
        }
    }

    private companion object {
        const val OFFLINE_DEBOUNCE_MS = 1500L
    }
}
