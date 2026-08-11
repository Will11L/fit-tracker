package com.example.sportapp.core.utils

import android.content.Context
import com.example.sportapp.BuildConfig
import com.example.sportapp.core.data.ServerUrlDataStore
import com.example.sportapp.core.data.ServerUrlRepository
import kotlinx.coroutines.runBlocking

/**
 * URLs effectives REST + WS de l'app.
 *
 * Surcharge possible via Settings -> "Server URL" (visible si user.is_admin)
 * qui ecrit un preset (PC_LAN / PI_PROD / CUSTOM) dans le DataStore
 * `server_url_settings`. La valeur est lue au boot via [init] (runBlocking
 * acceptable : 1er ms du process), AVANT que RetrofitInstance /
 * WebSocketManager ne capturent les URLs dans leurs `by lazy { ... }`.
 * Tout changement necessite un restart.
 *
 * Defaults : `BuildConfig.*_BASE_URL` -- garantit que l'app boote sans
 * dependance DataStore (typed never null) si [init] n'a pas encore tourne.
 */
object AppConfig {
    var API_BASE_URL: String = BuildConfig.API_BASE_URL
        private set

    var WS_BASE_URL: String = BuildConfig.WS_BASE_URL
        private set

    /**
     * Lit DataStore + applique le preset choisi. A appeler depuis
     * SportApp.onCreate AVANT RetrofitInstance.initialize.
     *
     * Aucun gating par build variant : la gate de visibilite UI vit dans
     * SettingsScreen (`if (isAdmin)`). Si un APK release a un DataStore
     * pre-rempli (typiquement parce que l'utilisateur etait admin lors du
     * dernier switch), on respecte ce choix.
     */
    fun init(context: Context) {
        // runBlocking au 1er ms du process : acceptable (DataStore read ~5-20ms),
        // pas d'alternative simple si on veut peupler avant RetrofitInstance.lazy.
        val snapshot = runBlocking {
            ServerUrlDataStore(context.applicationContext).snapshot()
        }
        // Petite duplication : on instancie un Repository transient juste pour
        // resoudre les URLs. Pas d'enjeu Hilt ici (1 appel au boot, pur calcul).
        val repo = ServerUrlRepository(context.applicationContext, ServerUrlDataStore(context.applicationContext))
        val resolved = repo.resolveUrls(snapshot)
        API_BASE_URL = resolved.api
        WS_BASE_URL = resolved.ws
    }
}
