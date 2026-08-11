# APPLI_ANDROID — Analyse détaillée de l'application mobile

> ⚠️ **DOC LARGEMENT FIGÉ AU 2026-05-04.** L'architecture sync a été **entièrement réécrite** depuis (T4.2 du 2026-05-07 : `SyncEngine` + `SyncRegistry` + `SyncCoordinator` remplacent `SyncManager` monolithique + `RemoteDataMerger`/`Getter`/`Upserter`, -1400 lignes nettes). Room v6/v7 dans le doc → réel **v18**. 4 modules feature ajoutés depuis non documentés : `admin/`, `onboarding/`, `chrono/`, `stats/`. Sections **toujours utilisées comme référence** : **§3D §2 squelette DAO Style A** (politique 9). Pour l'état courant : code `appli-android/app/src/main/java/com/example/sportapp/`, [ARCHITECTURE.md](ARCHITECTURE.md), [HOW_TO_ADD_ENTITY.md](HOW_TO_ADD_ENTITY.md), [DATABASES.md](DATABASES.md), historique [CLAUDE.md](../CLAUDE.md).

> Document construit en 4 sous-étapes (3A → 3B → 3C → 3D). Référence cousine : [PROJECT_MAP.md](PROJECT_MAP.md) (vue haute), [SERVEUR.md](SERVEUR.md) (côté backend), [INTEGRATION.md](INTEGRATION.md) (mapping serveur ↔ Android — étape 5).

## Sommaire

