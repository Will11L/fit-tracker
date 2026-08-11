package com.example.sportapp.core.network

import android.content.Context
import android.util.Log
import androidx.compose.material3.SnackbarDuration
import com.example.sportapp.core.sync.SyncEvents
import com.example.sportapp.core.utils.AppConfig
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Résultat d'une tentative de login (cf. [RetrofitInstance.login]). Distingue
 * un refus d'identifiants (le serveur a répondu 401/400) d'une erreur réseau
 * transitoire, pour ne pas afficher "identifiants incorrects" alors que c'est
 * juste le réseau (symptôme B : login qui échoue après une reconnexion réseau).
 */
sealed class LoginResult {
    data object Success : LoginResult()
    data object InvalidCredentials : LoginResult()
    data object NetworkError : LoginResult()
}

object RetrofitInstance {
    // Lu a l'init de l'object (= 1er touch via initialize() ou .authApi).
    // En debug, AppConfig.init(context) doit avoir tourne avant pour que
    // BASE_URL refleche le preset choisi par l'user (cf. SportApp.onCreate).
    private val BASE_URL: String get() = AppConfig.API_BASE_URL

    // Auto-retry borne (1x) sur IOException pour les requetes auth idempotentes.
    // Motivation : apres une periode d'inactivite, un socket keep-alive du pool
    // OkHttp peut etre MORT cote serveur/proxy/NAT sans que le client le sache
    // (half-open). La 1re requete reutilise ce socket -> elle part dans le vide
    // et hang jusqu'au timeout -> IOException. Aucun event reseau ne fire dans ce
    // cas (l'app n'a pas change de reseau), donc l'eviction-sur-reconnexion de
    // NetworkMonitor ne le couvre PAS. Ici on catch l'IOException, on evince le
    // pool et on reessaie UNE fois sur une connexion fraiche -> l'user ne re-clique
    // jamais (symptome "login qui echoue puis remarche au 2e essai").
    //
    // Scope volontairement etroit (blast radius maitrise) : SEULEMENT POST /token
    // (login, retry sans effet de bord) et GET /me (idempotent). PAS /refresh (le
    // serveur fait rotation + reuse-detection : un retry apres un refresh
    // partiellement applique revoquerait TOUTES les sessions). PAS les mutations
    // de donnees metier (POST/PUT/PATCH/DELETE) -> risque de doublons.
    private fun shouldAutoRetry(request: Request): Boolean {
        val last = request.url.pathSegments.lastOrNull() ?: return false
        return when (request.method) {
            "POST" -> last == "token"
            "GET" -> last == "me"
            else -> false
        }
    }

    private val autoRetryInterceptor = Interceptor { chain ->
        val request = chain.request()
        if (!shouldAutoRetry(request)) {
            chain.proceed(request)
        } else {
            try {
                chain.proceed(request)
            } catch (e: IOException) {
                Log.w(
                    "Auth",
                    "Auth request ${request.method} /${request.url.pathSegments.joinToString("/")} " +
                        "failed (${e.javaClass.simpleName}: ${e.message}) -> evict pool + retry once"
                )
                evictConnections()
                chain.proceed(request)
            }
        }
    }

    // Client des endpoints d'auth (signup / token / refresh / logout).
    // Timeout porté à 20s (défaut OkHttp : 10s) pour absorber la latence de
    // réveil radio (RRC) des réseaux mobiles : la 1re requête après une période
    // d'inactivité 4G/5G peut prendre plusieurs secondes rien que pour établir
    // le lien. Le serveur répond en ~0,5s -- ce n'est pas lui le facteur
    // limitant, c'est le lien radio au réveil.
    private val unauthenticatedClient = OkHttpClient.Builder()
        // connect garde 20s : absorbe le reveil radio (RRC) mobile sur une
        // connexion NEUVE (4G/5G au repos -> plusieurs secondes pour etablir le
        // lien). Ne PAS reduire (sinon faux echecs sur reseau mobile lent).
        .connectTimeout(20, TimeUnit.SECONDS)
        // read/write reduits 20s -> 10s : login/refresh renvoient une petite
        // reponse rapide (~0,5s serveur). Si un socket keep-alive MORT du pool est
        // reutilise (apres inactivite), ce timeout borne le "hang dans le vide"
        // avant que l'autoRetryInterceptor n'evince le pool et reessaie sur une
        // connexion fraiche -- au lieu de 20s d'attente cote user.
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(autoRetryInterceptor)
        .build()

