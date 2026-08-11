package com.example.sportapp.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import com.example.sportapp.BuildConfig

/**
 * Repository singleton pour le switch d'URL serveur (admin users uniquement).
 *
 * Expose les 3 presets + l'URL custom, calcule les URLs effectives (REST + WS),
 * persiste via [ServerUrlDataStore]. La section UI vit dans SettingsScreen,
 * gated `if (isAdmin)` -- cf. CurrentUserManager.isAdminFlow. AppConfig.init
 * lit ce DataStore au boot dans tous les build variants.
 *
 * Pattern : lu au boot par [com.example.sportapp.core.utils.AppConfig.init] pour
 * peupler les URLs effectives AVANT que RetrofitInstance / WebSocketManager
 * ne les capturent. Tout changement necessite un restart de l'app (les
 * `by lazy { buildApi(...) }` du RetrofitInstance figent l'URL).
 */
@Singleton
class ServerUrlRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    val dataStore: ServerUrlDataStore,
) {

    val snapshot: Flow<ServerUrlDataStore.Snapshot> = dataStore.snapshotFlow

    suspend fun current(): ServerUrlDataStore.Snapshot = dataStore.snapshot()

    suspend fun setPreset(preset: ServerUrlPreset) = dataStore.setPreset(preset)

    suspend fun setCustomUrl(url: String) = dataStore.setCustomUrl(url)

    /**
     * Resout les URLs effectives (REST + WS) selon le snapshot courant.
     *
     * - PC_LAN  : presets hardcodes (correspondent a debug build defaults).
     * - PI_PROD : presets hardcodes (correspondent a release build defaults).
     * - CUSTOM  : derive de [Snapshot.customUrl] (normalisation + WS scheme).
     *
     * En cas d'URL custom invalide/blanche : fallback sur Pi prod (preset par
     * defaut). Pas de raise -- l'app doit booter meme sur conf incoherente.
     */
    fun resolveUrls(snapshot: ServerUrlDataStore.Snapshot): ResolvedUrls {
        return when (snapshot.preset) {
            ServerUrlPreset.PC_LAN -> ResolvedUrls(
                api = PC_LAN_API,
                ws = PC_LAN_WS,
            )
            ServerUrlPreset.PI_PROD -> ResolvedUrls(
                api = PI_PROD_API,
                ws = PI_PROD_WS,
            )
            ServerUrlPreset.CUSTOM -> {
                val raw = snapshot.customUrl.trim()
                if (raw.isBlank()) {
                    ResolvedUrls(api = PI_PROD_API, ws = PI_PROD_WS)
                } else {
                    val (api, ws) = normalize(raw)
                    ResolvedUrls(api = api, ws = ws)
                }
            }
        }
    }

    data class ResolvedUrls(val api: String, val ws: String)

    companion object {
        // Hotes injectes au build depuis local.properties (cf. app/build.gradle.kts),
        // pour que l'infrastructure reelle ne soit pas dans le code source.
        val PC_LAN_API: String = BuildConfig.PC_LAN_API
        val PC_LAN_WS: String = BuildConfig.PC_LAN_WS
        val PI_PROD_API: String = BuildConfig.PI_PROD_API
        val PI_PROD_WS: String = BuildConfig.PI_PROD_WS

        /**
         * Normalise une URL custom saisie par le user :
         * - Si pas de scheme, prefixe `http://`.
         * - Assure trailing `/api/v1/` pour l'API + derive le WS scheme
         *   (http -> ws, https -> wss) avec endpoint `/api/v1/ws`.
         *
         * Exemples :
         *   "192.168.1.50:8000" -> ("http://192.168.1.50:8000/api/v1/", "ws://192.168.1.50:8000/api/v1/ws")
         *   "https://example.com" -> ("https://example.com/api/v1/", "wss://example.com/api/v1/ws")
         *   "http://host:8000/api/v1/" -> idem (idempotent)
         */
        fun normalize(raw: String): Pair<String, String> {
            val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) {
                raw
            } else {
                "http://$raw"
            }
            // Strip trailing slashes pour eviter "/api/v1//"
            val base = withScheme.trimEnd('/')
            // Cherche si l'URL contient deja /api/v1
            val apiBase = if (base.endsWith("/api/v1")) base else "$base/api/v1"
            val api = "$apiBase/"
            val ws = apiBase.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://") + "/ws"
            return api to ws
        }
    }
}