- **[3A. Infrastructure Android](#3a--infrastructure)** ✅ ce document
  - [1. Stack et arborescence du package](#1--stack-et-arborescence-du-package)
  - [2. Boot sequence : `SportApp.onCreate`](#2--boot-sequence--sportapponcreate)
  - [3. `MainActivity` + Navigation + deep links](#3--mainactivity--navigation--deep-links)
  - [4. Auth — TokenManager + AuthApi + CurrentUserManager](#4--auth--tokenmanager--authapi--currentusermanager)
  - [5. Networking — RetrofitInstance + ClientIdProvider + AppConfig](#5--networking--retrofitinstance--clientidprovider--appconfig)
  - [6. WebSocket — WebSocketManager](#6--websocket--websocketmanager)
  - [7. NetworkMonitor — détection online/offline + reconnexion](#7--networkmonitor--détection-onlineoffline--reconnexion)
  - [8. Persistance locale — AppDatabase + AppModule](#8--persistance-locale--appdatabase--appmodule-hilt)
  - [9. Storage — StorageManager + MuscleGoalsManager](#9--storage--storagemanager--musclegoalsmanager)
  - [10. UI infra — SnackbarController](#10--ui-infra--snackbarcontroller)
  - [11. SyncEntryPoint — Hilt EntryPoint pour briser circular deps](#11--syncentrypoint--hilt-entrypoint-pour-briser-circular-deps)
  - [12. Findings 3A](#12--findings-3a)
- **[3B. Architecture sync](#3b--architecture-sync)** ✅
- **[3C. UI (écrans, ViewModels, composants)](#3c--ui-viewmodels--écrans--modules-feature)** ✅
- **[3D. Modèles + DAOs Room + TypeConverters](#3d--modèles--daos-room--typeconverters)** ✅

---

# 3A — Infrastructure

## 1. Stack et arborescence du package

L'application Kotlin/Compose suit globalement une **architecture en couches horizontales** (sauf pour le module `notifications/` qui est en feature module — cf. PROJECT_MAP §6).

```
com.example.sportapp/
├── SportApp.kt              ← @HiltAndroidApp, init globaux
├── MainActivity.kt          ← @AndroidEntryPoint, NavHost, scaffold global
├── SnackbarController.kt    ← bus de snackbars global (singleton object)
│
├── network/                 ← Retrofit + auth/identité
│   ├── RetrofitInstance.kt
│   ├── TokenManager.kt
│   ├── ClientIdProvider.kt
│   ├── ApiUserService.kt + CurrentUserManager
│   ├── AuthApi.kt
│   └── *Api.kt              ← 22 APIs Retrofit (1 par entité)
│
├── data/
│   ├── model/               ← 22 entités Room
│   ├── local/               ← 22 DAOs + AppDatabase.kt
│   ├── remote/              ← WebSocketManager + 18 SyncHandlers
│   └── repository/          ← StorageManager + MuscleGoalsManager
│
├── sync/                    ← architecture sync (cf. 3B)
│   ├── base/
│   ├── syncables/
│   ├── SyncManager.kt
│   ├── RemoteData{Getter,Merger,Upserter}.kt
│   └── SyncEvents.kt
│
├── di/                      ← Hilt
│   ├── AppModule.kt         ← Provides DAOs
│   └── SyncEntryPoint.kt    ← EntryPoint pour briser les circular deps
│
├── ui/
│   ├── theme/               ← Theme, Type, couleurs
│   ├── screens/             ← 25 écrans Compose
│   └── components/          ← 100+ composants groupés par écran
│
├── viewmodel/               ← 20 ViewModels
│
├── notifications/           ← module dédié (Style A)
│   ├── data/
│   ├── domain/
│   ├── ui/
│   └── utils/
│
└── utils/                   ← AppConfig, TimeUtils, JsonUtils, NetworkMonitor, SnackbarUtils,
                              CustomDateUtils, VibrationUtils
```

### Dépendances clés (extrait `build.gradle.kts`)

| Catégorie | Lib + version |
|---|---|
| UI | Jetpack Compose (BOM via `libs.androidx.compose.bom`), Material 3 `1.4.0` |
| DI | Hilt 2.58, hilt-navigation-compose 1.3.0 |
| Persistance | Room 2.8.4 (runtime + ktx + compiler ksp), DataStore Preferences 1.2.0 |
| HTTP | Retrofit 2.9.0 + converter-gson 3.0.0 + OkHttp (transitif) |
| Charts | Vico (compose-m3 + core 2.4.1) |
| Drag & drop | reorderable 0.9.6 (burnoutcrew) |
| Pull-to-refresh | accompanist-swiperefresh 0.36.0 |
| Date | ThreeTenABP 1.4.9 (backport java.time pour minSdk) |
| Navigation | navigation-compose 2.9.6 |

`compileSdk = 36, targetSdk = 36, minSdk = 29`. Java 11 + Kotlin JVM 11.

---

## 2. Boot sequence : `SportApp.onCreate`

[appli-android/app/src/main/java/com/example/sportapp/SportApp.kt](../appli-android/app/src/main/java/com/example/sportapp/SportApp.kt)

```kotlin
@HiltAndroidApp
class SportApp : Application() {
    @Inject lateinit var wsManager: WebSocketManager
    private lateinit var monitor: NetworkMonitor

    override fun onCreate() {
        super.onCreate()

        AndroidThreeTen.init(this)              // ← polyfill java.time pour minSdk 29
        CurrentUserManager.init(applicationContext)  // ← lit user_id depuis SharedPrefs
        StorageManager.init(this)
        StorageManager.initUserMuscleStorage()  // ← crée filesDir/images/muscles si absent
        TokenManager.init(this)                  // ← lit JWT depuis SharedPrefs

        monitor = NetworkMonitor(this) {}
        monitor.start()                          // ← NetworkCallback → onAvailable / onLost

        RetrofitInstance.initialize(this)        // ← stocke applicationContext pour ClientIdProvider
    }

    override fun onTerminate() {
        super.onTerminate()
        monitor.stop()
        wsManager.stop()
    }
}
```

**Notes** :
- `wsManager` est injecté via Hilt mais **jamais démarré dans `onCreate`** : c'est `MainActivity` qui le démarre indirectement après login (via `SyncEntryPoint`).
- `NetworkMonitor` est instancié manuellement (pas par Hilt) avec `(this) {}` (callback `onReconnect` ignoré). Le vrai onReconnect logic est dans le `NetworkCallback` interne (cf. §7).

---

## 3. `MainActivity` + Navigation + deep links

[appli-android/app/src/main/java/com/example/sportapp/MainActivity.kt](../appli-android/app/src/main/java/com/example/sportapp/MainActivity.kt) (375 lignes)

### Structure

- `@AndroidEntryPoint` — Hilt prend le relais
- Demande la permission `POST_NOTIFICATIONS` (Android 13+)
- Gère un **deep link** `sportapp://notif/<route>?uuid=...` (par ex. `sportapp://notif/tasks?uuid=xyz`)
  - À l'intent reçu : pose la route dans un `MutableStateFlow`, qui déclenche la navigation après le composable rendu (via `LaunchedEffect`)
- Configure un **Scaffold global** :
  - `bottomBar` : `BottomNavBar` (caché sur splash/login/logout)
  - `drawer` : `ModalNavigationDrawer` avec `DrawerContent` (gestures off sur les écrans particuliers)
  - `snackbarHost` : custom `LazyColumn` qui anime les `SnackbarEvent` (slideIn/Out + fadeIn/Out)
- Mount des **overlays globaux** :
  - `NotificationOverlayHost` (du module `notifications/`)
  - `MiniChronoOverlay`, `MiniTimerOverlay` (chrono persistant pendant la nav)

### Navigation

`NavHost` avec **24 routes** :
- Auth : `login`, `splash`, `logout`
- Bottom nav : `home`, `chrono`, `stats`, `calendar`
- Drawer : `notifications`, `tasks`, `conversations`, `program`, `exercises`, `muscles`, `profile`, `settings`, `language_display`, `export_datas`, `sync_settings`, `delavier_method`
- Routes paramétrées : `planned_workout/{plannedWorkoutUUID}`, `session_exercise/{actualWorkoutExerciseUUID}`, `exercise/{exerciseUUID}`, `muscle/{muscleUUID}`, `session/{sessionUUID}`

**Choix de routing** : direction-aware transitions sur les 4 routes "rail" (`calendar < home < chrono < stats`) — slide gauche/droite selon la position relative. Les autres routes ont des slides standards.

### Findings nav

- ⚠ Le paramètre `session/{sessionUUID}` utilise le mot "session" malgré le rename → cohérent fonctionnellement (l'écran s'appelle `SessionTab` et affiche un `actual_workout`) mais nom historique. Cosmétique.
- ⚠ Routes hardcodées comme strings (`"login"`, `"home"`, etc.) — risque de typo. Pas de constants/enum centralisé.
- ⚠ Plusieurs routes ne sont pas authentifiées en garde : un `navController.navigate("home")` direct fonctionnerait sans token. La protection vient du fait que `startDestination` est calculé : `if (TokenManager.token.isNullOrBlank()) "login" else "splash"`. Mais une fois sur `splash`/`home`, rien ne vérifie la validité du token jusqu'au prochain appel API.

---

## 4. Auth — TokenManager + AuthApi + CurrentUserManager

### `TokenManager`
[appli-android/.../network/TokenManager.kt](../appli-android/app/src/main/java/com/example/sportapp/network/TokenManager.kt)

- Singleton `object` (statefull global)
- SharedPreferences `auth_prefs/jwt_token`
- API : `init(ctx)`, `setToken(ctx, token)`, `clearToken(ctx)`, `token` (getter)
- ⚠ **Token JWT stocké en clair** dans SharedPreferences — acceptable pour une app perso, mais lisible si le device est rooté ou via backup ADB. EncryptedSharedPreferences serait plus propre.
- ⚠ **Pas d'expiration check côté client** : le token expire après 30 min (cf. serveur), l'app le découvre seulement quand un appel API renvoie 401.

### `AuthApi`
[appli-android/.../network/AuthApi.kt](../appli-android/app/src/main/java/com/example/sportapp/network/AuthApi.kt)

```kotlin
interface AuthApi {
    @FormUrlEncoded
    @POST("token")
    suspend fun getToken(
        @Field("username") username: String,
        @Field("password") password: String
    ): TokenResponse
}
data class TokenResponse(val access_token: String, val token_type: String)
```

✅ Aligné avec le serveur (`POST /token`, OAuth2 form flow).

### `ApiUserService` + `CurrentUserManager`
[appli-android/.../network/ApiUserService.kt](../appli-android/app/src/main/java/com/example/sportapp/network/ApiUserService.kt)

```kotlin
data class UserInfo(
    val id: Int,
    val username: String,
    val role: String,    // ⚠ NON-NULLABLE
    val email: String
)

interface ApiUserService {
    @GET("me")
    suspend fun getUserInfo(): UserInfo
}

object CurrentUserManager {
    var userId: Int? // ← stocké en SharedPrefs user_prefs/user_id
    fun init/setUserId/clearUserId(...)
}
```

🔴 **Bug cross-stack confirmé** : Le serveur (`auth_router.py:54-58`) renvoie `{id, username, email}` — **pas de `role`**. Le client attend `role: String` non-nullable. Selon la version de Gson : soit `role` est null (et crash NPE plus tard), soit Gson lève une exception au parse. À tester en condition réelle. **Solutions** : soit ajouter `role` côté serveur (mais quelle valeur ? — vide), soit le retirer côté Android, soit le rendre `String?` nullable.

🟠 **`email` côté serveur est synthétique** (`f"{username}@sportapp.com"` cf. TODO_FIXES) — l'app affiche donc un email faux à l'utilisateur si elle l'expose dans ProfileScreen.

🟠 **`CurrentUserManager.userId: Int`** : l'app stocke le **`id` Postgres** du user (pas son uuid). C'est la **seule** dépendance au `id: Int` dans tout l'app — partout ailleurs c'est `uuid: String`. Cohérent puisque le user n'a pas d'uuid en SQLAlchemy (cf. seed et model). À auditer en 3D pour confirmer.

---

## 5. Networking — RetrofitInstance + ClientIdProvider + AppConfig

### `AppConfig`
```kotlin
object AppConfig {
    val API_BASE_URL: String = BuildConfig.API_BASE_URL  // injecté par Gradle selon variant
    val WS_BASE_URL: String = BuildConfig.WS_BASE_URL
}
```
✅ debug/release variants correctement câblés (déjà couvert en étape 1).

### `ClientIdProvider`
- Singleton `object`
- SharedPreferences `fittracker_prefs/client_id`
- UUID généré une seule fois et persistant (`getClientId(ctx)` retourne toujours le même)
- ✅ **Même UUID utilisé pour HTTP (`X-Client-Id`) ET WebSocket (`?client_id=...`)** → l'exclude broadcast côté serveur fonctionne correctement (le client n'est pas notifié de ses propres écritures)

### `RetrofitInstance` (déjà couvert en étape 1, recap)
- 2 OkHttpClient : un sans interceptor (pour `/token`, `/me`), un avec interceptors auth+clientId (pour les routes métier)
- 22 lazies créent 1 Retrofit + 1 Api par entité
- `authInterceptor` : ajoute `Authorization: Bearer <token>` si token présent
- `clientIdInterceptor` : ajoute `X-Client-Id` **uniquement sur POST/PUT/PATCH/DELETE** (correct — les GET ne déclenchent pas de trigger)
- ⚠ `gson` configuré avec `FieldNamingPolicy.IDENTITY` → noms de champs Kotlin identiques au JSON (donc le JSON doit utiliser snake_case OU les classes Kotlin doivent être en camelCase strict, à confirmer en 3D)
- `login()` met à jour `_isTokenValid: StateFlow<Boolean>` ; `verifyToken()` lance `userService.getUserInfo()` et met à jour `CurrentUserManager.userId`

### `network_security_config.xml`
```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false"><pi-lan-ip></domain>  <!-- Pi locale -->
        <domain includeSubdomains="false"><pc-lan-ip></domain>  <!-- PC dev -->
    </domain-config>
</network-security-config>
```
✅ HTTPS strict pour `<public-dns>` (couvert par le défaut Android pour la prod), HTTP autorisé seulement sur les 2 IP locales.

---

## 6. WebSocket — `WebSocketManager`

[appli-android/.../data/remote/WebSocketManager.kt](../appli-android/app/src/main/java/com/example/sportapp/data/remote/WebSocketManager.kt)

### Architecture

- `@Singleton @Inject constructor` avec **17 SyncHandlers injectés** (un par entité syncable côté WS)
- URL : `ws://...?access_token=$token&client=android&client_id=$clientId`
- État exposé : `isConnected: StateFlow<Boolean>`
- `start(token, resetRetry=true)` : ouvre la connexion, accepte un `resetRetry=false` pour la 2e tentative
- `stop()` : ferme proprement (code 1000) + cancel les coroutines
- **Reconnexion** : 1 seule retry après 3s. Si la 2e échoue, le WS reste fermé jusqu'à ce que le `NetworkMonitor.onAvailable` redéclenche `wsManager.start()`.

### Dispatch des messages reçus

```kotlin
private fun handleMessage(text: String) {
    val json = JSONObject(text)
    val type = json.getString("type")
    when (type) {
        "actual_workout_updated", "actual_workout_deleted" -> actualWorkoutHandler.handle(json)
        "actual_workout_exercise_updated", ... -> ...
        // 17 cas (un par entité)
    }
}
```

### Findings WS

- ⚠ **Pas de `else` dans le `when`** : un type inconnu est **silencieusement ignoré** sans log. Si le serveur ajoute `notification_updated` plus tard, l'app le ratera sans erreur visible.
- ❌ **`MuscleWeeklySummarySyncHandler` toujours injecté** (à retirer après suppression de l'entité fantôme).
- ❌ **0 handler pour 5 entités sans push WS côté serveur** : `notifications`, `routine_periods`, `routine_tasks`, `routine_task_checks` — cohérent avec l'absence de trigger SQL côté serveur. Mais ça veut dire que ces entités **ne sont synchronisées qu'au login + reconnexion réseau** (via `mergeAllFromServer`), jamais en temps réel pendant l'utilisation.
- ⚠ **`OkHttpClient()` sans config** : pas de timeouts personnalisés (par défaut 10s connect / 10s read / 10s write). Pas de logging interceptor. Acceptable mais pas optimal.
- ⚠ **`type = json.getString("type")`** raise `JSONException` si `type` absent (ce qui est le cas si le serveur lance la migration Alembic au lieu des `db_triggers/`, qui produit un payload sans `type`). Le `try/catch` autour mange l'erreur sans la signaler à l'utilisateur. **Bug potentiel selon le format de payload servi.**
- ✅ **Le `client_id` envoyé est cohérent avec `X-Client-Id` HTTP** → exclude origin fonctionne.
- ⚠ Si `token` change pendant que le WS est ouvert (peu probable mais possible après refresh), le WS continue avec l'ancien token (jamais reconnecté avec le nouveau sauf via `NetworkMonitor.onAvailable`).

---

## 7. NetworkMonitor — détection online/offline + reconnexion

[appli-android/.../utils/NetworkMonitor.kt](../appli-android/app/src/main/java/com/example/sportapp/utils/NetworkMonitor.kt)

### Architecture

- Pas de Hilt — instancié manuellement dans `SportApp.onCreate`
- Wrappe `ConnectivityManager.NetworkCallback`
- **`onAvailable`** : pose `SyncEvents.isNetworkAvailable.value = true`, puis :
  1. Récupère `userId` via `userService.getUserInfo()` si null (= relogin silencieux)
  2. Lance `remoteDataMerger.mergeAllFromServer()` (sync descendante)
  3. Lance `syncManager.syncAllToServer()` (sync montante)
  4. Émet `SyncEvents.onReconnected.emit(Unit)` (pour notifier les écrans)
  5. Restart `webSocketManager.start(token)`
- **`onLost`** : pose `isNetworkAvailable = false` + affiche un Snackbar **"Token expiré !"** (⚠ message faux — c'est le réseau, pas le token)
- `skipFirstAvailable` : flag pour ne pas double-trigger au démarrage si déjà connecté

### Pourquoi `EntryPointAccessors`

`NetworkMonitor` n'est pas un `@Inject` Hilt (créé dans `SportApp.onCreate` avant que Hilt soit prêt à fournir l'app instance ?). Pour accéder aux dépendances (SyncManager, WebSocketManager, RemoteDataMerger), il utilise `EntryPointAccessors.fromApplication(context, SyncEntryPoint::class.java)`. Pattern hacky mais qui marche.

### Findings NetworkMonitor

- 🟠 **Snackbar "Token expiré !" sur réseau perdu** ([NetworkMonitor.kt:78-81](../appli-android/app/src/main/java/com/example/sportapp/utils/NetworkMonitor.kt#L78)) — message UX trompeur. À remplacer par "Hors ligne" ou similaire.
- 🟡 **Pas de gestion d'erreur sur `mergeAllFromServer()`** — si le serveur est joignable mais retourne 500, on n'en sait rien. Idem `syncAllToServer()`. À auditer en 3B.
- 🟡 **Reconnexion WS systématique sans check si déjà connecté** — `wsManager.start(token)` est appelé même si le WS est déjà ouvert (cas où `onAvailable` se déclenche pendant que la connexion existe). Risque de double connexion. Le `start()` actuel `webSocket = client.newWebSocket(...)` écrase l'ancien sans le `close()` → la connexion précédente fuite jusqu'à GC.

---

## 8. Persistance locale — AppDatabase + AppModule (Hilt)

### `AppDatabase`
[appli-android/.../data/local/AppDatabase.kt](../appli-android/app/src/main/java/com/example/sportapp/data/local/AppDatabase.kt)

```kotlin
const val DATABASE_VERSION = 6

@Database(
    entities = [MuscleGoal, Exercise, ActualWorkout, ActualWorkoutExercise, ActualWorkoutSet,
                AvailableEquipment, CycleWorkout, Equipment, ExerciseEquipment, ExerciseMuscle,
                Muscle, MuscleWeeklySummary, Notification, PlannedWorkout, PlannedWorkoutExercise,
                RoutinePeriod, RoutineTask, RoutineTaskCheck, SupersetGroup, SupersetExercise,
                TrainingCycle, User],
    version = DATABASE_VERSION,
    exportSchema = false
)
@TypeConverters(InstructionsConverter::class, NotificationDataConverter::class)
abstract class AppDatabase : RoomDatabase() { /* 22 abstract DAO functions */ }
```

### `AppModule`
[appli-android/.../di/AppModule.kt](../appli-android/app/src/main/java/com/example/sportapp/di/AppModule.kt)

```kotlin
Room.databaseBuilder(app, AppDatabase::class.java, "sport_db")
    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)  // ← réduit la taille du journal
    .fallbackToDestructiveMigration(false)              // ← BLOQUE le fallback
    .build()
```

Puis 22 `@Provides fun provideXDao(db) = db.xDao()`.

### Findings persistance

- 🔴 **`fallbackToDestructiveMigration(false)` + `version = 6` + AUCUNE `Migration` enregistrée** → tout futur bump de schéma fait crasher l'app au démarrage sur les téléphones existants. Déjà noté en étape 1, mais maintenant on confirme : `AppModule.provideDatabase` ne contient AUCUN appel `.addMigrations(...)`.
- ⚠ **Journal mode TRUNCATE** au lieu du WAL par défaut. WAL serait plus performant en lectures concurrentes, TRUNCATE économise de la place. Tradeoff délibéré (pas un bug).
- ⚠ `MuscleWeeklySummary` toujours dans la liste d'entités (à retirer avec migration v6→v7 lors de la suppression).
- ⚠ `exportSchema = false` → impossible de générer le schéma Room en JSON pour les revues. Pour une app en évolution, activer `exportSchema = true` aide à voir les diffs de schéma.
- ⚠ `@TypeConverters(InstructionsConverter::class, NotificationDataConverter::class)` — 2 converters seulement, à inspecter en 3D pour voir comment ils sérialisent (probablement Instructions → JSON list, NotificationData → JSON object).

---

## 9. Storage — StorageManager + MuscleGoalsManager

### `StorageManager` (singleton object)
[appli-android/.../data/repository/StorageManager.kt](../appli-android/app/src/main/java/com/example/sportapp/data/repository/StorageManager.kt)

- Stockage local d'images de muscles
- Charge depuis `filesDir/images/muscles/` (custom user) ou `assets/images/muscles/` (par défaut)
- Format : `{name_lowercase_underscore}.png`
- Fallback `loadDefaultMuscleImage()` charge `default_muscle.png`
- ⚠ Aucune gestion d'erreur si l'image est corrompue (juste retourne null)
- ⚠ **Suppose que les assets `images/muscles/*.png` existent** — si absents, l'app affiche `default_muscle.png` ou null. À confirmer côté `assets/`.

### `MuscleGoalsManager` (singleton @Inject)
[appli-android/.../data/repository/MuscleGoalsManager.kt](../appli-android/app/src/main/java/com/example/sportapp/data/repository/MuscleGoalsManager.kt)

```kotlin
@Singleton
class MuscleGoalsManager @Inject constructor(
    private val muscleGoalDao: MuscleGoalDao,
    private val muscleDao: MuscleDao,
    private val actualWorkoutSetDao: ActualWorkoutSetDao,
    private val syncManager: SyncManager,
) {
    suspend fun updateMuscleGoalsForWeek(weekISO: String) {
        val goals = muscleGoalDao.getGoalsForWeek(weekISO)
        // Pour chaque goal :
        //   - calcule done = nb sets DONE pour ce muscle dans la semaine
        //   - met à jour status = DONE si done >= parseTargetMinimum(goal.target)
        //   - Met à jour le DAO
        // Lance syncManager.syncMuscleGoals() à la fin
    }
}
```

🟠 **Bug architectural** : `parseTargetMinimum` est importé depuis `com.example.sportapp.ui.screens` ([MuscleGoalsManager.kt:8](../appli-android/app/src/main/java/com/example/sportapp/data/repository/MuscleGoalsManager.kt#L8)) — **inversion de dépendance** (data layer dépend de UI layer). Cette fonction devrait vivre dans `utils/` ou dans le domaine MuscleGoal. À refactorer.

---

## 10. UI infra — SnackbarController

[appli-android/.../SnackbarController.kt](../appli-android/app/src/main/java/com/example/sportapp/SnackbarController.kt)

- Singleton `object` avec `MutableStateFlow<List<SnackbarEvent>>`
- `SnackbarEvent` : data class avec `id` (UUID), message, couleurs (background/text/icon/border), action + secondaryAction (suspend), durée
- API : `show(event)`, `dismissSnackbarById(id)`, `dismissAll()`
- Auto-dismiss via coroutine `delay(event.duration.toMillis())` puis dismiss
- Extension `SnackbarDuration.toMillis()` mappe Short=2000, Long=4000, Indefinite=Long.MAX_VALUE
- ✅ Pattern propre, pas de dépendance Hilt
- Couplé à `MainActivity.scaffold.snackbarHost` qui itère sur `snackbars.collectAsState()` avec animations

---

## 11. SyncEntryPoint — Hilt EntryPoint pour briser circular deps

[appli-android/.../di/SyncEntryPoint.kt](../appli-android/app/src/main/java/com/example/sportapp/di/SyncEntryPoint.kt)

```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncEntryPoint {
    fun remoteDataMerger(): RemoteDataMerger
    fun syncManager(): SyncManager
    fun webSocketManager(): WebSocketManager
}
```

Utilisé par `NetworkMonitor` (qui n'est pas Hilt) pour récupérer ses dépendances à l'exécution :
```kotlin
val entryPoint = EntryPointAccessors.fromApplication(context, SyncEntryPoint::class.java)
val merger = entryPoint.remoteDataMerger()
```

✅ Pattern standard Hilt pour casser les liens vers du code non-Hilt. Acceptable.

---

## 12. Findings 3A

Tous les items ont été ajoutés à [TODO_FIXES.md](TODO_FIXES.md). Récap :

### 🔴 Critique cross-stack (bugs serveur ↔ client)

- **`ApiUserService.UserInfo.role: String` non-nullable** — le serveur (`/me`) ne renvoie pas `role`. Crash potentiel au parse Gson. À corriger côté Android (rendre nullable ou supprimer) ou côté serveur (ajouter le champ avec une valeur par défaut).
- **`CycleWorkoutApi` appelle 3 endpoints inexistants côté serveur** ([CycleWorkoutApi.kt](../appli-android/app/src/main/java/com/example/sportapp/network/CycleWorkoutApi.kt)) :
  - `POST /cycle-workouts` (n'existe pas — serveur a seulement GET, PUT/bulk, PUT/{uuid}, DELETE/{uuid})
  - `PUT /cycle-workouts` sans uuid (n'existe pas)
  - `DELETE /cycle-workouts` avec body (n'existe pas)
  - À auditer en 3B : ces méthodes sont-elles appelées par un Sync handler ou un ViewModel ? Si oui → 405/404 silencieux. Si non → code mort, supprimer.
- **Format payload WS** : l'app attend `{type, payload, userId}` (format `db_triggers/` riche). Si le serveur lance la migration Alembic en dernier, le payload est `{table, op, id, uuid, user_id, updated_at}` (sans `type`) → `json.getString("type")` raise une JSONException, mangée par le try/catch → message ignoré silencieusement.

### 🔴 Critique stabilité

- **Room sans Migration** (déjà connu) — confirmé en lisant `AppModule.kt` : aucun `addMigrations(...)`.

### 🟠 Important

- **`NetworkMonitor.onLost` affiche "Token expiré !"** ([NetworkMonitor.kt:78](../appli-android/app/src/main/java/com/example/sportapp/utils/NetworkMonitor.kt#L78)) — UX trompeur (c'est le réseau, pas le token).
- **`MuscleGoalsManager` (data layer) importe depuis `ui.screens`** — inversion de dépendance.
- **WebSocketManager : pas de `else` dans le dispatch `when`** — types de message inconnus silencieusement ignorés sans log.
- **WebSocketManager : reconnexion peut fuiter** — `start(token)` ne ferme pas l'ancien WS s'il est encore ouvert.
- **WebSocketManager : `MuscleWeeklySummarySyncHandler` toujours injecté** (à retirer en cleanup).
- **Token JWT en clair dans SharedPrefs** — acceptable pour app perso mais EncryptedSharedPreferences est plus propre.

### 🟡 Mineur

- **Routes hardcodées comme strings** dans `MainActivity.NavHost` (`"login"`, `"home"`, ...) — risque de typo. Créer un `object Routes { const val LOGIN = "login" }`.
- **`exportSchema = false` sur Room** — empêche les diffs de schéma. À activer.
- **`OkHttpClient()` WS sans timeouts customisés**.
- **`StorageManager` aucune gestion d'erreur** si image corrompue.
- **Pas de check d'expiration JWT côté client** — le 401 du serveur est le seul signal.
- **5 entités sans WS handler côté Android** (cohérent serveur) : `notifications`, `routine_periods`, `routine_tasks`, `routine_task_checks` + cohérent absence de payload pour `users`. Sync REST uniquement pour ces entités.

### Réponses aux questions cross-stack ouvertes

| Question soulevée à la fin de 2C | Réponse 3A |
|---|---|
| L'app utilise-t-elle `id: int` ou `uuid: str` ? | **`uuid: str` partout, sauf `User`** qui utilise `id: Int` (Room PK + stocké dans `CurrentUserManager`). À confirmer en 3D pour les autres modèles. |
| L'app filtre-t-elle les NOTIFY broadcastées sans `userId` ? | À voir en 3B (sync handlers). `WebSocketManager` ne filtre pas, il dispatch. C'est le handler qui doit filtrer. |
| Comment l'app gère-t-elle les 5 entités sans push WS ? | **Sync REST seulement** : `mergeAllFromServer` au login/reconnexion + appels manuels via Apis. Pas de temps-réel pour notifications/routines. |
| Format de payload (Alembic vs db_triggers) | L'app attend le format **db_triggers riche** (`type`, `payload`, `userId`). Si le serveur lance Alembic en dernier, le format différent **plante silencieusement** le dispatch. Source de vérité unique à imposer côté serveur (déjà noté en 2C). |

---

*Sous-étape 3A terminée. Étape suivante : 3B (architecture sync — SyncManager, Syncables, RemoteData{Getter,Merger,Upserter}, sync handlers WS).*

---

# 3B — Architecture sync

> 4 composants centraux : `SyncManager` orchestre, `Syncable<T>` abstrait l'unité de sync (1 par entité), `RemoteData{Getter,Merger,Upserter}` exécute les flux globaux, `*SyncHandler` (data/remote/) traite les messages WS entrants. Plus 2 helpers dans `EntitySyncUtils.kt`.

## Sommaire 3B

- [1. Architecture sync : 4 chemins](#1--architecture-sync--4-chemins)
- [2. Pattern `Syncable<T>` et son squelette canonique](#2--pattern-syncablet-et-son-squelette-canonique)
- [3. `EntitySyncUtils` : `syncEntity` + `syncEntityDeletions`](#3--entitysyncutils--syncentity--syncentitydeletions)
- [4. `SyncManager` — orchestrateur global](#4--syncmanager--orchestrateur-global)
- [5. `RemoteDataMerger` — sync descendante (3-way merge)](#5--remotedatamerger--sync-descendante-3-way-merge)
- [6. `RemoteDataUpserter` — sync montante en bulk](#6--remotedataupserter--sync-montante-en-bulk)
- [7. `RemoteDataGetter` — code mort suspect](#7--remotedatagetter--code-mort-suspect)
- [8. Pattern `*SyncHandler` (WebSocket)](#8--pattern-synchandler-websocket)
- [9. Tableau de conformité Syncables (22)](#9--tableau-de-conformité-syncables-22)
- [10. Réponses aux questions cross-stack ouvertes](#10--réponses-aux-questions-cross-stack-ouvertes)
- [11. Findings 3B](#11--findings-3b)

## 1. Architecture sync : 4 chemins

L'app a **4 chemins de communication** avec le serveur, chacun avec sa logique :

```
┌─ Chemin 1 : Sync montante "ciblée" (par entité) ─────────────────────────────┐
│  ViewModel → SyncManager.syncX() → syncEntity(syncable)                       │
│                                      ↓                                         │
│                                    upsertBulk → fallback upsert individuel    │
└──────────────────────────────────────────────────────────────────────────────┘

┌─ Chemin 2 : Sync montante "globale" (toutes entités) ────────────────────────┐
│  NetworkMonitor.onAvailable / login ↓                                          │
│  SyncManager.syncAllToServer()                                                 │
│   ├─ Phase 1 : forEach syncable → syncEntityDeletions                          │
│   └─ Phase 2 : forEach syncable → syncEntity                                   │
└──────────────────────────────────────────────────────────────────────────────┘

┌─ Chemin 3 : Sync descendante (REST GET batch) ──────────────────────────────┐
│  NetworkMonitor.onAvailable / login ↓                                          │
│  RemoteDataMerger.mergeAllFromServer()                                         │
│   └─ Pour chaque entité : 3-way merge par UUID :                               │
│        • si remote.deletedAt != null → delete local                            │
│        • si pas de local → insertFromServer                                    │
│        • si remote.updatedAt > local.updatedAt → updateFromServer              │
└──────────────────────────────────────────────────────────────────────────────┘

┌─ Chemin 4 : Push temps-réel (WebSocket) ────────────────────────────────────┐
│  WS message → WebSocketManager.handleMessage(json)                             │
│   └─ dispatch sur json["type"] → <Entity>SyncHandler.handle(json)              │
│        • "X_updated"  → parse payload → insertFromServer/updateFromServer      │
│        • "X_deleted"  → extract uuid → delete                                   │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Point clé** : les chemins 1/2 (montants) marquent les items `synced = true` après succès. Les chemins 3/4 (descendants) écrivent avec `synced = true` directement (donc l'app ne re-pushera pas immédiatement ce qu'elle vient de recevoir).

## 2. Pattern `Syncable<T>` et son squelette canonique

[appli-android/.../sync/base/Syncable.kt](../appli-android/app/src/main/java/com/example/sportapp/sync/base/Syncable.kt)

```kotlin
interface Syncable<T> {
    val entityName: String

    suspend fun getAllOnce(): List<T>
    suspend fun getRemote(): List<T>
    suspend fun getUnsyncedLocals(): List<T>
    suspend fun getPendingDeletions(): List<T>
    suspend fun markAsSynced(item: T)
    suspend fun insertOrUpdate(item: T, synced: Boolean, pendingDeletion: Boolean)
    suspend fun upsertBulk(items: List<T>)
    suspend fun upsert(item: T)
    suspend fun deleteRemote(item: T)
    suspend fun deleteLocal(item: T)
}
```

### Squelette d'implémentation (canonique observé)

22 `*Syncable.kt` suivent un même squelette de ~45 lignes :

```kotlin
class XSyncable(private val dao: XDao) : Syncable<X> {
    override val entityName = "Xs"
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getRemote() = RetrofitInstance.xApi.getAll()
    override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
    override suspend fun getPendingDeletions() = dao.getPendingDeletions()
    override suspend fun markAsSynced(item: X) { dao.markAsSynced(item.uuid) }
    override suspend fun insertOrUpdate(item: X, synced: Boolean, pendingDeletion: Boolean) {
        dao.insert(item.copy(synced = synced, pendingDeletion = pendingDeletion))
    }
    override suspend fun upsert(item: X) { RetrofitInstance.xApi.upsert(item.uuid, item) }
    override suspend fun upsertBulk(items: List<X>) { RetrofitInstance.xApi.upsertAll(items) }
    override suspend fun deleteRemote(item: X) { RetrofitInstance.xApi.delete(item.uuid) }
    override suspend fun deleteLocal(item: X) { dao.delete(item) }
}
```

### Variations (justifiées ou pas)

| Syncable | Variation | Statut |
|---|---|---|
| `UserSyncable` | utilise `item.id: Int` au lieu de `item.uuid` (cohérent : User n'a pas d'uuid) | ✅ extension justifiée |
| `ExerciseMuscleSyncable` | `upsert(item)` → `upsert(exerciseUUID, muscleUUID, item)` (3 params) ; `deleteRemote(item)` → `delete(exerciseUUID, muscleUUID)` (par paire au lieu de par uuid) | ⚠ divergence — l'API serveur expose les 2 (uuid ET paire), à uniformiser sur `(uuid)` selon canonique |
| Tous les autres (20) | conformes au squelette | ✅ |

## 3. `EntitySyncUtils` : `syncEntity` + `syncEntityDeletions`

[appli-android/.../sync/base/EntitySyncUtils.kt](../appli-android/app/src/main/java/com/example/sportapp/sync/base/EntitySyncUtils.kt)

### `syncEntity(syncable)` — sync montante avec retry

```kotlin
suspend fun <T> syncEntity(syncable: Syncable<T>): Result<Unit> {
    val unsynced = syncable.getUnsyncedLocals()
    if (unsynced.isEmpty()) return Result.success(Unit)

    return runCatching {
        syncable.upsertBulk(unsynced)              // ← 1. Tentative bulk
    }.onSuccess {
        unsynced.forEach { syncable.markAsSynced(it) }
    }.onFailure {
        // 2. Fallback : upsert individuel pour chaque item
        unsynced.forEach { item ->
            runCatching { syncable.upsert(item) }.onSuccess {
                syncable.markAsSynced(item)
            }.onFailure { hasError = true }
        }
    }.let { ... }
}
```

**Notes** :
- ✅ Tentative bulk d'abord (efficace), fallback individuel (résilient à un seul item buggé)
- ⚠ Si l'erreur de bulk vient d'1 seul item, le fallback re-tente tous → gaspillage. Acceptable.
- ⚠ Pas de retry exponentiel si erreur réseau intermittente. L'item reste `unsynced` jusqu'à la prochaine `syncAll` (manuelle ou auto via `NetworkMonitor`).

### `syncEntityDeletions(syncable)` — sync des suppressions

```kotlin
suspend fun <T> syncEntityDeletions(syncable: Syncable<T>): Result<Unit> {
    syncable.getPendingDeletions().forEach { item ->
        runCatching { syncable.deleteRemote(item) }
            .onSuccess { syncable.deleteLocal(item) }
            .onFailure {
                if (it is HttpException && it.code() == 404) {
                    syncable.deleteLocal(item)   // ✅ déjà supprimé serveur
                } else {
                    hasError = true
                }
            }
    }
}
```

✅ Bonne gestion du 404 (delete local quand même). ❌ Pas de retry sur 500.

### `safeSync*WithSnackbar`

Wrappers qui appellent `syncEntity` / `syncEntityDeletions` puis affichent un Snackbar succès ou erreur. Utilisé par `SyncManager` pour les méthodes "ciblées" (`syncMuscleGoals`, `syncActualWorkouts`, etc.).

## 4. `SyncManager` — orchestrateur global

[appli-android/.../sync/SyncManager.kt](../appli-android/app/src/main/java/com/example/sportapp/sync/SyncManager.kt) (déjà partiellement lu en étape 1)

### Méthodes principales

- **`syncAllToServer()`** : boucle sur 21 syncables. Phase 1 = deletes, Phase 2 = upserts.
  - ⚠ **`isSyncing` flag global** (pas une vraie semaphore) — pas de race condition stricte mais pas robuste à un crash entre `isSyncing = true` et `false`.
  - Snackbar "Starting automatic synchronization..." au début, "completed" / "Error" à la fin.
- **`syncX()` ciblées** (1 par entité) : par ex. `syncMuscleGoals()`, `syncActualWorkouts()`, `syncRoutineTaskChecks()`, ...
- **`syncActualWorkoutExerciseAndPlannedWorkoutExerciseAndSets()`** : extension justifiée pour le flow "fin de séance" (sync workout + exercises + sets en un coup).
- **`syncActualSetsExerciseAndWorkouts()`** : variante similaire.
- **`checkForUnsyncedData()`** : test rapide via `dao.hasUnsynced()` pour afficher un badge "données non syncées" dans l'UI.

### Ordre des syncables dans `syncAllToServer`

```kotlin
val syncables = listOf(
    actualWorkoutSync,            // 1. parents user-scoped
    availableEquipmentSync,
    equipmentSync,
    exerciseSync,
    exerciseEquipmentSync,        // ⚠ junction Exercise×Equipment
    muscleSync,
    muscleSummarySync,            // ← MuscleWeeklySummary (à virer)
    plannedWorkoutSync,
    plannedWorkoutExerciseSync,   // ⚠ junction PlannedWorkout×Exercise
    supersetGroupSync,
    trainingCycleSync,
    actualWorkoutExerciseSync,    // 2. enfants directs
    actualWorkoutSetSync,
    exerciseMuscleSync,           // 3. junctions
    muscleGoalSync,
    supersetExerciseSync,
    cycleWorkoutSync,
    routinePeriodSync,
    routineTaskSync,              // 4. routines
    routineTaskCheckSync,
    notificationSync              // 5. notifications
)

// Phase 1 : deletes pour tous (dans cet ordre — parents d'abord)
syncables.forEach { syncEntityDeletions(it) }

// Phase 2 : upserts pour tous (dans le même ordre)
syncables.forEach { syncEntity(it) }
```

### Findings SyncManager

- 🔴 **Phase 1 (deletes) dans le mauvais ordre FK** : pour delete sans violer les FK, il faudrait l'ordre INVERSE (enfants/junctions d'abord, parents en dernier). Là, `actualWorkoutSync` delete avant `actualWorkoutExerciseSync` → si un workout a des exercises côté serveur, le delete du workout peut FK-violer. **Soit le serveur cascade-delete (à confirmer en 4)**, soit c'est un bug latent. À auditer.
- 🟠 **Phase 2 (upserts) ordre OK pour la création** : parents avant enfants, junctions à la fin. ✅
- 🟠 **`availableEquipmentSync` AVANT `equipmentSync`** : noté en 2B comme suspect. Ces 2 entités ne sont pas FK-liées, juste stylistique. Pas critique mais à clarifier en 3D.
- ⚠ **`isSyncing` Boolean simple** au lieu d'une `Mutex` — un crash entre `isSyncing = true` et le `finally` laisse l'app en état "sync bloquée" jusqu'au redémarrage (improbable mais possible).
- ⚠ **22 snackbars potentiels** par syncAll (1 par entité via `safe*WithSnackbar`) — UX bruyante. À auditer en 3C.
- ⚠ **`UserSyncable` n'est PAS dans la liste** — User n'est jamais syncé via `syncAllToServer`. Cohérent avec la politique sécurité (l'app ne push pas les users), mais à valider.

## 5. `RemoteDataMerger` — sync descendante (3-way merge)

[appli-android/.../sync/RemoteDataMerger.kt](../appli-android/app/src/main/java/com/example/sportapp/sync/RemoteDataMerger.kt) (~600 lignes, 22 entités)

### Algorithme

Pour **chaque entité**, mêmes étapes :

```kotlin
val remote = api.getAll()                          // GET /xs (REST)
val local = dao.getAllOnce().associateBy { it.uuid }
for (item in remote) {
    when {
        item.deletedAt != null -> dao.delete(local[item.uuid])  // soft-delete propagé
        local[item.uuid] == null -> dao.insertFromServer(item.copy(synced = true))
        isRemoteNewer(local[item.uuid].updatedAt, item.updatedAt) ->
            dao.insertFromServer(item.copy(synced = true))  // remote plus récent
        // sinon : on garde local (qui sera push au prochain syncAllToServer)
    }
}
```

Avec :
```kotlin
private fun isRemoteNewer(localUpdated: String?, remoteUpdated: String?): Boolean {
    if (remoteUpdated == null) return false
    if (localUpdated == null) return true
    return remoteUpdated > localUpdated   // ⚠ comparaison string lexicographique
}
```

### Findings RemoteDataMerger

- ✅ **Conflit géré par "last-writer-wins"** via `updatedAt` — simple et correct pour un usage mono-user multi-device.
- ⚠ **Comparaison `updatedAt` en string** : marche si format ISO 8601 strict (`YYYY-MM-DDTHH:MM:SS.fffffff`) car ordre lexicographique ≡ ordre temporel. Si formats divergent (avec/sans timezone, microseconds variables), comparaison fausse. Risque selon ce que le serveur renvoie vs ce que l'app stocke.
- ⚠ **Bloc `Users` commenté** ([RemoteDataMerger.kt:237-252](../appli-android/app/src/main/java/com/example/sportapp/sync/RemoteDataMerger.kt#L237)) — les users ne sont jamais synchronisés depuis le serveur via cette voie. Cohérent avec la politique sécurité (un user ne devrait pas voir les autres users).
- ⚠ **`isLocalNewer` défini mais jamais utilisé** ([l. 597-601](../appli-android/app/src/main/java/com/example/sportapp/sync/RemoteDataMerger.kt#L597)) — code mort.
- 🟠 **`local.deletedAt` non vérifié** : si l'utilisateur a soft-deleted localement (pendingDeletion), et que le serveur renvoie l'item normalement (pas encore propagé), le merger va `updateFromServer` → écraser le pendingDeletion local → l'utilisateur perd sa suppression locale tant qu'elle n'a pas été envoyée. **Bug subtil** : il faut faire `mergeAllFromServer` APRÈS `syncAllToServer` (= push d'abord, pull ensuite), ce que `NetworkMonitor.onAvailable` fait dans le bon ordre (merge puis sync) → wait, le faux : `mergeAllFromServer` est appelé EN PREMIER ([NetworkMonitor.kt:51-55](../appli-android/app/src/main/java/com/example/sportapp/utils/NetworkMonitor.kt#L51)), puis `syncAllToServer`. Donc effectivement risque d'écrasement de suppressions locales en attente.

## 6. `RemoteDataUpserter` — sync montante en bulk

[appli-android/.../sync/RemoteDataUpserter.kt](../appli-android/app/src/main/java/com/example/sportapp/sync/RemoteDataUpserter.kt) (~520 lignes, 2 fonctions)

### `upsertAllUnsynced` (par item)
Pour chaque entité, récupère les unsynced (ou tous selon entité, cf. inconsistance), envoie un par un.

### `upsertAllData` (bulk)
Pour chaque entité, envoie TOUTE la table en bulk. Utilisé pour la sync initiale ou debug.

### Findings RemoteDataUpserter

- 🟠 **Inconsistance majeure : `getAllUnsynced` vs `getAllOnce`** dans `upsertAllUnsynced` :
  | Entité | Source utilisée |
  |---|---|
  | actualWorkout, actualWorkoutSet, exercise, muscleWeeklySummary, notification, plannedWorkout, plannedWorkoutExercise, routinePeriod, routineTask, routineTaskCheck, supersetExercise, supersetGroup, trainingCycle, user | `getAllUnsynced` ✅ |
  | availableEquipment, equipment, exerciseEquipment, exerciseMuscle, muscle, muscleGoal | `getAllOnce` ❌ (envoie TOUT à chaque sync) |
  
  Conséquence : 6 entités envoient toutes leurs données à chaque `upsertAllUnsynced`. Gaspillage bande passante + risque de race conditions si plusieurs clients modifient en même temps. À uniformiser sur `getAllUnsynced`.
- 🔴 **`cycleWorkoutApi.upsert(cw)` (sans uuid)** ([RemoteDataUpserter.kt:70](../appli-android/app/src/main/java/com/example/sportapp/sync/RemoteDataUpserter.kt#L70)) — appelle un endpoint qui n'existe pas côté serveur (cf. [3A bug](#12--findings-3a)).
- 🟠 **`exerciseMuscleApi.upsert(exerciseUUID, muscleUUID, exerciseMuscle)`** ([l. 106](../appli-android/app/src/main/java/com/example/sportapp/sync/RemoteDataUpserter.kt#L106)) — appelle l'endpoint par paire au lieu de par uuid (le serveur expose les deux mais à uniformiser).
- 🟠 **Inconsistance utilisation User** : `upsertAllUnsynced` appelle `userApi.upsert(u.id, u)` pour TOUS les users locaux ; `upsertAllData` a le bloc User commenté "TODO : User is not synced for security reasons". **Décider** : soit on push les users, soit on ne push pas. Actuellement les 2 fonctions sont incohérentes.
- ⚠ **2 fonctions qui font ≈ la même chose** (`upsertAllUnsynced` vs `upsertAllData`) — l'une utilise `upsert(uuid, item)` individuel, l'autre `upsertAll(items)` bulk. Probablement l'une est une ancienne version. À auditer le call-graph en 3C pour savoir laquelle est appelée.

## 7. `RemoteDataGetter` — code mort suspect

[appli-android/.../sync/RemoteDataGetter.kt](../appli-android/app/src/main/java/com/example/sportapp/sync/RemoteDataGetter.kt) (~205 lignes)

```kotlin
suspend fun getAll(log: Boolean = false): Boolean {
    val muscles = api.getAll()
    muscleDao.clearAll()
    muscleDao.insertAll(muscles.map { it.copy(synced = false) })  // ⚠ synced = false
    ...
}
```

**Comportement** : DROP+REINSERT massif pour 21 entités. Pose `synced = false` (= "à push"), ce qui est faux pour des données venant du serveur.

### Findings

- 🔴 **`synced = false` pour des données serveur** ([RemoteDataGetter.kt:46-203](../appli-android/app/src/main/java/com/example/sportapp/sync/RemoteDataGetter.kt#L46)) — bug logique : juste après `getAll()`, l'app pense que toutes ses données sont à push. Si elle lance `syncAllToServer()` derrière, elle re-push au serveur ce qu'elle vient de pull. Probable boucle inefficace.
- 🟠 **`Notification` : `dao.insertAll` manquant** ([l. 193-196](../appli-android/app/src/main/java/com/example/sportapp/sync/RemoteDataGetter.kt#L193)) — récupère `notifications` mais n'insert jamais.
- ⚠ **Probablement code mort** — `RemoteDataMerger` est utilisé partout (`NetworkMonitor`, login flow). `RemoteDataGetter` ne semble appelé nulle part. À confirmer par grep en 3C/3D.

→ **Recommandation** : supprimer `RemoteDataGetter.kt` après confirmation qu'il n'est appelé nulle part.

## 8. Pattern `*SyncHandler` (WebSocket)

[appli-android/.../data/remote/](../appli-android/app/src/main/java/com/example/sportapp/data/remote/) — 17 handlers (1 par entité avec push WS côté serveur)

### Squelette canonique

```kotlin
@Singleton
class XSyncHandler @Inject constructor(
    private val dao: XDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "x_updated" -> {
                val payload = json.getJSONObject("payload")
                val x = X(
                    uuid = payload.getString("uuid"),
                    userId = payload.getInt("userId"),
                    name = payload.getString("name"),
                    // ... extraction champ par champ
                    updatedAt = payload.getNullableString("updatedAt"),
                    deletedAt = payload.getNullableString("deletedAt"),
                    synced = true
                )
                val existing = dao.getXByUUID(x.uuid)
                if (existing == null) dao.insertFromServer(x) else dao.updateFromServer(x)
            }
            "x_deleted" -> {
                val uuid = json.getString("uuid")
                dao.getXByUUID(uuid)?.let { dao.delete(it) }
            }
        }
    }
}
```

### Findings Sync handlers

- 🔴 **Aucun handler ne filtre par `userId`** : si le serveur broadcast à TOUS (ce qui est le cas pour `exercise_muscle`, `exercise_equipment`, `equipment` — cf. 2C TODO), le handler insert le payload reçu directement dans la DAO locale. Conséquences :
  - **`ExerciseMuscleSyncHandler`** : un user A qui modifie un de ses `exercise_muscle` push une NOTIFY que TOUS les users connectés reçoivent. Le user B reçoit le payload, l'insert dans sa Room locale → **orphelin** car `exerciseUUID` ne correspond à aucun exercice de B (les exercises sont user-scoped et B n'a pas l'exercice de A).
  - Idem pour `EquipmentSyncHandler` (Type C global, OK car équipement est partagé) — moins grave.
  - **À ajouter** : check `if (dao.getExerciseByUUID(payload.exerciseUUID) != null) insert; else ignore` dans les handlers de junctions.
- 🟠 **`payload.optString("zone", null)`** ([MuscleSyncHandler.kt:26](../appli-android/app/src/main/java/com/example/sportapp/data/remote/MuscleSyncHandler.kt#L26)) — comportement subtil de `JSONObject.optString` : retourne le fallback si la clé est absente OU si la valeur est JSON `null`. Mais selon les versions, peut retourner la string `"null"` au lieu d'un `null` Kotlin. Pattern utilisé partout. Risque de stocker `"null"` au lieu de `null` dans Room. Un helper `getNullableString` existe ([JsonUtils.kt](../appli-android/app/src/main/java/com/example/sportapp/utils/JsonUtils.kt)) et est utilisé pour `updatedAt`/`deletedAt`. À utiliser systématiquement pour tous les champs nullables.
- 🟠 **`ExerciseSyncHandler` n'extrait pas `instructions`** ([ExerciseSyncHandler.kt:22-38](../appli-android/app/src/main/java/com/example/sportapp/data/remote/ExerciseSyncHandler.kt#L22)) — le model `Exercise` a un champ `instructions: List<String>?` (cf. schema `exercise_schema.py`), le trigger SQL `exercises_trigger.sql` doit être audité (3D + 4) pour voir s'il l'envoie. Si oui, le handler le perd.
- 🟠 **`payload.optInt("recommendedSets", 0)`** ([ExerciseSyncHandler.kt:26](../appli-android/app/src/main/java/com/example/sportapp/data/remote/ExerciseSyncHandler.kt#L26)) — même bug que `optString` : si la valeur est null, on stocke 0 au lieu de null. Si le model a `recommendedSets: Int?`, on perd la sémantique "non spécifié".

## 9. Tableau de conformité Syncables (22)

| Syncable | Squelette canonique ? | Variation | API endpoints utilisés |
|---|---|---|---|
| ActualWorkoutSyncable | ✅ | — | `upsert(uuid, item)`, `upsertAll`, `delete(uuid)` |
| ActualWorkoutExerciseSyncable | ✅ | — | par uuid |
| ActualWorkoutSetSyncable | ✅ | — | par uuid |
| AvailableEquipmentSyncable | ✅ | — | par uuid |
| CycleWorkoutSyncable | ⚠ | `upsert(item)` sans uuid → endpoint 🔴 inexistant | non standard |
| EquipmentSyncable | ✅ | — | par uuid |
| ExerciseSyncable | ✅ | — | par uuid |
| ExerciseEquipmentSyncable | ⚠ | par paire ou par uuid à confirmer | non standard |
| ExerciseMuscleSyncable | ⚠ | `upsert(exerciseUUID, muscleUUID, item)` 3 params, `delete(exerciseUUID, muscleUUID)` | non standard, à uniformiser |
| MuscleSyncable | ✅ | — | canonique |
| MuscleGoalSyncable | ✅ | — | par uuid |
| MuscleWeeklySummarySyncable | ✅ | — | à supprimer (entité fantôme) |
| NotificationSyncable | ✅ | — | par uuid |
| PlannedWorkoutSyncable | ✅ | — | par uuid |
| PlannedWorkoutExerciseSyncable | ✅ | — | par uuid |
| RoutinePeriodSyncable | ✅ | — | par uuid |
| RoutineTaskSyncable | ✅ | — | par uuid |
| RoutineTaskCheckSyncable | ✅ | — | par uuid |
| SupersetExerciseSyncable | ✅ | — | par uuid |
| SupersetGroupSyncable | ✅ | — | par uuid |
| TrainingCycleSyncable | ✅ | — | par uuid |
| UserSyncable | ⚠ | `markAsSynced(item.id)`, `delete(item.id)` (cohérent — User n'a pas d'uuid) | extension justifiée |

**18/22 conformes ✅** · **3 variations** (CycleWorkout, ExerciseMuscle, User — User justifié, les 2 autres à uniformiser) · **1 à supprimer** (MuscleWeeklySummary).

## 10. Réponses aux questions cross-stack ouvertes

| Question (de fin d'étape 2) | Réponse 3B |
|---|---|
| L'app filtre les NOTIFY broadcastées sans `userId` ? | **NON** : `WebSocketManager.handleMessage` dispatch sans filtrer ; `*SyncHandler.handle` insert le payload reçu sans valider que l'entité parente est dans la Room locale. Pour `exercise_muscles` (broadcast à tous côté serveur), un user reçoit les liens des autres users → **insertion d'orphelins** côté Room (les FK ne sont pas validées par Room en mode insert simple). Bug confirmé. |
| Comment les 5 entités sans push WS sont synchronisées ? | Via `RemoteDataMerger.mergeAllFromServer` (appelé au login + à chaque reconnexion réseau via `NetworkMonitor`) + via `SyncManager.syncRoutineTasks/Periods/...()` ciblées appelables manuellement par l'UI. Pas de temps-réel pendant l'utilisation (= un autre device modifiant ne se propage qu'après reconnexion). |
| L'app dépend-elle de `id: int` ou `uuid: str` ? | **`uuid: str` partout sauf User** (qui utilise `id: Int`). `UserSyncable` est l'unique syncable qui utilise `markAsSynced(item.id)` et `delete(item.id)`. Cohérent avec le model Room. |
| Format de payload (Alembic vs db_triggers) | L'app attend le format **db_triggers riche** (`{type, payload, userId}`) — confirmé en lisant les `*SyncHandler.handle`. Si le serveur lance Alembic en dernier, le payload est `{table, op, ...}` (sans `type`) → `json.getString("type")` raise → mangé par try/catch dans WebSocketManager → message **silencieusement ignoré**. Bug latent en cas de rollback Alembic. |

## 11. Findings 3B

Tous les items sont ajoutés à [TODO_FIXES.md](TODO_FIXES.md). Récap :

### 🔴 Critique cross-stack

- **Phase 1 deletes dans le mauvais ordre FK** ([SyncManager.syncAllToServer](../appli-android/app/src/main/java/com/example/sportapp/sync/SyncManager.kt#L189)) — parents deletés avant enfants. Soit le serveur cascade-delete (à confirmer en 4), soit bug latent.
- **`*SyncHandler` n'ignore pas les payloads des autres users** — pour les junctions broadcast à tous (`exercise_muscle`, `exercise_equipment`), un user A reçoit les modifs de user B et insert des orphelins dans sa Room locale.
- **`RemoteDataGetter.synced = false`** — pose ce flag sur des données venant du serveur → re-push immédiat. Probablement code mort, à confirmer + supprimer.
- **`RemoteDataUpserter` utilise `cycleWorkoutApi.upsert(cw)` sans uuid** — endpoint inexistant côté serveur (déjà cf. 3A).

### 🟠 Important

- **`mergeAllFromServer` peut écraser un pendingDeletion local** — l'ordre `merge → push` dans `NetworkMonitor.onAvailable` est inversé : il faudrait `push → merge`.
- **`RemoteDataUpserter.upsertAllUnsynced` mélange `getAllUnsynced` et `getAllOnce`** — 6 entités envoient TOUT à chaque sync (gaspillage + race).
- **`isRemoteNewer` compare des strings ISO 8601** — fragile si formats divergent. À auditer en 4.
- **`isSyncing: Boolean` simple** dans `SyncManager` — pas une `Mutex`, état zombie possible après crash.
- **`RemoteDataUpserter.upsertAllData` vs `upsertAllUnsynced` incohérence User** (l'un push, l'autre commenté).
- **`payload.optString(key, null)` partout dans les handlers** — risque de stocker `"null"` string au lieu de `null` Kotlin. Utiliser `JsonUtils.getNullableString` systématiquement.
- **`ExerciseSyncHandler` n'extrait pas `instructions`** — perte de données si le trigger SQL le pousse.
- **`RemoteDataMerger` bloc Users commenté + `isLocalNewer` jamais utilisé** — code mort.

### 🟡 Mineur

- **`ExerciseMuscleSyncable` divergent** (signatures par paire au lieu de par uuid) — uniformiser.
- **`CycleWorkoutSyncable.upsert(item)` sans uuid** — uniformiser.
- **22 snackbars potentiels par syncAll** — UX bruyante, à condenser (1 snackbar global "Sync OK / Sync errors").
- **`syncEntity` pas de retry exponentiel** — items unsynced restent en attente jusqu'à la prochaine sync manuelle.
- **`RemoteDataGetter.notifications` insertAll manquant**.
- **`RemoteDataMerger` utilise `local = true` boolean log avec `val log = true` qui écrase le param** ([RemoteDataMerger.kt:37](../appli-android/app/src/main/java/com/example/sportapp/sync/RemoteDataMerger.kt#L37)) — micro-bug.

---

*Sous-étape 3B terminée. Étape suivante : 3C (UI — 25 écrans + 20 ViewModels + 100+ composants + module notifications/).*

---

# 3C — UI : ViewModels + écrans + modules feature

> Approche : audit complet des 20 ViewModels (où vit la logique métier), audit détaillé du module `notifications/` (Style A), survol des modules feature `auth/` et `settings/`, sample des écrans principaux. Les ~100 composants UI ne sont pas audités fichier par fichier (volume trop grand pour valeur ajoutée — couverts en sample).

## Sommaire 3C

- [1. Découverte cross-cutting : 3 feature modules Style A](#1--découverte-cross-cutting--3-feature-modules-style-a)
- [2. Audit des 20 ViewModels](#2--audit-des-20-viewmodels)
- [3. `AuthManager` — module `auth/`](#3--authmanager--module-auth)
- [4. Module `notifications/` (12 fichiers)](#4--module-notifications-12-fichiers)
- [5. Module `settings/` (5 fichiers)](#5--module-settings-5-fichiers)
- [6. Patterns UI + antipatterns détectés](#6--patterns-ui--antipatterns-détectés)
- [7. Findings 3C](#7--findings-3c)

## 1. Découverte cross-cutting : 3 feature modules Style A

L'audit révèle que le projet a **3 feature modules** Style A (cohérents avec la politique du projet validée en étape 1) :

| Module | Fichiers | Rôle | Architecture |
|---|---|---|---|
| `auth/` | 1 (`AuthManager.kt`) | Boot auth + start/stop WebSocket + clear session | Style A minimal (1 classe singleton) |
| `notifications/` | 12 (`data/`, `domain/`, `ui/`, `utils/`) | Notifs in-app + push téléphone + deep links + overlay | Style A complet — déjà connu |
| `settings/` | 5 (`AppSettings.kt`, `AppSettingsRepository.kt`, `SettingsDataStore.kt`, `SettingsModule.kt`, `SettingsViewModel.kt`) | DataStore Preferences pour les réglages app (vibrations, overlay, etc.) | Style A propre |

**+ 1 feature dispersée** :
- **Chrono / Timer** : la logique vit dans `viewmodel/ChronoViewModel.kt` (295 lignes), les composants dans `ui/components/chronoScreen/`, l'overlay flottant dans `MainActivity` via `MiniChronoOverlay` / `MiniTimerOverlay`. **Pas un module Style A** mais devrait l'être (extension justifiée pour le futur refactor).

→ La politique "Style A pour les features auto-suffisantes, Style B pour le core couplé training" (validée en étape 1) **est déjà appliquée en pratique** sur 3 features ! L'utilisateur peut être tranquille : il n'a pas inventé d'architecture incohérente, juste implémenté progressivement.

## 2. Audit des 20 ViewModels

| ViewModel | Lignes | Statut | Logique principale | Bugs / divergences |
|---|---|---|---|---|
| `LoginViewModel` (LoginScreenViewModel.kt) | 47 | ✅ | Login → snackbar → callback | ⚠ Naming : classe ≠ fichier |
| `SplashScreenViewModel` | 78 | ✅ | Boot orchestré : auth → merge → push → home | Cohérent |
| `HomeViewModel` | 234 | ✅ | Today's session, planned/actual flows, startActualWorkoutFromPlanned | ⚠ `parseTargetReps` local, dupliqué dans CalendarViewModel ; `println` debug verbeux |
| `SessionTabViewModel` | 435 | ✅ | Le plus complexe : add/delete exercises, ignore/unignore planned, reorder, sync | ⚠ Très long ; tryIgnore/tryUnignore propagés au planned parent ; `println` debug ; logique métier hors ViewModel pourrait justifier un repository dédié |
| `SessionExerciseViewModel` | 344 | ✅ | Update reps/weight/notes/status, dropsets, bonus sets, reindex | ⚠ Référence `actualWorkoutSet.targetReps` (à confirmer en 3D) ; `parseTargetReps` interne et `minTargetFromRecommended` interne — dupliqué |
| `PlannedWorkoutViewModel` | 124 | ✅ | Add / mark for deletion exercises planifiés | Court et propre |
| `WeekViewViewModel` | 268 | ✅ | Calcul `weekProgress` via `combine` de 5 flows | ⚠ Init bloc avec println debug ; bonne logique de "cap par exercice" |
| `ChronoViewModel` | 295 | ✅ | Stopwatch + Timer state machines | ⚠ `notifyTimerDone(userId, "Rest", 90)` hardcodé — l'UI ne peut pas changer le label/durée envoyé à la notif |
| `StatsViewModel` | 11 | ❌ **VIDE** | "Logique des statistiques à venir" | Feature non implémentée |
| `GoalsTabViewModel` | 370 | ⚠ | Auto-completion goals, sort by name/priority, copy from last week, display by zone | 🔴 Importe `ui.screens.parseTargetMinimum` (inversion dépendance) ; ⚠ auto-completion lance `viewModelScope.launch` dans un combine → boucle potentielle |
| `RoutineTasksViewModel` | 718 | ✅ | Le plus gros : sections UI, drag & drop, periods, tasks, checks, reorder | Très complet, propre |
| `HistoryViewModel` | 13 | ❌ **VIDE** | Vide | Feature non implémentée |
| `ProfileViewModel` (ProfileScreenViewModel.kt) | 14 | ✅ | Expose `hasUnsyncedData` | Très court ; ⚠ Naming class ≠ fichier |
| `LogoutScreenViewModel` | 16 | ✅ | Délègue à `AuthManager.stopAuth()` | Propre |
| `ExerciseListScreenViewModel` | 261 | ✅ | Toggle favorite, delete (cascade muscle+equipment), add, update | Propre, cascade bien gérée |
| `ExerciseScreenViewModel` | 240 | ⚠ | Détail exercice, dernières sessions, images muscles | ⚠ **Bug typing** ligne 49 : `private val actualWorkoutDao: ActualWorkoutExerciseDao` (typage dupliqué — devrait être `ActualWorkoutDao`) |
| `MuscleListScreenViewModel` | 60 | ✅ | CRUD muscles | Propre |
| `MuscleScreenViewModel` | 58 | ✅ | Détail muscle, toggle favorite | Propre |
| `CalendarViewModel` | 498 | ✅ | Calendar mensuel, perfect weeks, complete days, rest days, monthProgress | ⚠ `parseTargetReps` dupliqué de HomeViewModel ; `println` debug très verbeux ; logique de calcul bien faite |
| `SyncSettingsViewModel` | (lu en 2B-2) | ✅ | Panneau admin sync | Bonne pratique : VM générique listant toutes les tables |

**Observations** :
- 🔴 **2 ViewModels VIDES** : `StatsViewModel`, `HistoryViewModel`. Features stub — l'écran existe mais ne fait rien ou presque.
- ⚠ **`ExerciseScreenViewModel` a un bug de typage** ligne 49 : `actualWorkoutDao: ActualWorkoutExerciseDao` (devrait être `ActualWorkoutDao`). Probablement une faute lors d'un refactor — à voir si la fonction est même utilisée.
- ⚠ **Naming class ≠ fichier** : `LoginViewModel` dans `LoginScreenViewModel.kt`, `ProfileViewModel` dans `ProfileScreenViewModel.kt`, `RoutineTasksViewModel` dans `RoutineTasksScreenViewModel.kt`. Inconsistance, à uniformiser.
- ⚠ **Logique dupliquée** : `parseTargetReps` apparaît dans `HomeViewModel`, `SessionExerciseViewModel`, `CalendarViewModel` — devrait être dans `utils/RepUtils.kt`.
- ⚠ **`println` partout** : debug verbeux laissé en place. À remplacer par `Log.d` (filtrable en prod) ou retirer.
- ⚠ **Pattern "marker unsynced + sync immédiat"** systématique : à chaque write `dao.markAsUnsynced(uuid)` puis `syncManager.syncX()`. Cohérent mais bruyant (le SnackbarController affiche un snackbar par sync, cf. 3B).

## 3. `AuthManager` — module `auth/`

[appli-android/.../auth/AuthManager.kt](../appli-android/app/src/main/java/com/example/sportapp/auth/AuthManager.kt)

```kotlin
@Singleton
class AuthManager @Inject constructor(
    private val wsManager: WebSocketManager,
    @ApplicationContext private val appContext: Context
) {
    sealed class AuthState { Authenticated; NeedLogin; Offline }

    suspend fun initAuth(): AuthState {
        RetrofitInstance.skipInterceptorAuth = true   // ⚠ flag potentiellement ignoré
        try {
            val token = TokenManager.token
            if (token.isNullOrBlank()) return AuthState.NeedLogin
            if (!RetrofitInstance.verifyToken()) return AuthState.NeedLogin
            wsManager.start(token)
            return AuthState.Authenticated
        } catch (e: Exception) {
            CurrentUserManager.setUserId(appContext, -1)        // ⚠ sentinel -1
            showSnackbar("No network - Offline mode", WARNING)
            return AuthState.Offline
        } finally {
            RetrofitInstance.skipInterceptorAuth = false
        }
    }

    fun stopAuth() {
        wsManager.stop()
        TokenManager.clearToken(appContext)
        CurrentUserManager.clearUserId(appContext)
    }
}
```

### Findings AuthManager

- 🟠 **`RetrofitInstance.skipInterceptorAuth = true`** ([AuthManager.kt:26](../appli-android/app/src/main/java/com/example/sportapp/auth/AuthManager.kt#L26)) — flag posé puis retiré dans le `finally`. **Mais ce flag n'est pas testé dans `authInterceptor`** ([RetrofitInstance.kt:55-68](../appli-android/app/src/main/java/com/example/sportapp/network/RetrofitInstance.kt#L55)). Probable héritage d'un ancien design : `var skipInterceptorAuth = false` est déclarée mais inutilisée. Code mort à supprimer (ou à brancher si l'intention initiale a un sens).
- 🟠 **Sentinel `userId = -1` en mode Offline** ([AuthManager.kt:46](../appli-android/app/src/main/java/com/example/sportapp/auth/AuthManager.kt#L46)) — pattern fragile. `CurrentUserManager.init` filtre `if (id != -1)` donc -1 est ignoré au prochain démarrage. Mais entre-temps, `CurrentUserManager.userId` retourne -1 → si un VM utilise cet ID dans une query DAO, comportement imprévu. Préférer `setUserId(null)` ou ne rien faire.

## 4. Module `notifications/` (12 fichiers)

Architecture **Style A complète** (data/domain/ui/utils). Le module est mature et propre.

### Structure

```
notifications/
├── data/
│   └── NotificationRepository.kt       ← façade DAO + builders
├── domain/
│   ├── NotificationCenter.kt           ← orchestrateur (post = persist + overlay + push + vibrate + sync)
│   ├── PhoneNotificationManager.kt     ← NotificationCompat builders + 4 channels par level
│   └── NotificationNavigationMapper.kt ← type → route (deep link)
├── ui/
│   ├── NotificationViewModel.kt        ← markAsRead, markAllAsRead, markPendingDeletion
│   ├── NotificationOverlayHost.kt      ← composable overlay top-of-screen avec animations
│   ├── NotificationScreen.kt           ← écran liste (non lu en détail)
│   └── components/
│       ├── EmptyNotificationsState.kt
│       ├── NotificationsHeader.kt
│       ├── SwipeableNotificationItem.kt
│       └── NotificationsSummaryInline.kt
└── utils/
    └── notificationUtils.kt            ← enums NotificationType, NotificationLevel + extensions kind/levelKind
```

### Points clés

- ✅ **Type-safe via enums** : `NotificationType` (TIMER_DONE, WORKOUT_REMINDER, SYNC_DONE, SYNC_ERROR, CHAT, EXERCISE, UNKNOWN) + `NotificationLevel` (INFO, SUCCESS, WARNING, ERROR). Le commentaire dans `NotificationRepository` rappelle "Ne JAMAIS utiliser de strings en dur pour type / level".
- ✅ **Channel par level** : 4 canaux Android (`in_app_info`, `in_app_success`, `in_app_warning`, `in_app_error`) avec patterns vibration différents.
- ✅ **Deep link via `PendingIntent`** : `sportapp://notif/<route>?uuid=<uuid>` géré dans `MainActivity.handleDeepLinkIntent`.
- ✅ **Settings honorés** : `AppSettingsRepository.settings` (cf. §5) contrôle `showInAppNotificationOverlay`, `showPhoneNotifications`, `soundOnInAppNotification`, `vibrateOnInAppNotification`.
- ✅ **Dedupe via `dedupeKey`** : la PendingIntent ID est `(dedupeKey ?: uuid).hashCode()` → notifs avec même `dedupeKey` se remplacent (anti-spam).
- ✅ **Overlay auto-dismiss après 3500ms** ([NotificationOverlayHost.kt:50](../appli-android/app/src/main/java/com/example/sportapp/notifications/ui/NotificationOverlayHost.kt#L50)).

### Findings notifications

- 🟡 **`NotificationRepository.build()` existe en 2 versions** (string-based + enum-based). L'enum-based est mieux mais les deux coexistent. À factoriser.
- 🟡 **`NotificationCenter.post` vibre 2x potentiellement** ([NotificationCenter.kt:54-57](../appli-android/app/src/main/java/com/example/sportapp/notifications/domain/NotificationCenter.kt#L54)) — un commentaire `⚠️ ceci fait vibrer même quand l'app est au 1er plan. En arrière-plan, la vibration doit venir du channel.` Le code applique `VibrationUtils.vibrateForNotification` côté app + `enableVibration(true)` sur le channel système. Risque de double vibration si app au premier plan. À auditer.
- 🟡 **`NotificationCenter.notifyTimerDone` helper** existe mais `ChronoViewModel.onTimerFinished` hardcode `"Rest", 90` au lieu de passer le vrai nom et durée du timer. Bug fonctionnel : la notif dit toujours "Rest 90s" peu importe la durée réelle.

## 5. Module `settings/` (5 fichiers)

Architecture **Style A** propre :

| Fichier | Rôle |
|---|---|
| `AppSettings.kt` | Data class : `showInAppNotificationOverlay`, `showPhoneNotifications`, `vibrateOnInAppNotification`, `soundOnInAppNotification` (probablement) |
| `SettingsDataStore.kt` | Wrapper DataStore Preferences |
| `AppSettingsRepository.kt` | Repo qui expose `settings: StateFlow<AppSettings>` |
| `SettingsModule.kt` | Hilt provides |
| `SettingsViewModel.kt` | VM pour SettingsScreen |

Pas audité ligne par ligne (volume faible, architecture déjà validée). L'utilisation par `NotificationCenter` confirme que c'est branché correctement.

## 6. Patterns UI + antipatterns détectés

### ✅ Patterns positifs cohérents

- **`@HiltViewModel @Inject constructor`** partout — DI propre.
- **`StateFlow<X>` exposé via `stateIn(scope, WhileSubscribed(5000), default)`** — pattern recommandé pour la lifecycle-awareness.
- **Mutations DAO toujours suivies de `markAsUnsynced` + `syncManager.syncX()`** — cohérent.
- **Pattern "observe par UUID"** : `dao.observeByUUID(uuid)` partout, supportant l'async réactif.
- **`SavedStateHandle`** pour récupérer les params de nav (`exerciseUUID`, `actualWorkoutExerciseUUID`).
- **`combine` de flows** pour les calculs dérivés (week progress, calendar summary, goals enrichis) — bonne utilisation de Kotlin Flow.

### ⚠ Antipatterns à corriger

- **Logs `println(...)` partout** — laisse le debug en prod. À remplacer par `Log.d` (filtrable) ou retirer.
- **Logique dupliquée** entre VMs : `parseTargetReps`, `minTargetFromRecommended`, `parseTargetMinimum` (dans `ui.screens` !).
- **Inversion de dépendance** : `MuscleGoalsManager`, `GoalsTabViewModel` importent `com.example.sportapp.ui.screens.parseTargetMinimum`. La data layer ne devrait pas dépendre de UI.
- **`ExerciseScreenViewModel` typage faux** ligne 49 : `private val actualWorkoutDao: ActualWorkoutExerciseDao` (devrait être `ActualWorkoutDao`).
- **2 VMs vides** (`StatsViewModel`, `HistoryViewModel`) — features stub.
- **Naming class ≠ filename** sur 3 fichiers (LoginScreenViewModel.kt → LoginViewModel, etc.).
- **Pattern hardcode** : `ChronoViewModel.onTimerFinished()` envoie ("Rest", 90) au lieu du vrai timer.
- **Auto-completion dans `combine`** ([GoalsTabViewModel.kt:128-136](../appli-android/app/src/main/java/com/example/sportapp/viewmodel/GoalsTabViewModel.kt#L128)) — lance `viewModelScope.launch { ... muscleGoalDao.updateStatus(...) }` à chaque émission. Si l'update retrigger le combine et `shouldAutoComplete` reste true, **boucle infinie** théorique. La condition `if (status == "DONE") return false` protège mais fragile.

## 7. Findings 3C

Tous ajoutés à [TODO_FIXES.md](TODO_FIXES.md).

### 🔴 Critique
- **`ExerciseScreenViewModel:49` typage incorrect** — `actualWorkoutDao: ActualWorkoutExerciseDao`. Code dead ou bug runtime selon usage.

### 🟠 Important
- **2 ViewModels vides** (`StatsViewModel`, `HistoryViewModel`) — features stubs, à compléter ou retirer les écrans associés.
- **`AuthManager.skipInterceptorAuth` flag inutilisé** — soit le brancher dans `authInterceptor`, soit le supprimer (code mort).
- **`AuthManager.setUserId(-1)` sentinel** — pattern fragile, préférer `setUserId(null)`.
- **`MuscleGoalsManager` et `GoalsTabViewModel` importent `ui.screens.parseTargetMinimum`** — inversion de dépendance, déjà noté en 3A.
- **`ChronoViewModel.onTimerFinished` hardcode `"Rest", 90`** — la notif dit toujours "Rest 90s" peu importe la vraie durée.
- **`NotificationCenter.post` double-vibration potentielle** — commentaire dans le code l'admet, à clarifier.
- **Logique dupliquée `parseTargetReps`** dans 3 VMs — extraire dans `utils/`.
- **Auto-completion dans `combine` GoalsTabViewModel** — risque de boucle théorique.

### 🟡 Mineur
- **`println` partout dans les VMs** — remplacer par `Log.d` filtrable.
- **Naming class ≠ filename** : `LoginScreenViewModel.kt → LoginViewModel`, `ProfileScreenViewModel.kt → ProfileViewModel`, `RoutineTasksScreenViewModel.kt → RoutineTasksViewModel`. Choisir une convention.
- **`NotificationRepository.build()` en 2 versions** (string + enum). Garder l'enum.
- **3 feature modules Style A déjà en place** (`auth/`, `notifications/`, `settings/`) — pas un bug, juste une découverte qui valide la politique architecturale.
- **Chrono devrait être un feature module** (`chrono/`) pour cohérence — actuellement éclaté entre `viewmodel/`, `ui/components/chronoScreen/` et `MainActivity` overlays.

---

# 3D — Modèles + DAOs Room + TypeConverters

> Audit complet : 22 entités Room (`data/model/`) + 22 DAOs (`data/local/`) + 2 TypeConverters (intégrés dans les fichiers de modèle). Cross-check systématique avec les 22 schémas Pydantic correspondants côté serveur. **Méthode** : lecture intégrale de chaque fichier (consigne utilisateur 2026-05-04 : *"vraiment tout lire"*), tableau de conformité, findings rangés par criticité.

## Sommaire 3D

- [1. Squelette canonique modèle Room](#1--squelette-canonique-modèle-room)
- [2. Squelette canonique DAO (Style A majoritaire) + 3 styles divergents](#2--squelette-canonique-dao-style-a-majoritaire--3-styles-divergents)
- [3. Tableau de conformité 22 modèles](#3--tableau-de-conformité-22-modèles)
- [4. Tableau de conformité 22 DAOs](#4--tableau-de-conformité-22-daos)
- [5. TypeConverters (InstructionsConverter + NotificationDataConverter)](#5--typeconverters)
- [6. Cross-check Pydantic ↔ Room](#6--cross-check-pydantic--room)
- [7. Findings 3D](#7--findings-3d)

## 1. Squelette canonique modèle Room

Le pattern observé sur la majorité des entités (≈18/22) :

```kotlin
@Entity(
    tableName = "snake_case_pluriel",
    indices = [Index(value = ["uuid"], unique = true)]
    [+ foreignKeys = [ForeignKey(...) avec onDelete = CASCADE] si applicable]
)
data class X(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "user_id") val userId: Int,        // ← si Type A user-scoped

    [...champs métier en camelCase Kotlin avec @ColumnInfo(name="snake_case") explicite...],

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null
)
```

**4 invariants attendus** :
1. PK = `uuid: String` non-nullable, **sauf User** (PK = `id: Int` autoGenerate, justifié — pas d'`uuid` côté serveur).
2. Index unique sur `uuid` pour les lookups par UUID (toutes les queries `getXByUUID`).
3. FK `@ForeignKey(... onDelete = CASCADE)` vers les parents directs (entités enfants/junctions).
4. Trio sync : `synced` + `pendingDeletion` + `updatedAt`/`deletedAt`.

## 2. Squelette canonique DAO (Style A majoritaire) + 3 styles divergents

### Style A — `Internal` suffix, public wraps (18/22 DAOs)

```kotlin
@Dao
interface XDao {
    // === OBSERVE (Flow réactif) ===
    @Query("SELECT * FROM xs") fun observeAll(): Flow<List<X>>
    @Query("SELECT * FROM xs WHERE uuid = :uuid LIMIT 1") fun observeByUUID(uuid: String): Flow<X?>

    // === GET ONE-SHOT ===
    @Query("SELECT * FROM xs WHERE uuid = :uuid") suspend fun getXByUUID(uuid: String): X?
    @Query("SELECT * FROM xs") suspend fun getAllOnce(): List<X>

    // === INSERT/UPDATE/DELETE PUBLIC (default impl : copy(updatedAt + synced=false)) ===
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(x: X) {
        insertInternal(x.copy(updatedAt = getNowISO8601(), synced = false))
    }
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(xs: List<X>) { /* idem en bulk */ }
    @Update
    suspend fun updateX(x: X) { /* idem */ }
    @Delete suspend fun delete(x: X)

    // === SYNC ===
    @Query("SELECT * FROM xs WHERE synced = 0") suspend fun getAllUnsynced(): List<X>
    @Query("UPDATE xs SET synced = 1 WHERE uuid = :uuid") suspend fun markAsSynced(uuid: String)
    @Query("UPDATE xs SET synced = 0 WHERE uuid = :uuid") suspend fun markAsUnsynced(uuid: String)
    @Query("SELECT EXISTS(SELECT 1 FROM xs WHERE synced = 0 LIMIT 1)") suspend fun hasUnsynced(): Boolean
    @Query("UPDATE xs SET pendingDeletion = 1, updated_at = :updatedAt WHERE uuid = :uuid")
    suspend fun markAsPendingDeletion(uuid: String, updatedAt: String = getNowISO8601())
    @Query("SELECT * FROM xs WHERE pendingDeletion = 1") suspend fun getPendingDeletions(): List<X>
    @Query("DELETE FROM xs") suspend fun clearAll()

    // === INTERNES (utilisées par les wrappers publics) ===
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertInternal(x: X)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllInternal(xs: List<X>)
    @Update suspend fun updateInternal(x: X)

    // === FROM SERVER (no-op transform : payload tel quel, synced respecté) ===
    suspend fun insertFromServer(x: X) = insertInternal(x)
    suspend fun insertAllFromServer(xs: List<X>) = insertAllInternal(xs)
    suspend fun updateFromServer(x: X) = updateInternal(x)
}
```

**Pourquoi ce pattern** : la séparation public/internal garantit que toute mutation locale pose `synced = false` + `updatedAt` automatiquement. Les `*FromServer` shortcuts permettent au merger d'écrire `synced = true` sans réécrire les flags.

### Style B — préfixe `raw` (2/22 DAOs : `ActualWorkoutDao`, `ActualWorkoutExerciseDao`)

Variante syntaxique : les méthodes annotées Room s'appellent `rawInsert`, `rawInsertAll`, `rawUpdate`, `rawMarkAsPendingDeletion`. Les méthodes publiques **non annotées** (juste `suspend fun`) font le wrapping `copy(updatedAt + synced=false)`.

```kotlin
@Insert(onConflict = REPLACE) suspend fun rawInsert(x: X)
suspend fun insert(x: X) {
    rawInsert(x.copy(updatedAt = getNowISO8601(), synced = false))
}
```

→ Comportement fonctionnel **identique au Style A**. Différence cosmétique.

### Style C — sans wrap (1/22 DAO : `ActualWorkoutSetDao`)

Les méthodes publiques `insert`, `insertAll`, `updateActualWorkoutSet` sont annotées Room et **n'ont PAS de body** :

```kotlin
@Insert(onConflict = REPLACE) suspend fun insert(set: ActualWorkoutSet)
@Insert(onConflict = REPLACE) suspend fun insertAll(sets: List<ActualWorkoutSet>)
@Update suspend fun updateActualWorkoutSet(set: ActualWorkoutSet)
```

**Conséquence** : appeler `setDao.insert(set)` n'écrase pas `synced` ni `updatedAt`. L'appelant doit poser ces champs lui-même via `set.copy(synced = false, updatedAt = getNowISO8601())` (ce que les VMs font implicitement à la construction du data class — `updatedAt = getNowISO8601()` est posé au moment de l'instanciation, pas au moment de l'insert). Idem `markAsPendingDeletion` du même DAO ne touche pas `updated_at`.

→ **Bug latent** : si un VM modifie un set existant avec `set.copy(reps = 12)` (sans toucher `synced` ni `updatedAt`) puis appelle `updateActualWorkoutSet(set)`, la mutation ne sera **jamais re-pushée au serveur** (`synced` reste à `true`). Heureusement, dans la pratique, les VMs utilisent les méthodes par champ (`updateReps`, `updateWeight`, `updateStatus`...) qui posent explicitement `synced = 0`.

### Style "User" — `id: Int` au lieu de `uuid: String` (1/22 DAO)

Justifié (User n'a pas d'`uuid` côté serveur). Mais **ne suit pas** le pattern : pas de `getAllOnce()` (a `getAll(): Flow`... wait si, ligne 57-58, OK), pas d'`updatedAt` (le modèle ne l'a pas), `markAsSynced(id: Int)` et `markAsPendingDeletion(id: Int)` au lieu de `(uuid: String)`. Voir [3B](#3b--architecture-sync) pour le `UserSyncable` correspondant.

## 3. Tableau de conformité 22 modèles

Légende :
- ✅ Conforme au squelette canonique
- ⚠ Divergence mineure (cosmétique ou justifiée)
- 🔴 Bug ou divergence critique

| Modèle | Type | userId | FK CASCADE | Indices | Naming colonnes | Statut |
|---|---|---|---|---|---|---|
| `User` | spécial | `id: Int` PK | aucune | uuid : N/A | `first_name`, `last_name` ✅ | ⚠ pas d'`updatedAt`/`deletedAt` |
| `ActualWorkout` | A | `userId: Int` (sans `@ColumnInfo`!) | aucune | uuid unique | 🔴 col `userId` au lieu de `user_id` ; `is_done` ✅ ; `name` annoté redondant | ⚠ |
| `ActualWorkoutExercise` | B | (via parent) | ActualWorkout + Exercise | uuid unique | 🔴 col `addedManually` au lieu de `added_manually` ; reste OK ; bonus class `*WithWorkoutDateAndSets` | ⚠ |
| `ActualWorkoutSet` | B | (via parent) | ActualWorkoutExercise | uuid unique | `set_order`, `target_reps`, `is_dropset` ✅ | ✅ |
| `AvailableEquipment` | **C global** | aucun | aucune | uuid unique | ✅ snake_case | ⚠ nom suggère user-scoped, mais serveur+Room sont global |
| `CycleWorkout` | C global | aucun | PlannedWorkout + TrainingCycle | uuid unique | `planned_workout_uuid`, `training_cycle_uuid` ✅ | ✅ |
| `Equipment` | C global | aucun | aucune | uuid unique | ✅ | ✅ |
| `Exercise` | A | `userId: Int` `@ColumnInfo("user_id")` | aucune | uuid unique | tout `@ColumnInfo` snake_case ✅ ; `instructions: List<String>?` via converter ; `is_favorite` ✅ ; `last_done` ✅ | ✅ |
| `ExerciseEquipment` | B (junction) | aucun | Exercise + Equipment | uuid unique | ✅ | ✅ |
| `ExerciseMuscle` | B (junction) | aucun | Exercise + Muscle | uuid unique | ✅ ; bonus class `ExerciseMuscleSimple` | ✅ |
| `Muscle` | A | `userId: Int` `@ColumnInfo("user_id")` | aucune | uuid unique | 🔴 `isFavorite` SANS `@ColumnInfo` (col `isFavorite` camelCase) vs Exercise | ⚠ |
| `MuscleGoal` | A | `userId: Int` ✅ | Muscle | uuid unique | 🔴 5 colonnes camelCase : `weekISO`, `priority`, `done`, `target`, `status` (sans `@ColumnInfo`) ; `addedManually` annoté ✅ | ⚠ |
| `MuscleWeeklySummary` | A | `userId: Int` ✅ | Muscle | uuid unique | snake_case complet ✅ | 🟠 entité fantôme à supprimer |
| `Notification` | A | `userId: Int` ✅ | aucune | uuid unique | `read_at`, `archived_at`, `dedupe_key` ✅ ; `data: Map<String, Any>?` via converter ; ⚠ `createdAt: String? = null` non auto-rempli (vs `updatedAt = getNowISO8601()`) | ⚠ |
| `PlannedWorkout` | A | `userId: Int` ✅ | aucune | uuid unique | `day_of_week` ✅ | ✅ |
| `PlannedWorkoutExercise` | B | (via parent) | PlannedWorkout + Exercise | uuid unique | `order` annoté (mot-clé SQL géré) ; `ignored` sans `@ColumnInfo` (mot simple, OK) | ✅ |
| `RoutinePeriod` | A | `userId: Int` ✅ | aucune | uuid unique | `start_time`, `end_time`, `order_index` ✅ (mot-clé SQL géré proprement) | ✅ |
| `RoutineTask` | A | `userId: Int` ✅ | RoutinePeriod | uuid unique | `period_uuid`, `order_index`, `is_active` ✅ | ✅ |
| `RoutineTaskCheck` | A | `userId: Int` ✅ | RoutineTask | uuid unique + **index composite** `(user_id, task_uuid, date)` | `task_uuid`, `is_checked`, `checked_at` ✅ | ✅ |
| `SupersetExercise` | B (junction) | aucun | SupersetGroup + Exercise | uuid unique | `superset_group_uuid`, `order_in_group` ✅ | 🔴 `orderInGroup: Int? = null` nullable mais **Pydantic obligatoire** |
| `SupersetGroup` | A | 🔴 `userId: Int? = null` **nullable** | aucune | uuid unique | snake_case complet ✅ | 🔴 nullable côté Room mais **non-nullable côté Pydantic** → push avec userId=null = 422 |
| `TrainingCycle` | C global | aucun | aucune | uuid unique | `start_date`, `end_date` ✅ ; types String côté Room vs `date` côté Pydantic | ⚠ |

**Résumé** : 11/22 conformes ✅, 9/22 divergences mineures ⚠, 2/22 critiques 🔴 (`SupersetGroup.userId` + `SupersetExercise.orderInGroup` nullables côté Room mais obligatoires côté serveur).

## 4. Tableau de conformité 22 DAOs

| DAO | Style | `getAllOnce` | `getAllUnsynced` | `markAsPendingDeletion` bump `updated_at` ? | `*FromServer` ? | Notes |
|---|---|---|---|---|---|---|
| `UserDao` | spécial | ✅ | ✅ | ❌ pas d'`updated_at` (User n'en a pas) | ✅ | `markAsPendingDeletion(id: Int)` |
| `ActualWorkoutDao` | B (raw) | ✅ | ✅ | ✅ via `rawMarkAsPendingDeletion` | ✅ | beaucoup de queries date/range |
| `ActualWorkoutExerciseDao` | B (raw) | ✅ | ✅ | ✅ | ✅ | bonus query JOIN `getLast3SessionsForExercise` |
| `ActualWorkoutSetDao` | C (no-wrap) | ✅ | ✅ | ❌ ne touche pas `updated_at` (UPDATE simple) | ✅ via `insert(...)` direct (sans wrap) | 🔴 `insert/update` ne posent ni `synced=false` ni `updatedAt` ; **2 méthodes `markAsPendingDeletion`** : `markSetsAsPendingDeletionWithUUID` ET `markAsPendingDeletion` (doublon) |
| `AvailableEquipmentDao` | A | ✅ | ✅ | ✅ via `markAsPendingDeletionInternal` | ✅ | conforme |
| `CycleWorkoutDao` | A | ✅ | ✅ | 🔴 **bug Room** : `@Query` UPDATE + body Kotlin → Room génère le `@Query` qui ne touche pas `updated_at` ; le body qui appellerait `markAsPendingDeletionInternal(uuid, now)` est **dead code** | ✅ | 🔴 `getCycleWorkoutByUUID` aussi ambigu (`@Query` SELECT + body `getAllOnce().find{}`) — confirmé jamais appelé donc cosmétique |
| `EquipmentDao` | A | ✅ | ✅ | ✅ | ✅ | conforme |
| `ExerciseDao` | A | ✅ | ✅ | ✅ via direct `@Query` UPDATE avec `updated_at` | ✅ | bonus `toggleFavorite`, `updateDescription` |
| `ExerciseEquipmentDao` | A | ✅ | ✅ | ✅ via `markAsPendingDeletionInternal` | ✅ | conforme |
| `ExerciseMuscleDao` | A | ✅ | ✅ | ✅ via `markAsPendingDeletionInternal` | ✅ | bonus `getMusclesByExerciseUUID` JOIN |
| `MuscleDao` | A | ✅ | ✅ | ✅ via direct UPDATE | ✅ | bonus `toggleFavorite` ET `updateFavorite` (doublon) |
| `MuscleGoalDao` | A | ✅ | 🔴 **`getAllUnSynced`** (S majuscule, divergence du canonique `getAllUnsynced`) | ✅ | ✅ | 🔴 `deleteAll()` + `clearAll()` doublon (un orphelin, jamais appelé) ; logique métier `parseTargetMinimum` dupliquée en SQL (`updateStatusAccordingToDone`) |
| `MuscleWeeklySummaryDao` | A | ✅ | ✅ | ✅ | ✅ | conforme (mais entité à virer) |
| `NotificationDao` | A | ✅ | ✅ | ✅ via direct UPDATE avec `synced = 0` | ✅ | bonus très propre : `markAsRead`, `markAllAsRead`, `markAsUnread`, `archive`, `unarchive`, `observeUnreadCount`, `observeUnsyncedCount` |
| `PlannedWorkoutDao` | A | ✅ | ✅ | ✅ via direct UPDATE | ✅ | conforme |
| `PlannedWorkoutExerciseDao` | A | ✅ | ✅ | ✅ via direct UPDATE | ✅ | bonus `markAsIgnored`, `markAsNotIgnored`, `markAsPendingDeletionWithPlannedWorkoutUUIDAndExerciseUUID` |
| `RoutinePeriodDao` | A | ✅ | ✅ | ✅ via direct UPDATE | ✅ via expression `=` (style nouveau) | conforme |
| `RoutineTaskDao` | A | ✅ | ✅ | ✅ via direct UPDATE | ✅ via expression `=` | conforme |
| `RoutineTaskCheckDao` | A | ✅ | ✅ | ✅ via direct UPDATE | ✅ via expression `=` | 🔴 **violation 2 couches** : `setChecked()` import `CurrentUserManager` (network) + `showSnackbar` (UI) + `SnackbarType` (UI) ; appelé par `RoutineTasksScreenViewModel` |
| `SupersetExerciseDao` | A | ✅ | ✅ | ✅ via direct UPDATE | ✅ | conforme |
| `SupersetGroupDao` | A | ✅ | ✅ | ✅ via direct UPDATE | ✅ | conforme |
| `TrainingCycleDao` | A | ✅ | ✅ | ✅ via direct UPDATE | ✅ | conforme |

**Résumé** : 16/22 conformes ✅, 3 divergences mineures ⚠ (User justifié, ActualWorkout/AWE Style B), 3 critiques 🔴 (`ActualWorkoutSetDao` no-wrap, `CycleWorkoutDao` bug Room `@Query`+body, `MuscleGoalDao` doublons + naming, `RoutineTaskCheckDao` violation couches).

## 5. TypeConverters

Localisés **dans les fichiers de modèle eux-mêmes** (pas de `data/local/Converters.kt` dédié) :

### `InstructionsConverter` ([Exercise.kt:41-51](../appli-android/app/src/main/java/com/example/sportapp/data/model/Exercise.kt#L41))

```kotlin
class InstructionsConverter {
    @TypeConverter
    fun fromList(value: List<String>?): String? = value?.let { Gson().toJson(it) }

    @TypeConverter
    fun toList(value: String?): List<String>? = value?.let {
        Gson().fromJson(it, object : TypeToken<List<String>>() {}.type)
    }
}
```

- ✅ Nullable-safe (le `?.let` propage `null`)
- ⚠ `Gson()` instancié à chaque appel (non-singleton, waste mineur — préférer un `companion object { val gson = Gson() }`)
- ⚠ Aucun try/catch : si une instruction stockée est JSON malformée (corruption locale, migration partielle), `fromJson` raise `JsonSyntaxException` non interceptée → crash de la query Room qui lit cet exercice.
- ⚠ **Localisation** : dans `Exercise.kt` au lieu de `data/local/Converters.kt` — mélange entité métier et sérialisation. Cosmétique.

### `NotificationDataConverter` ([Notification.kt:53-63](../appli-android/app/src/main/java/com/example/sportapp/data/model/Notification.kt#L53))

```kotlin
class NotificationDataConverter {
    @TypeConverter fun fromMap(value: Map<String, Any>?): String? = ...
    @TypeConverter fun toMap(value: String?): Map<String, Any>? = ...
}
```

- ✅ Nullable-safe
- ⚠ Mêmes remarques (Gson() per call, pas de try/catch, layout dans le fichier de modèle)
- 🟠 **Bug typage subtil documenté dans le code lui-même** : `// Note: Gson will deserialize numbers as Double by default.` Conséquence : `data = mapOf("count" to 5)` → DB → relecture → `data["count"]` est `5.0: Double` (pas `5: Int`). Si un consommateur fait `data["count"] as Int` → `ClassCastException`. Pour l'instant, les usages observés (`NotificationNavigationMapper`, `NotificationOverlayHost`) lisent `data["screen"]` et `data["uuid"]` (toujours String), donc pas d'occurrence du bug. Mais piège latent.

### Inscription dans `AppDatabase`

```kotlin
@TypeConverters(InstructionsConverter::class, NotificationDataConverter::class)
abstract class AppDatabase : RoomDatabase()
```

Les 2 sont enregistrés au niveau base. ✅

## 6. Cross-check Pydantic ↔ Room

Méthode : pour chaque entité, vérifier que les types Kotlin matchent les types Pydantic et que les noms JSON envoyés par Gson (qui utilise le **field name Kotlin** car `FieldNamingPolicy.IDENTITY` cf. 3A) correspondent aux **alias Pydantic** (ou aux field names si pas d'alias).

### Compatibilité globale

- ✅ **Tous les schémas Pydantic** ont `populate_by_name: True` côté `Out` (parfois aussi côté `Base`/`Create`) → acceptent **les deux formes** snake_case + camelCase en input.
- ✅ **Aliases camelCase** : `Field(..., alias="isDone")`, `Field(..., alias="userId")`, etc. — Gson envoie `isDone` (camelCase Kotlin) qui matche l'alias.
- ✅ **Le flux écriture serveur→client** marche aussi : Pydantic sérialise par défaut sous le **nom du field Python** (snake_case) sauf si on configure `by_alias=True` au `model_dump`. À vérifier si les routers utilisent `by_alias`.

### Divergences trouvées

| Entité | Pydantic | Room | Sévérité |
|---|---|---|---|
| `SupersetGroup` | `user_id: int` **non-nullable** dans Base | `userId: Int? = null` **nullable** | 🔴 push avec `userId=null` → 422 |
| `SupersetExercise` | `order_in_group: int = Field(..., alias="orderInGroup")` **non-nullable** | `orderInGroup: Int? = null` **nullable** | 🔴 push avec `orderInGroup=null` → 422 |
| `User` | `UserOut` = `{id, username}` seulement (pas de first/last name, pas d'`updatedAt`) | `User` Room = `{id, username, firstName, lastName, synced, pendingDeletion}` | ⚠ asymétrie : champs locaux jamais renvoyés par `/me`. `firstName`/`lastName` jamais utilisés côté UI (vérifié par grep — 0 occurrence). |
| `ActualWorkout` | `uuid: Optional[str] = None` | `uuid: String` non-nullable | ⚠ si serveur push uuid=null, Gson rejette ; si client push uuid=null, le crud serveur ne génère pas (déjà cf. TODO 196) |
| `MuscleGoal` | `weekISO: str` (sans alias) | `weekISO` (sans `@ColumnInfo`) | ⚠ cohérent côté serialization (`"weekISO"` JSON → match) mais **anomalie naming DB** (col `weekISO` camelCase au milieu de cols snake_case) |
| `TrainingCycle` | `start_date: date`, `end_date: date` (type Python `date`) | `startDate: String`, `endDate: String` | ⚠ Pydantic parse `"YYYY-MM-DD"` String en `date` ✅. Mais si Android envoie `"2025-12-31T00:00:00"` (datetime ISO complet), parse échoue côté serveur. À surveiller. |
| `MuscleWeeklySummary` | `week_start_date: date` | `weekStartDate: String` | ⚠ même remarque |
| `Muscle` | `MuscleOut` a `id: int` ET `user_id: int` | `Muscle` Room sans `id` Int | ⚠ Gson ignore le champ `id` côté Android (pas de field correspondant dans le data class). OK fonctionnellement. |
| `ActualWorkout` | `is_done: bool` non-nullable obligatoire | `isDone: Boolean = false` default | ✅ compatible |
| `Exercise` | `is_favorite: bool` non-nullable obligatoire | `isFavorite: Boolean = false` default | ✅ |
| `Notification.data` | `Optional[Dict[str, Any]]` | `Map<String, Any>?` via converter | ✅ aligné, mais bug Gson Int→Double cf. §5 |

### Naming colonnes Room — incohérences notables

À côté du JSON wire format (qui marche), il y a une incohérence **dans le nom des colonnes SQLite** générées par Room :

| Modèle | Colonne attendue (snake_case) | Colonne réelle | Effet |
|---|---|---|---|
| `ActualWorkout.userId` | `user_id` | `userId` | 🟠 `WHERE user_id = ?` ne marche pas → toutes les queries multi-user de cette table cassées si elles utilisent le snake_case |
| `ActualWorkoutExercise.addedManually` | `added_manually` | `addedManually` | 🟡 cosmétique (pas utilisé en query) |
| `Muscle.isFavorite` | `is_favorite` (cf. Exercise) | `isFavorite` | 🟡 incohérent avec Exercise |
| `MuscleGoal.weekISO`/`priority`/`done`/`target`/`status` | `week_iso`/`priority`/... | `weekISO`/`priority`/... | 🟡 mélange dans la même table |

→ **Conséquence pratique** : aucune query observée n'utilise `WHERE user_id = ?` sur `actual_workouts` (les queries existantes sont par `uuid`, `date`, `day` — toutes OK). Donc pas de crash actuel, mais piège latent dès que quelqu'un voudra filtrer les workouts par user (ce qui devrait arriver vu la politique sécurité user-scoped).

## 7. Findings 3D

Tous les items sont reportés à [TODO_FIXES.md](TODO_FIXES.md). Récap par criticité :

### 🔴 Critique

- **`RoutineTaskCheckDao.setChecked` viole 2 couches** ([RoutineTaskCheckDao.kt:54-92](../appli-android/app/src/main/java/com/example/sportapp/data/local/RoutineTaskCheckDao.kt#L54)) — un `@Dao` (data layer) `import CurrentUserManager` (network) ET `import showSnackbar` + `SnackbarType` (UI Compose). Confirmé 0 autre DAO ne le fait. Refactor : déplacer la logique dans `RoutineTasksScreenViewModel` (qui a déjà accès aux deux).
- **`SupersetGroup.userId: Int? = null` nullable côté Room mais non-nullable côté Pydantic** — push avec `userId=null` → 422. Lié au bug serveur déjà noté ([TODO 166](TODO_FIXES.md) : `user_id` ne devrait pas être dans `Base`). Trancher : soit retirer `user_id` de `SupersetGroupBase` côté serveur (et donc rendre Room user-scoped strict via `Depends`), soit rendre `userId` non-nullable côté Room.
- **`SupersetExercise.orderInGroup: Int? = null` nullable** ([SupersetExercise.kt:33](../appli-android/app/src/main/java/com/example/sportapp/data/model/SupersetExercise.kt#L33)) — Pydantic exige `order_in_group: int`, donc push avec null = 422. Rendre non-nullable côté Room (avec un default sensible, par ex. `0`).
- **`ActualWorkoutSetDao` Style C sans wrap auto** — `insert`, `insertAll`, `updateActualWorkoutSet` ne posent pas `synced=false` ni `updatedAt`. Aligner sur Style A pour éviter qu'un futur dev oublie de poser ces flags. **Bug actuel masqué** car les VMs construisent les sets fraîchement (default `getNowISO8601()` au ctor) et utilisent les méthodes par champ pour les updates.
- **`CycleWorkoutDao` bug Room `@Query` + body Kotlin** ([CycleWorkoutDao.kt:60-64](../appli-android/app/src/main/java/com/example/sportapp/data/local/CycleWorkoutDao.kt#L60)) — `markAsPendingDeletion(uuid)` annoté `@Query("UPDATE ... pendingDeletion = 1 ...")` (sans toucher `updated_at`) avec un body Kotlin qui appellerait `markAsPendingDeletionInternal(uuid, now)` (qui touche `updated_at`). **Room génère le code à partir du `@Query` et ignore le body** → `updated_at` n'est PAS bumped → le merger 3-way pourrait ignorer la suppression. Confirmé appelé 1 fois depuis `SyncSettingsViewModel:401`. Solution : retirer le `@Query`, garder seulement le body (qui appelle `markAsPendingDeletionInternal`). Idem pour `getCycleWorkoutByUUID:20-23` (mais celui-là jamais appelé, juste cosmétique).
- **`User` Room sans `updatedAt`/`deletedAt`** — empêche tout 3-way merge / soft-delete propre. `UserSyncable` existe mais ne peut pas comparer les timestamps. Cohérent avec la politique sécurité (users non-syncés depuis client) mais à clarifier : soit retirer `synced`/`pendingDeletion` aussi (= "User est read-only côté client"), soit ajouter les timestamps.
- **`MuscleGoalDao.deleteAll() + clearAll()` doublon** ([MuscleGoalDao.kt:80-81](../appli-android/app/src/main/java/com/example/sportapp/data/local/MuscleGoalDao.kt#L80) + [l. 115-116](../appli-android/app/src/main/java/com/example/sportapp/data/local/MuscleGoalDao.kt#L115)) — confirmé `deleteAll` jamais appelé (orphelin). Supprimer.

### 🟠 Important

- **`ActualWorkout.userId` SANS `@ColumnInfo(name = "user_id")`** ([ActualWorkout.kt:18](../appli-android/app/src/main/java/com/example/sportapp/data/model/ActualWorkout.kt#L18)) — la colonne SQLite s'appelle `userId` (camelCase). Toute future query `WHERE user_id = ?` cassera. Aligner sur le canonique en ajoutant `@ColumnInfo(name = "user_id")`. ⚠ Bump migration Room nécessaire.
- **`Muscle.isFavorite` / `MuscleGoal.weekISO`/`priority`/`done`/`target`/`status` SANS `@ColumnInfo` snake_case** — colonnes camelCase incohérentes avec le reste. Aligner. ⚠ Migration Room nécessaire.
- **`ActualWorkoutExercise.addedManually` SANS `@ColumnInfo`** — col `addedManually` au lieu de `added_manually`. Cosmétique mais incohérent. ⚠ Migration Room nécessaire.
- **`MuscleGoalDao.getAllUnSynced` (S majuscule)** — divergence du canonique `getAllUnsynced`. Renommer.
- **`MuscleGoalDao.updateStatusAccordingToDone` SQL embarque la logique métier `parseTargetMinimum`** ([MuscleGoalDao.kt:58-75](../appli-android/app/src/main/java/com/example/sportapp/data/local/MuscleGoalDao.kt#L58)) — duplique ce qui est en Kotlin (`parseTargetMinimum` qui vit dans `ui.screens` !). 2 sources de vérité → divergence garantie. À unifier en Kotlin (plus facile à tester, et évite la dépendance UI déjà notée en 3A).
- **`ActualWorkoutSetDao` 2 méthodes `markAsPendingDeletion`** ([ActualWorkoutSetDao.kt:108-115](../appli-android/app/src/main/java/com/example/sportapp/data/local/ActualWorkoutSetDao.kt#L108)) — `markSetsAsPendingDeletionWithUUID` et `markAsPendingDeletion` font la même requête (UPDATE par uuid). Doublon, garder le canonique `markAsPendingDeletion`.
- **`MuscleDao.toggleFavorite` + `updateFavorite`** ([MuscleDao.kt:38-42](../appli-android/app/src/main/java/com/example/sportapp/data/local/MuscleDao.kt#L38)) — même UPDATE, deux noms. Garder `updateFavorite` (le nom `toggleFavorite` est trompeur car la query ne fait pas de toggle, elle affecte la valeur passée).
- **`Notification.createdAt: String? = null` non auto-rempli** au ctor (vs `updatedAt = getNowISO8601()`) — fragile : un appelant qui construit un `Notification(...)` sans passer par `NotificationRepository.build()` oubliera `createdAt`. Mettre `createdAt: String? = getNowISO8601()` par défaut. **Confirmé** : `NotificationRepository.build()` pose la valeur, donc en pratique aujourd'hui le bug ne se manifeste pas.
- **TypeConverters : `Gson()` per call** — instancier en `companion object { val gson = Gson() }` partagé.
- **`NotificationDataConverter` bug Int→Double Gson** — risque de `ClassCastException` si un consumer fait `data["count"] as Int`. Soit changer la signature en `Map<String, String>`, soit imposer une convention "tout en string sauf datetime".

### 🟡 Mineur

- **TypeConverters dans les fichiers de modèle** au lieu de `data/local/Converters.kt` dédié — divergence layout (cosmétique).
- **`User.firstName` / `User.lastName` champs Room non utilisés** (0 occurrence dans le code UI / VMs) — **et non renvoyés par `/me`**. Soit supprimer ces champs côté Room (avec migration), soit étendre `UserOut` côté serveur pour les renvoyer.
- **`User.username` sans index** — query `WHERE username = ?` (s'il y en a) lente si beaucoup d'users. Add `@Index(value = ["username"], unique = true)`.
- **`Notification` indices manquants** : pas d'index sur `created_at` ni sur `(user_id, created_at)` alors que `observeAll() ORDER BY created_at DESC` est l'observe principal.
- **`SupersetExercise.orderInGroup` nullable** (déjà 🔴 ci-dessus, mais aussi étrange sémantiquement : `order` ne devrait jamais être null).
- **2 data classes "projection" dans les fichiers de modèle** : `ActualWorkoutExerciseWithWorkoutDateAndSets`, `ExerciseMuscleSimple`. Pas un bug, mais à isoler dans `data/model/projections/` pour clarté.
- **`CycleWorkoutDao.getCycleWorkoutByUUID`** ambigu (`@Query` + body Kotlin), confirmé jamais appelé → soit clarifier (retirer le body), soit supprimer la fonction (orpheline).
- **2 styles syntaxiques DAO co-existent** (Style A `Internal` vs Style B `raw`) — fonctionnellement équivalents. Choisir l'un et migrer l'autre pour cohérence (la politique squelette uniforme l'impose). Style A est majoritaire.
- **`exportSchema = false`** sur Room (déjà 3A) — bloque la diff de schéma versionnée nécessaire pour le système de migrations.

### Réponses aux questions cross-stack ouvertes

| Question levée en 3B/3A | Réponse 3D |
|---|---|
| L'app dépend-elle de `id: Int` ou `uuid: String` côté modèles ? | **`uuid: String` partout sauf User** (PK `id: Int` autoGenerate, justifié — User n'a pas d'`uuid` côté serveur). Confirmé sur les 22 modèles. |
| Les FK Room sont-elles déclarées ? | **Oui pour 9 entités** (toutes les junctions et entités enfants : ActualWorkoutExercise, ActualWorkoutSet, CycleWorkout, ExerciseEquipment, ExerciseMuscle, MuscleGoal, MuscleWeeklySummary, PlannedWorkoutExercise, RoutineTask, RoutineTaskCheck, SupersetExercise) avec `onDelete = CASCADE`. **Non pour les FK vers User** (cohérent : User côté Android `id` ≠ User côté serveur `id` selon le device). Conséquence : un cleanup local d'un user n'enchaîne pas les CASCADE → résidus orphelins. |
| Le mapping snake_case JSON ↔ camelCase Kotlin marche-t-il ? | **Oui** grâce aux aliases Pydantic + Gson `IDENTITY`. **Exception** : 1 col camelCase dans `MuscleGoal.weekISO` (côté serveur ET Room) — anomalie symétrique à conserver telle quelle (ou migrer côté serveur en `week_iso` avec `Field(..., alias="weekISO")`). |
| Les `instructions` côté serveur sont-elles bien envoyées au handler WS ? | À auditer en 4 (tableau triggers). Le handler `ExerciseSyncHandler` ne les extrait pas (TODO 3B), mais `RemoteDataMerger` qui passe par REST GET reçoit l'objet complet `Exercise` désérialisé par Gson → `instructions` est inclus si le payload `/exercises` le contient. |

---

*Sous-étape 3D terminée. Volume audité : 22 modèles + 22 DAOs + 2 TypeConverters + 22 schémas Pydantic en cross-check = 68 fichiers. Étape 3 (Android) **complète**. Étape suivante : 4 (Bases de données — Postgres + Room schémas, intégrité, anomalies).*

---

*Sous-étape 3C terminée. Étape suivante : 3D (modèles + DAOs Room + TypeConverters). Volume : 22 modèles + 22 DAOs + 2 TypeConverters.*