    private val gson = com.google.gson.GsonBuilder()
        .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.IDENTITY)
        .create()

    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }
    // 👇 ajoute ça
    fun getAppContext(): Context {
        return appContext
    }

    private val retrofitNoAuth = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(unauthenticatedClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val authApi: AuthApi = retrofitNoAuth.create(AuthApi::class.java)

    // === Interceptor JWT ===
    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = TokenManager.token

        if (token.isNullOrBlank()) {
            return@Interceptor chain.proceed(original)
        }

        val requestWithToken = original.newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()

        chain.proceed(requestWithToken)
    }


    // === Interceptor ClientId ===
    private val clientIdInterceptor = Interceptor { chain ->
        val original = chain.request()
        val method = original.method.uppercase()
        val needsHeader = method == "POST" || method == "PUT" || method == "PATCH" || method == "DELETE"

        val request = if (needsHeader) {
            val clientId = ClientIdProvider.getClientId(appContext)
            original.newBuilder()
                .addHeader("X-Client-Id", clientId)
                .build()
        } else {
            original
        }

        chain.proceed(request)
    }

    // === Authenticator 401 ===
    // V8.2 — Refresh flow. Sur 401 :
    //   1. Si la requete originale n'avait pas d'Authorization -> null (pas
    //      de retry, sinon boucle infinie).
    //   2. Si on a un refresh_token : tenter POST /refresh sous mutex (un
    //      seul refresh concurrent meme si 21 GET parallels 401 simultanes,
    //      pour eviter reuse detection cote serveur).
    //   3. Si refresh OK -> retry la requete originale avec le new access.
    //   4. Si refresh KO (refresh expire/revoke) -> clear tout + emit
    //      onTokenExpired + null (comportement V4.5).
    private val refreshMutex = Mutex()

    private val authAuthenticator = Authenticator { _: Route?, response: Response ->
        val original = response.request
        val sentToken = original.header("Authorization")?.removePrefix("Bearer ")
        if (sentToken.isNullOrBlank()) return@Authenticator null


        // Refresh sous mutex : un seul appel /refresh concurrent. Les autres
        // threads attendent et retry avec le nouveau token (qui aura ete
        // mis a jour entre-temps par le thread vainqueur).
        runBlocking {
            refreshMutex.withLock {
                // Si un autre thread a deja refresh, le TokenManager.token
                // a change entre temps -> retry avec le nouveau, pas besoin
                // de re-refresh.
                val currentToken = TokenManager.token
                if (!currentToken.isNullOrBlank() && currentToken != sentToken) {
                    return@runBlocking buildRetryWith(original, currentToken)
                }

                val newAccess = refreshTokensLocked()
                if (newAccess != null) buildRetryWith(original, newAccess) else null
            }
        }
    }

    /**
     * Coeur du refresh V8.2 -- DOIT etre appele sous [refreshMutex] (mutex non
     * reentrant, ne jamais appeler depuis un bloc deja lock). Retourne le
     * nouveau access token, ou null :
     *  - refresh token absent, ou rejete 401/403 -> session morte, force logout ;
     *  - autre code HTTP (5xx...) -> pas un probleme d'auth, tokens conserves ;
     *  - erreur reseau -> evict pool + tokens conserves (reessai plus tard).
     */
    private suspend fun refreshTokensLocked(): String? {
        val refresh = TokenManager.refreshToken
        if (refresh.isNullOrBlank()) {
            forceLogoutAndReturnNull()
            return null
        }

        return try {
            val pair = authApi.refresh(RefreshRequest(refresh))
            TokenManager.setTokens(appContext, pair.access_token, pair.refresh_token)
            _isTokenValid.value = true
            pair.access_token
        } catch (e: HttpException) {
            // Le serveur a repondu : seul un 401/403 signifie que le
            // refresh token est reellement invalide/revoque -> session
            // morte, on force le logout. Tout autre code (5xx...) n'est
            // PAS un probleme d'auth -> on abandonne ce retry SANS
            // detruire les tokens (reessai au prochain appel reseau OK).
            if (e.code() == 401 || e.code() == 403) {
                Log.w("Auth", "Refresh rejected by server (HTTP ${e.code()}) -> force logout")
                forceLogoutAndReturnNull()
            } else {
                Log.w("Auth", "Refresh failed (HTTP ${e.code()}) -> give up retry, keep tokens")
            }
            null
        } catch (e: IOException) {
            // Erreur reseau/transport pendant le refresh (pas de reseau,
            // timeout, socket mort du pool) : le refresh token est tres
            // probablement encore valide -> NE PAS le detruire ni forcer
            // le logout. On evince le pool pour que le PROCHAIN /refresh
            // parte sur une connexion fraiche, puis on abandonne ce retry.
            // On ne retry PAS /refresh inline : le serveur fait rotation +
            // reuse-detection, un retry apres un refresh partiellement
            // applique revoquerait TOUTES les sessions. La session est
            // reessayee au prochain appel (connexion fraiche) ou au retour
            // reseau (sinon un simple blip ejecterait l'user).
            Log.w("Auth", "Refresh failed (network: ${e.message}) -> evict pool, give up retry, keep tokens")
            evictConnections()
            null
        }
    }

    /**
     * Refresh explicite du JWT access token, pour les flux qui ne passent PAS
     * par l'Authenticator OkHttp -- typiquement le handshake WebSocket : son
     * OkHttpClient n'a pas d'Authenticator, donc un 401/403 au handshake ne
     * declenche jamais le refresh REST (cf. WebSocketManager / WsReconnector,
     * fix 2026-07-07). Meme [refreshMutex] que l'Authenticator -> un seul
     * POST /refresh concurrent (rotation + reuse-detection cote serveur).
     *
     * [staleToken] = le token qui vient d'etre rejete par le serveur : si le
     * token courant est deja different (refresh deja fait entre-temps par le
     * flux REST), on le retourne directement sans re-appeler /refresh.
     *
     * @return le nouveau access token, ou null si le refresh a echoue
     *         (reseau KO -> tokens conserves ; 401/403 -> force logout).
     */
    suspend fun refreshAccessToken(staleToken: String? = null): String? {
        return refreshMutex.withLock {
            val currentToken = TokenManager.token
            if (!currentToken.isNullOrBlank() && staleToken != null && currentToken != staleToken) {
                return@withLock currentToken
            }
            refreshTokensLocked()
        }
    }

    private fun buildRetryWith(original: Request, newAccess: String): Request {
        return original.newBuilder()
            .header("Authorization", "Bearer $newAccess")
            .build()
    }

    private fun forceLogoutAndReturnNull(): Request? {
        TokenManager.clearToken(appContext)
        CurrentUserManager.clearUserId(appContext)
        _isTokenValid.value = false
        SyncEvents.onTokenExpired.tryEmit(Unit)
        return null
    }

    // === Client Authentifié ===
    private val authenticatedClient = OkHttpClient.Builder()
        // Outermost : retry IOException pour GET /me (socket mort du pool). Doit
        // englober les interceptors auth/clientId pour que la requete rejouee
        // re-injecte le bearer courant.
        .addInterceptor(autoRetryInterceptor)
        .addInterceptor(authInterceptor)
        .addInterceptor(clientIdInterceptor)
        .authenticator(authAuthenticator)
        .build()

    // === APIs ===
    val actualWorkoutExerciseApi: ActualWorkoutExerciseApi by lazy { buildApi(ActualWorkoutExerciseApi::class.java) }
    val actualWorkoutApi: ActualWorkoutApi by lazy { buildApi(ActualWorkoutApi::class.java) }
    val actualWorkoutSetApi: ActualWorkoutSetApi by lazy { buildApi(ActualWorkoutSetApi::class.java) }
    val availableEquipmentApi: AvailableEquipmentApi by lazy { buildApi(AvailableEquipmentApi::class.java) }
    val cycleWorkoutApi: CycleWorkoutApi by lazy { buildApi(CycleWorkoutApi::class.java) }
    val equipmentApi: EquipmentApi by lazy { buildApi(EquipmentApi::class.java) }
    val exerciseApi: ExerciseApi by lazy { buildApi(ExerciseApi::class.java) }
    val exerciseEquipmentApi: ExerciseEquipmentApi by lazy { buildApi(ExerciseEquipmentApi::class.java) }
    val exerciseMuscleApi: ExerciseMuscleApi by lazy { buildApi(ExerciseMuscleApi::class.java) }
    val muscleApi: MuscleApi by lazy { buildApi(MuscleApi::class.java) }
    val muscleGoalApi: MuscleGoalApi by lazy { buildApi(MuscleGoalApi::class.java) }
    val notificationApi: NotificationApi by lazy { buildApi(NotificationApi::class.java) }
    val plannedWorkoutApi: PlannedWorkoutApi by lazy { buildApi(PlannedWorkoutApi::class.java) }
    val plannedWorkoutExerciseApi: PlannedWorkoutExerciseApi by lazy { buildApi(PlannedWorkoutExerciseApi::class.java) }
    val quoteApi: QuoteApi by lazy { buildApi(QuoteApi::class.java) }
    val routinePeriodApi: RoutinePeriodApi by lazy { buildApi(RoutinePeriodApi::class.java) }
    // Phase 0 (2026-05-12) : remplace routineTaskApi / routineTaskCheckApi par Task unifie.
    val taskApi: TaskApi by lazy { buildApi(TaskApi::class.java) }
    val taskCheckApi: TaskCheckApi by lazy { buildApi(TaskCheckApi::class.java) }
    val supersetExerciseApi: SupersetExerciseApi by lazy { buildApi(SupersetExerciseApi::class.java) }
    val supersetGroupApi: SupersetGroupApi by lazy { buildApi(SupersetGroupApi::class.java) }
    val trainingCycleApi: TrainingCycleApi by lazy { buildApi(TrainingCycleApi::class.java) }
    val userApi: UserApi by lazy { buildApi(UserApi::class.java) }

    // Nutrition A1 (2026-06-17)
    val foodApi: FoodApi by lazy { buildApi(FoodApi::class.java) }
    val foodPortionApi: FoodPortionApi by lazy { buildApi(FoodPortionApi::class.java) }
    val mealPresetApi: MealPresetApi by lazy { buildApi(MealPresetApi::class.java) }
    val mealApi: MealApi by lazy { buildApi(MealApi::class.java) }
    val mealEntryApi: MealEntryApi by lazy { buildApi(MealEntryApi::class.java) }
    val nutritionGoalApi: NutritionGoalApi by lazy { buildApi(NutritionGoalApi::class.java) }
    val recipeApi: RecipeApi by lazy { buildApi(RecipeApi::class.java) }
    val recipeIngredientApi: RecipeIngredientApi by lazy { buildApi(RecipeIngredientApi::class.java) }
    // Nutrition A3 (2026-06-17) : proxy Open Food Facts (read-only, pas une entité syncée).
    val nutritionOffApi: NutritionOffApi by lazy { buildApi(NutritionOffApi::class.java) }

    // Santé / Health Connect V1 (2026-06-17)
    val healthStepCountApi: HealthStepCountApi by lazy { buildApi(HealthStepCountApi::class.java) }
    val healthMetricApi: HealthMetricApi by lazy { buildApi(HealthMetricApi::class.java) }
    val healthGoalApi: HealthGoalApi by lazy { buildApi(HealthGoalApi::class.java) }

    // Hydratation (2026-07-05)
    val waterIntakeApi: WaterIntakeApi by lazy { buildApi(WaterIntakeApi::class.java) }
    val adminApi: com.example.sportapp.feature.admin.data.AdminApi by lazy { buildApi(com.example.sportapp.feature.admin.data.AdminApi::class.java) }

    private fun <T> buildApi(apiClass: Class<T>): T {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(authenticatedClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(apiClass)
    }

    // === Token handling ===

    val userService: ApiUserService by lazy { buildApi(ApiUserService::class.java) }

    // Cas C — Agent IA in-app (Phase 2 MCP). POST /agent/chat, JWT injecté par
    // l'authInterceptor. La boucle tool-use + la clé Anthropic vivent côté Pi.
    val agentService: ApiAgentService by lazy { buildApi(ApiAgentService::class.java) }

    private val _isTokenValid = MutableStateFlow(false)
    val isTokenValid: StateFlow<Boolean> = _isTokenValid

    suspend fun login(username: String, password: String): LoginResult {
        return try {
            val response = authApi.getToken(username, password)
            TokenManager.setTokens(appContext, response.access_token, response.refresh_token)
            _isTokenValid.value = true
            LoginResult.Success
        } catch (e: HttpException) {
            // Le serveur a repondu : 401/400 = identifiants refuses. Tout autre
            // code (5xx...) = probleme serveur transitoire, pas la faute de l'user
            // -> on le traite comme une erreur reseau (message "reessayez").
            val code = e.code()
            val errorBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
            Log.e("Auth", "Login HTTP $code errorBody=$errorBody")
            TokenManager.clearToken(appContext)
            _isTokenValid.value = false
            if (code == 401 || code == 400) LoginResult.InvalidCredentials else LoginResult.NetworkError
        } catch (e: IOException) {
            // Timeout / pas de reseau / socket mort du pool apres reconnexion :
            // ce n'est PAS un probleme d'identifiants. Message dedie pour ne pas
            // faire croire a l'user que son mot de passe est faux (symptome B).
            Log.e("Auth", "Login network error for user=$username (BASE_URL=$BASE_URL): ${e.message}")
            TokenManager.clearToken(appContext)
            _isTokenValid.value = false
            LoginResult.NetworkError
        } catch (e: Exception) {
            Log.e("Auth", "Login unexpected error for user=$username", e)
            TokenManager.clearToken(appContext)
            _isTokenValid.value = false
            LoginResult.NetworkError
        }
    }

    suspend fun verifyToken(): Boolean {
        return try {
            val userInfo = userService.getUserInfo()
            CurrentUserManager.setUserId(appContext, userInfo.id)
            CurrentUserManager.setUserAdmin(appContext, userInfo.isAdmin)
            CurrentUserManager.setProfile(appContext, userInfo)
            _isTokenValid.value = true
            true
        } catch (e: Exception) {
            // Diagnosabilite (la release n'a pas de logging interceptor) : tracer
            // le type d'echec /me. Reste un return false -- initAuth distingue
            // ensuite session morte (token clear par l'Authenticator) vs offline
            // (token intact) via la nullite du token.
            Log.w("Auth", "verifyToken /me failed (${e.javaClass.simpleName}: ${e.message})")
            _isTokenValid.value = false
            false
        }
    }

    /**
     * Evince les connexions HTTP en cache des deux pools OkHttp. A appeler
     * quand le reseau physique vient de revenir (cf. NetworkMonitor.onReconnected) :
     * apres une coupure wifi/data, le pool garde des sockets keep-alive "morts"
     * lies a l'ancien reseau. La 1re requete qui reutilise un tel socket echoue
     * (timeout/reset) avant qu'OkHttp ne le detecte -> login/refresh qui "echoue
     * puis remarche au 2e essai". Evincer le pool force une connexion fraiche au
     * prochain appel. Best-effort.
     */
    fun evictConnections() {
        try {
            unauthenticatedClient.connectionPool.evictAll()
            authenticatedClient.connectionPool.evictAll()
        } catch (e: Exception) {
            Log.w("Auth", "evictConnections failed: ${e.message}")
        }
    }

}
