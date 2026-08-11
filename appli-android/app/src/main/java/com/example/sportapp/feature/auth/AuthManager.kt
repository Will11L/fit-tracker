package com.example.sportapp.feature.auth

import android.content.Context
import android.util.Log
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.data.remote.WebSocketManager
import com.example.sportapp.core.network.TokenManager
import com.example.sportapp.core.sync.RemoteDataMerger
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    private val wsManager: WebSocketManager,
    private val remoteDataMerger: RemoteDataMerger,
    @ApplicationContext private val appContext: Context
) {
    sealed class AuthState {
        data object Authenticated : AuthState()
        data object NeedLogin : AuthState()
        data object Offline : AuthState()
    }

    suspend fun initAuth(): AuthState {
        try {
            val token = TokenManager.token

            // Pas de token -> login
            if (token.isNullOrBlank()) {
                return AuthState.NeedLogin
            }

            // Token présent -> vérifier la session via /me.
            val isValid = RetrofitInstance.verifyToken()
            if (!isValid) {
                // /me a échoué. Deux causes à NE PAS confondre :
                //  - Le serveur a rejeté la session (401/403 + refresh échoué) :
                //    l'Authenticator OkHttp a déjà clear les tokens -> token null
                //    -> session réellement morte -> re-login.
                //  - Erreur réseau/transport (pas de réseau, timeout, socket mort,
                //    Tailscale/wifi pas encore up au lancement) : verifyToken()
                //    avale l'IOException en `false` mais les tokens sont intacts
                //    -> session potentiellement valide -> mode offline, PAS de
                //    re-login forcé (bug "session expirée" au relancement sur un
                //    simple blip réseau).
                return if (TokenManager.token.isNullOrBlank()) {
                    AuthState.NeedLogin
                } else {
                    AuthState.Offline
                }
            }

            // Token OK -> websocket
            wsManager.start(token)

            // V4.4 — Pull initial des donnees serveur. Sur un nouveau device
            // (premier login), Room est vide -> sans ce merge l'app affiche
            // des donnees vides jusqu'au premier evenement WS / reconnexion
            // reseau (qui peut ne jamais survenir si le reseau est stable).
            // try/catch defensive : un merge KO ne doit pas bloquer le login.
            try {
                remoteDataMerger.mergeAllFromServer()
            } catch (e: Exception) {
                Log.w("AuthManager", "Initial mergeAllFromServer failed: ${e.message}")
            }

            return AuthState.Authenticated

        } catch (e: Exception) {
            // V6.4-D3 : sentinel -1 retire au profit de clearUserId() (= null) -
            // semantique plus claire (pas de user vs user "id 0/-1" bidon).
            CurrentUserManager.clearUserId(appContext)
            showSnackbar("No network - Offline mode", type = SnackbarType.WARNING)
            return AuthState.Offline
        }
    }

    suspend fun stopAuth() {
        // V8.2 : best-effort POST /logout pour revoquer le refresh cote
        // serveur avant de clear local. On ignore les erreurs (network
        // KO, refresh deja revoque, etc.) - le clear local doit toujours
        // se faire pour permettre le logout.
        val refresh = TokenManager.refreshToken
        if (!refresh.isNullOrBlank()) {
            try {
                RetrofitInstance.authApi.logout(com.example.sportapp.core.network.RefreshRequest(refresh))
            } catch (e: Exception) {
                Log.w("AuthManager", "POST /logout failed (ignored): ${e.message}")
            }
        }
        wsManager.stop()
        TokenManager.clearToken(appContext)
        CurrentUserManager.clearUserId(appContext)
    }


}
