package com.example.sportapp.core.data.remote

import android.content.Context
import android.util.Log
import com.example.sportapp.core.utils.AppConfig
import com.example.sportapp.core.network.ClientIdProvider
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.network.TokenManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

import org.json.JSONObject

@Singleton
class WebSocketManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val actualWorkoutHandler: ActualWorkoutSyncHandler,
    private val actualWorkoutExerciseHandler: ActualWorkoutExerciseSyncHandler,
    private val actualWorkoutSetHandler: ActualWorkoutSetSyncHandler,
    private val availableEquipmentHandler: AvailableEquipmentSyncHandler,
    private val equipmentHandler: EquipmentSyncHandler,
    private val exerciseEquipmentHandler: ExerciseEquipmentSyncHandler,
    private val cycleWorkoutHandler: CycleWorkoutSyncHandler,
    private val exerciseMuscleHandler: ExerciseMuscleSyncHandler,
    private val muscleGoalHandler: MuscleGoalSyncHandler,
    private val plannedWorkoutExerciseHandler: PlannedWorkoutExerciseSyncHandler,
    private val plannedWorkoutHandler: PlannedWorkoutSyncHandler,
    private val supersetExerciseHandler: SupersetExerciseSyncHandler,
    private val supersetGroupHandler: SupersetGroupSyncHandler,
    private val trainingCycleHandler: TrainingCycleSyncHandler,

    private val muscleHandler: MuscleSyncHandler,
    private val exerciseHandler: ExerciseSyncHandler,

    // Phase 0 (2026-05-12) : Task unification
    private val taskHandler: TaskSyncHandler,
    private val taskCheckHandler: TaskCheckSyncHandler,

    private val quoteHandler: QuoteSyncHandler,

    // Nutrition A1 (2026-06-17) : realtime WS pour les 8 entites nutrition
    private val foodHandler: FoodSyncHandler,
    private val foodPortionHandler: FoodPortionSyncHandler,
    private val mealPresetHandler: MealPresetSyncHandler,
    private val mealHandler: MealSyncHandler,
    private val mealEntryHandler: MealEntrySyncHandler,
    private val nutritionGoalHandler: NutritionGoalSyncHandler,
    private val recipeHandler: RecipeSyncHandler,
    private val recipeIngredientHandler: RecipeIngredientSyncHandler,

    // Santé / Health Connect V1 (2026-06-17) : realtime WS pour les 3 entites sante
    private val healthStepCountHandler: HealthStepCountSyncHandler,
    private val healthMetricHandler: HealthMetricSyncHandler,
    private val healthGoalHandler: HealthGoalSyncHandler,

    // Hydratation (2026-07-05)
    private val waterIntakeHandler: WaterIntakeSyncHandler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    // Lu a chaque start() (pas a l'instanciation Hilt) pour refleter l'URL
    // actuellement en vigueur dans AppConfig (qui peut etre modifiee en debug
    // via Settings -> Server URL avec restart de l'app).
    private val wsBaseUrl: String get() = AppConfig.WS_BASE_URL

    private var lastToken: String? = null

    // Fix 2026-07-07 (403 : token expire jamais rafraichi) : la politique de
    // reconnexion vit dans WsReconnector (testable JVM). Le tokenProvider relit
    // TokenManager.token a CHAQUE tentative (token frais si l'Authenticator REST
    // a deja refresh) ; sur handshake 401/403, refresh proactif via
    // RetrofitInstance.refreshAccessToken (le client OkHttp du WS n'a pas
    // d'Authenticator) ; retry borne avec backoff au lieu du one-shot.
    private val reconnector = WsReconnector(
        scope = scope,
        tokenProvider = { TokenManager.token ?: lastToken },
        tokenRefresher = { stale -> RetrofitInstance.refreshAccessToken(stale) },
        connect = { token -> start(token, resetRetry = false) },
    )

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    fun start(token: String, resetRetry: Boolean = true) {
        lastToken = token
        if (resetRetry) reconnector.reset()

        // V4.4 — Ferme l'ancienne connexion si on reconnecte (sinon leak).
        // L'onClosed de cette ancienne WS sera ignore plus bas via le controle
        // d'identite (ws === webSocket) : quand il arrive (async), webSocket
        // pointe deja sur la NOUVELLE connexion. Remplace l'ancien flag
        // manualClose qui racait (remis a false synchroniquement avant
        // l'onClosed async -> reconnexion en boucle infinie).
        webSocket?.close(1000, "Reconnect")

        val clientId = ClientIdProvider.getClientId(context)

        // F5a-5 : OkHttp WS timeouts customisés
        // - pingInterval(30s) : keep-alive applicatif, évite les coupures NAT/proxy.
        // - readTimeout(0)    : OkHttp par défaut coupe une socket inactive après 10s ;
        //                      pour une WS qui reste idle longtemps entre 2 events, il
        //                      faut désactiver ce timeout (le ping/pong gère la santé).
        val client = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url("$wsBaseUrl?access_token=$token&client=android&v=1&client_id=$clientId")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                if (ws !== webSocket) return // callback d'une WS perimee
                Log.i("WebSocket", "✅ Connected")
                _isConnected.value = true
                reconnector.reset()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, r: Response?) {
                if (ws !== webSocket) return // callback d'une WS perimee
                Log.e("WebSocket", "❌ Failure: ${t.message} (handshake HTTP ${r?.code})")
                _isConnected.value = false
                reconnector.onDisconnected(r?.code, lastToken)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (ws !== webSocket) return // WS fermee par start()/stop() -> pas de reconnexion
                Log.w("WebSocket", "⚠️ Closed: $reason")
                _isConnected.value = false
                reconnector.onDisconnected(null, lastToken)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                if (ws !== webSocket) return // callback d'une WS perimee
                Log.d("WebSocket", "📩 Message reçu: $text")
                handleMessage(text)
            }
        })
    }

    fun stop() {
        // L'onClosed de cette WS sera ignore (ws !== webSocket qui passe a null).
        webSocket?.close(1000, "App closed")
        webSocket = null
        _isConnected.value = false
        reconnector.reset()
        scope.coroutineContext.cancelChildren()
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.getString("type")
            Log.d("WebSocket", "📦 Type détecté: $type")

            scope.launch {
                when (type) {
                    "actual_workout_updated", "actual_workout_deleted" -> actualWorkoutHandler.handle(json)
                    "actual_workout_exercise_updated", "actual_workout_exercise_deleted" -> actualWorkoutExerciseHandler.handle(json)
                    "actual_workout_set_updated", "actual_workout_set_deleted" -> actualWorkoutSetHandler.handle(json)
                    "available_equipment_updated", "available_equipment_deleted" -> availableEquipmentHandler.handle(json)
                    "equipment_updated", "equipment_deleted" -> equipmentHandler.handle(json)
                    "exercise_equipment_updated", "exercise_equipment_deleted" -> exerciseEquipmentHandler.handle(json)
                    "cycle_workout_updated", "cycle_workout_deleted" -> cycleWorkoutHandler.handle(json)
                    "exercise_muscle_updated", "exercise_muscle_deleted" -> exerciseMuscleHandler.handle(json)
                    "muscle_goal_updated", "muscle_goal_deleted" -> muscleGoalHandler.handle(json)
                    "planned_workout_exercise_updated", "planned_workout_exercise_deleted" -> plannedWorkoutExerciseHandler.handle(json)
                    "planned_workout_updated", "planned_workout_deleted" -> plannedWorkoutHandler.handle(json)
                    "superset_exercise_updated", "superset_exercise_deleted" -> supersetExerciseHandler.handle(json)
                    "superset_group_updated", "superset_group_deleted" -> supersetGroupHandler.handle(json)
                    "training_cycle_updated", "training_cycle_deleted" -> trainingCycleHandler.handle(json)

                    "muscle_updated", "muscle_deleted" -> muscleHandler.handle(json)
                    "exercise_updated", "exercise_deleted" -> exerciseHandler.handle(json)

                    // Phase 0 (2026-05-12) : Task unification
                    "task_updated", "task_deleted" -> taskHandler.handle(json)
                    "task_check_updated", "task_check_deleted" -> taskCheckHandler.handle(json)

                    "quote_updated", "quote_deleted" -> quoteHandler.handle(json)

                    // Nutrition A1 (2026-06-17) : 8 entites nutrition
                    "food_updated", "food_deleted" -> foodHandler.handle(json)
                    "food_portion_updated", "food_portion_deleted" -> foodPortionHandler.handle(json)
                    "meal_preset_updated", "meal_preset_deleted" -> mealPresetHandler.handle(json)
                    "meal_updated", "meal_deleted" -> mealHandler.handle(json)
                    "meal_entry_updated", "meal_entry_deleted" -> mealEntryHandler.handle(json)
                    "nutrition_goal_updated", "nutrition_goal_deleted" -> nutritionGoalHandler.handle(json)
                    "recipe_updated", "recipe_deleted" -> recipeHandler.handle(json)
                    "recipe_ingredient_updated", "recipe_ingredient_deleted" -> recipeIngredientHandler.handle(json)

                    // Santé / Health Connect V1 (2026-06-17) : 3 entites sante
                    "health_step_count_updated", "health_step_count_deleted" -> healthStepCountHandler.handle(json)
                    "health_metric_updated", "health_metric_deleted" -> healthMetricHandler.handle(json)
                    "health_goal_updated", "health_goal_deleted" -> healthGoalHandler.handle(json)

                    // Hydratation (2026-07-05)
                    "water_intake_updated", "water_intake_deleted" -> waterIntakeHandler.handle(json)

                    else -> Log.w("WebSocket", "⚠️ Type inconnu (pas de handler) : $type")
                }
            }
        } catch (e: Exception) {
            Log.e("WebSocket", "❌ Erreur parse/handle", e)
        }
    }

}
