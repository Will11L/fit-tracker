package com.example.sportapp.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.feature.chrono.ui.ChronoScreen
import com.example.sportapp.feature.exercises.ui.ExerciseStatsScreen
import com.example.sportapp.feature.muscles.ui.MuscleStatsScreen
import com.example.sportapp.feature.stats.ui.StatsScreen
import com.example.sportapp.designsystem.theme.SportAppTheme
import dagger.hilt.android.AndroidEntryPoint
import com.example.sportapp.designsystem.bottomNavigationBar.BottomNavBar
import com.example.sportapp.designsystem.common_components.AppSnackbarHost
import com.example.sportapp.designsystem.drawer.DrawerContent
import com.example.sportapp.feature.delavier_method.ui.DelavierMethodScreen
import com.example.sportapp.feature.calendar.ui.CalendarViewScreen
import com.example.sportapp.feature.conversations.ui.ConversationsScreen
import com.example.sportapp.feature.exercises.ui.ExerciseListScreen
import com.example.sportapp.feature.exercises.ui.ExerciseScreen
import com.example.sportapp.feature.settings.ui.ExportDatasScreen
import com.example.sportapp.feature.home.ui.HomeScreen
import com.example.sportapp.feature.settings.ui.LanguageDisplayScreen
import com.example.sportapp.feature.auth.ui.LogoutScreen
import com.example.sportapp.feature.notifications.ui.NotificationsScreen
import com.example.sportapp.feature.nutrition.ui.FoodCatalogueScreen
import com.example.sportapp.feature.nutrition.ui.FoodDetailScreen
import com.example.sportapp.feature.nutrition.ui.NutritionGoalsScreen
import com.example.sportapp.feature.nutrition.ui.NutritionJournalScreen
import com.example.sportapp.feature.nutrition.ui.NutritionStatsScreen
import com.example.sportapp.feature.nutrition.ui.RecipesScreen
import com.example.sportapp.feature.profile.ui.ProfileScreen
import com.example.sportapp.feature.session.ui.SessionExerciseScreen
import com.example.sportapp.feature.planning.ui.WeekViewScreen
import com.example.sportapp.feature.session.ui.SessionTab
import kotlinx.coroutines.launch
import com.example.sportapp.feature.muscles.ui.MuscleListScreen
import com.example.sportapp.feature.muscles.ui.MuscleScreen
import com.example.sportapp.feature.equipment.ui.EquipmentDetailScreen
import com.example.sportapp.feature.equipment.ui.EquipmentListScreen
import com.example.sportapp.feature.planning.ui.PlannedWorkoutScreen
import com.example.sportapp.feature.auth.ui.SplashScreen
import com.example.sportapp.feature.settings.ui.SyncSettingsScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.sportapp.feature.chrono.ui.overlay.MiniChronoOverlay
import com.example.sportapp.feature.demo_tour.ui.DemoTourViewModel
import com.example.sportapp.feature.demo_tour.ui.components.DemoCaptionOverlay
import com.example.sportapp.feature.demo_tour.ui.components.LocalDemoTourActiveTarget
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import com.example.sportapp.feature.settings.ui.SettingsScreen
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import com.example.sportapp.feature.chrono.ui.ChronoScreenViewModel
import com.example.sportapp.feature.session.viewmodel.SessionTabViewModel
import kotlinx.coroutines.CoroutineScope
import androidx.compose.animation.*
import androidx.navigation.NavBackStackEntry
import com.example.sportapp.core.network.TokenManager
import com.example.sportapp.core.sync.SyncEvents
import com.example.sportapp.feature.notifications.domain.NotificationCenter
import com.example.sportapp.feature.notifications.domain.NotificationNavigationMapper
import com.example.sportapp.feature.notifications.ui.NotificationOverlayHost
import com.example.sportapp.feature.notifications.ui.NotificationViewModel
import com.example.sportapp.feature.chrono.ui.overlay.MiniTimerOverlay
import com.example.sportapp.feature.auth.ui.LoginScreen
import com.example.sportapp.feature.auth.ui.SignupScreen
import com.example.sportapp.feature.routines.ui.TasksScreen
import javax.inject.Inject
import com.example.sportapp.designsystem.theme.appColors


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var notificationCenter: NotificationCenter

    /** Lu au boot pour résoudre le themeMode courant (LIGHT/DARK/SYSTEM)
     *  et le passer à SportAppTheme. */
    @Inject lateinit var onboardingRepo: com.example.sportapp.feature.onboarding.data.OnboardingRepository

    /** Singleton observé pour l'overlay "Setting up your workouts..." rendu
     *  globalement pendant la transition onboarding -> home (couvre l'insert
     *  sample data + nav fade + 1er render HomeScreen). */
    @Inject lateinit var postOnboardingSetupState: com.example.sportapp.feature.onboarding.data.PostOnboardingSetupState

    private data class DeepLinkNav(
        val route: String,
        val notificationUuid: String?
    )

    private val deepLinkRoutes = kotlinx.coroutines.flow.MutableStateFlow<DeepLinkNav?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ : demander la permission POST_NOTIFICATIONS (popup système).
        // La popup système suffit ; pas de redirect Settings en plus (l'onboarding
        // step 3 a un call-to-action explicite si l'user veut réessayer plus tard).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }

        handleDeepLinkIntent(intent)

        enableEdgeToEdge()

        setContent {
            // i18n live preview GLOBAL (Bug fix 2026-05-12) : on observe le
            // OnboardingPreferences (themeMode + appLocale) une fois ici et on
            // override LocalContext + LocalConfiguration toute l'app via
            // CompositionLocalProvider. Au tap "English" dans Settings, le
            // DataStore est update -> ce Flow re-emit -> remember(appLocale)
            // recompute -> toute l'app recompose en EN instant, sans recreation
            // d'Activity. Cf. pattern OnboardingScreen Session A polish.
            val prefs = onboardingRepo.preferences
                .collectAsState(initial = com.example.sportapp.feature.onboarding.data.OnboardingPreferences())
                .value
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (prefs.themeMode) {
                com.example.sportapp.feature.onboarding.data.ThemeMode.LIGHT -> false
                com.example.sportapp.feature.onboarding.data.ThemeMode.DARK -> true
                com.example.sportapp.feature.onboarding.data.ThemeMode.SYSTEM -> systemDark
            }

            // Build localized Context + Configuration une seule fois par appLocale.
            // SYSTEM = pas d'override (utilise la locale Android systeme).
            // FIX 2026-05-12 crash : createConfigurationContext seul renvoie un
            // ContextImpl SANS Activity dans baseContext -> hiltViewModel()
            // crashe avec "Expected an activity context for HiltViewModelFactory".
            // -> on enveloppe dans un ContextWrapper(activity) qui delegate
            // getResources()/getAssets() au ConfigurationContext, mais preserve
            // l'Activity comme baseContext (Hilt.findActivity() peut walker la chain).
            val baseContext = androidx.compose.ui.platform.LocalContext.current
            val baseConfig = androidx.compose.ui.platform.LocalConfiguration.current
            val localizedConfig = remember(prefs.appLocale, baseConfig) {
                val tag = prefs.appLocale.tag
                if (tag == null) baseConfig
                else android.content.res.Configuration(baseConfig).apply {
                    setLocale(java.util.Locale.forLanguageTag(tag))
                }
            }
            val localizedContext = remember(localizedConfig, baseContext) {
                val tag = prefs.appLocale.tag
                if (tag == null) baseContext
                else {
                    val configCtx = baseContext.createConfigurationContext(localizedConfig)
                    object : android.content.ContextWrapper(baseContext) {
                        override fun getResources(): android.content.res.Resources = configCtx.resources
                        override fun getAssets(): android.content.res.AssetManager = configCtx.assets
                    }
                }
            }

          CompositionLocalProvider(
              androidx.compose.ui.platform.LocalContext provides localizedContext,
              androidx.compose.ui.platform.LocalConfiguration provides localizedConfig,
          ) {
            SportAppTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                val startDestination = remember {
                    if (TokenManager.token.isNullOrBlank()) Routes.LOGIN else Routes.SPLASH
                }
                val navBackStackEntry = navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry.value?.destination?.route

                val isAuthScreen = currentRoute == null
                    || currentRoute == Routes.SPLASH
                    || currentRoute == Routes.LOGIN
                    || currentRoute == Routes.LOGOUT
                    || currentRoute == Routes.SIGNUP
                    || currentRoute == Routes.ONBOARDING
                val isDrawerGestureEnabled = !isAuthScreen && currentRoute != Routes.DELAVIER_METHOD

                val snackbars by SnackbarController.snackbars.collectAsState()

                // Dernier « retour » système (pile de nav vide → l'app se fermerait) :
                // confirmation « Quitter l'application ? » à la place. Le handler du
                // NavHost (actif tant qu'il peut dépiler) et ceux des écrans priment ;
                // celui-ci ne prend la main qu'au tout dernier retour.
                var showExitDialog by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                val canPopBack = navBackStackEntry.value != null &&
                    navController.previousBackStackEntry != null
                androidx.activity.compose.BackHandler(enabled = !canPopBack && !showExitDialog) {
                    showExitDialog = true
                }
                if (showExitDialog) {
                    com.example.sportapp.designsystem.common_components.ConfirmationDialog(
                        title = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.exit_app_title),
                        message = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.exit_app_message),
                        confirmButtonText = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.exit_app_confirm),
                        confirmButtonColor = appColors.primaryAction,
                        onConfirm = { finish() },
                        onDismiss = { showExitDialog = false },
                    )
                }

                val chronoScreenViewModel = hiltViewModel<ChronoScreenViewModel>()
                val demoTourViewModel = hiltViewModel<DemoTourViewModel>()

                // Observe demo tour step + auto-navigate vers la route associée au
                // targetRouteKind. WELCOME/GOODBYE = pas de nav, overlay seul.
                val demoTourStep by demoTourViewModel.currentStep.collectAsState()
                val firstSampleUuid by demoTourViewModel.firstSampleWorkoutUuid.collectAsState()
                val activeTourTarget by demoTourViewModel.activeTargetKey.collectAsState()
                LaunchedEffect(demoTourStep) {
                    val step = demoTourStep ?: return@LaunchedEffect
                    val target: String? = when (step.targetRouteKind) {
                        com.example.sportapp.feature.demo_tour.domain.TargetRouteKind.NONE -> null
                        com.example.sportapp.feature.demo_tour.domain.TargetRouteKind.STATS -> Routes.STATS
                        com.example.sportapp.feature.demo_tour.domain.TargetRouteKind.CALENDAR -> Routes.CALENDAR
                        com.example.sportapp.feature.demo_tour.domain.TargetRouteKind.SESSION -> firstSampleUuid?.let { Routes.session(it) }
                        com.example.sportapp.feature.demo_tour.domain.TargetRouteKind.PROGRAM -> Routes.PROGRAM
                        com.example.sportapp.feature.demo_tour.domain.TargetRouteKind.CHRONO -> Routes.CHRONO
                    }
                    if (target != null) {
                        // Skip nav si on est déjà sur la bonne base route -- évite le flash
                        // noir de transition NavHost entre 2 sub-steps de la même page (ex.
                        // STATS_RANGE -> STATS_CHART qui restent tous deux sur Routes.STATS).
                        val currentBase = navController.currentBackStackEntry
                            ?.destination?.route?.substringBefore("/")
                        val targetBase = target.substringBefore("/")
                        if (currentBase != targetBase) {
                            navController.navigate(target) {
                                launchSingleTop = true
                            }
                        }
                    }
                }

                // À la fin du tour (Got it ou Skip) : retour auto vers HOME.
                LaunchedEffect(Unit) {
                    demoTourViewModel.tourEndedEvent.collect {
                        navController.navigate(Routes.HOME) {
                            launchSingleTop = true
                        }
                    }
                }

                // V4.5 — Token expiré (401 sur n'importe quel call REST) : on
                // affiche une snackbar et on force la nav vers login. L'Authenticator
                // OkHttp dans RetrofitInstance a déjà clear le token + le user_id.
                val sessionExpiredMsg = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.session_expired)
                LaunchedEffect(Unit) {
                    SyncEvents.onTokenExpired.collect {
                        showSnackbar(
                            message = sessionExpiredMsg,
                            type = SnackbarType.ERROR,
                        )
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }

                // A placer après navController
                val notificationVm = hiltViewModel<NotificationViewModel>()
                LaunchedEffect(Unit) {
                    deepLinkRoutes.collect { nav ->
                        if (nav == null) return@collect

                        nav.notificationUuid?.let { notificationVm.markAsRead(it) }

                        navController.navigate(nav.route) {
                            launchSingleTop = true
                            restoreState = true
                        }

                        // important: consommer l'event pour éviter de renaviguer à recomposition
                        deepLinkRoutes.value = null
                    }
                }

                val showSetupOverlay by postOnboardingSetupState.isVisible.collectAsState()

                CompositionLocalProvider(LocalDemoTourActiveTarget provides activeTourTarget) {
                Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    snackbarHost = {
                        AppSnackbarHost(snackbars = snackbars)
                    },
                    bottomBar = {
                        if (!isAuthScreen) {
                            // A7 : le 1er slot est désormais la bascule Sport/Nutrition
                            // (remplace le burger). Le drawer s'ouvre au swipe depuis
                            // le bord (gesturesEnabled = isDrawerGestureEnabled).
                            BottomNavBar(navController = navController)
                        }
                    }
                ) { innerPadding ->
                    if (isAuthScreen) {
                        AppNavigation(
                            navController = navController,
                            drawerState = drawerState,
                            scope = scope,
                            startDestination = startDestination,
                            modifier = Modifier.padding(innerPadding),
                            chronoScreenViewModel = chronoScreenViewModel,
                            demoTourViewModel = demoTourViewModel,
                        )
                    } else {
                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            gesturesEnabled = isDrawerGestureEnabled,
                            drawerContent = {
                                DrawerContent(
                                    navController = navController,
                                    drawerState = drawerState,
                                    bottomPadding = innerPadding.calculateBottomPadding()
                                )
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                AppNavigation(
                                    navController = navController,
                                    drawerState = drawerState,
                                    scope = scope,
                                    startDestination = startDestination,
                                    modifier = Modifier.fillMaxSize(),
                                    chronoScreenViewModel = chronoScreenViewModel,
                                    demoTourViewModel = demoTourViewModel,
                                )

                                NotificationOverlayHost(
                                    events = notificationCenter.overlayEvents,
                                    onClick = { notif ->
                                        val target = NotificationNavigationMapper.resolve(notif)
                                        val route = target?.route ?: Routes.NOTIFICATIONS

                                        val current = navController.currentBackStackEntry?.destination?.route
                                        if (current?.substringBefore("/") == route.substringBefore("/")) {
                                            // déjà dessus → ne rien faire (évite le flash)
                                            if (target?.markAsReadBeforeNavigate == true) {
                                                notificationVm.markAsRead(notif.uuid)
                                            }
                                            return@NotificationOverlayHost
                                        }

                                        if (target?.markAsReadBeforeNavigate == true) {
                                            notificationVm.markAsRead(notif.uuid)
                                        }

                                        navController.navigate(route) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )


                                MiniChronoOverlay(
                                    viewModel = chronoScreenViewModel,
                                    onOpenChrono = {
                                        val current = navController.currentBackStackEntry?.destination?.route
                                        if (current?.substringBefore("/") == Routes.CHRONO) return@MiniChronoOverlay

                                        navController.navigate(Routes.CHRONO) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.TopStart)
                                )

                                MiniTimerOverlay(
                                    viewModel = chronoScreenViewModel,
                                    onOpenChrono = {
                                        val current = navController.currentBackStackEntry?.destination?.route
                                        if (current?.substringBefore("/") == Routes.CHRONO) return@MiniTimerOverlay

                                        navController.navigate(Routes.CHRONO) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.TopStart)
                                )

                                // Demo tour caption overlay (bottom card). Visible UNIQUEMENT en
                                // mode authentifié (ce branch else) -- pas sur login/signup/
                                // onboarding. Conditionnel via AnimatedVisibility sur step != null.
                                // Le highlight des éléments cibles est porté directement par les
                                // composables instrumentés via Modifier.demoHighlight("key") +
                                // LocalDemoTourActiveTarget provider plus haut.
                                DemoCaptionOverlay(
                                    viewModel = demoTourViewModel,
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                )
                            }
                        }
                    }
                }

                // Global overlay "Setting up your workouts..." -- visible
                // pendant la transition onboarding -> home (singleton drive).
                // Au-dessus du Scaffold pour couvrir tout (incl. la nav fade).
                if (showSetupOverlay) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(64.dp),
                                color = com.example.sportapp.designsystem.theme.appColors.primaryAction,
                                strokeWidth = 6.dp,
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                            )
                            Text(
                                text = "Setting up the demo tour...",
                                color = appColors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                }  // Box global overlay container
                }  // CompositionLocalProvider LocalDemoTourActiveTarget
            }  // SportAppTheme
          }  // CompositionLocalProvider LocalContext + LocalConfiguration (i18n global)
        }
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val uri = intent?.data ?: return

        // attendu: sportapp://notif/<route...>?uuid=...
        if (uri.scheme != "sportapp" || uri.host != "notif") return

        val route = uri.pathSegments.joinToString("/")
        if (route.isBlank()) return

        val uuid = uri.getQueryParameter("uuid")

        deepLinkRoutes.value = DeepLinkNav(route = route, notificationUuid = uuid)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLinkIntent(intent)
    }

}

private val mainRoutesOrder = listOf(Routes.CALENDAR, Routes.HOME, Routes.CHRONO, Routes.STATS)

private fun String?.baseRoute(): String? = this?.substringBefore("/")

private fun AnimatedContentTransitionScope<NavBackStackEntry>.direction(): Int {
    val from = initialState.destination.route.baseRoute()
    val to = targetState.destination.route.baseRoute()

    val fromIndex = mainRoutesOrder.indexOf(from)
    val toIndex = mainRoutesOrder.indexOf(to)

    // si une route n'est pas dans le "rail" principal, pas de direction
    if (fromIndex == -1 || toIndex == -1) return 0

    return when {
        toIndex > fromIndex -> 1   // vers la droite
        toIndex < fromIndex -> -1  // vers la gauche
        else -> 0
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.railEnter(): EnterTransition {
    val dir = direction()
    return when (dir) {
        1  -> slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))    // arrive de la droite
        -1 -> slideInHorizontally(tween(300)) { -it } + fadeIn(tween(300))   // arrive de la gauche
        else -> fadeIn(tween(300))
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.railExit(): ExitTransition {
    val dir = direction()
    return when (dir) {
        1  -> slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300)) // sort à gauche
        -1 -> slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300))  // sort à droite
        else -> fadeOut(tween(300))
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    drawerState: DrawerState,
    scope: CoroutineScope,
    startDestination: String,
    modifier: Modifier = Modifier,
    chronoScreenViewModel: ChronoScreenViewModel,
    demoTourViewModel: DemoTourViewModel,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(route = Routes.LOGIN,
            enterTransition = { slideInHorizontally(animationSpec = tween(0)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(0)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(0)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(0)) { it } + fadeOut() }
        ) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.SPLASH) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onCreateAccount = {
                    navController.navigate(Routes.SIGNUP)
                }
            )
        }

        composable(route = Routes.SIGNUP,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            SignupScreen(
                onSignupSuccess = {
                    navController.navigate(Routes.SPLASH) {
                        // Pop login + signup pour empecher le retour en arriere
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onBackToLogin = { navController.popBackStack() }
            )
        }

        composable(route = Routes.SPLASH,
            enterTransition = { slideInHorizontally(animationSpec = tween(0)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(0)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(0)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(0)) { it } + fadeOut() }
        ) {
            SplashScreen(navController = navController)
        }

        composable(
            route = Routes.HOME,
            enterTransition = { railEnter() },
            exitTransition = { railExit() },
            popEnterTransition = { railEnter() },
            popExitTransition = { railExit() }
        ) {
            HomeScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }

        // Bottom navigation composables
        composable(
            route = Routes.CHRONO,
            enterTransition = { railEnter() },
            exitTransition = { railExit() },
            popEnterTransition = { railEnter() },
            popExitTransition = { railExit() }
        ) {
            ChronoScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } },
                viewModel = chronoScreenViewModel
            )
        }

        composable(
            route = Routes.STATS,
            enterTransition = { railEnter() },
            exitTransition = { railExit() },
            popEnterTransition = { railEnter() },
            popExitTransition = { railExit() }
        ) {
            StatsScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }

        // Drawer navigation composables

        composable(
            route = Routes.NUTRITION,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            NutritionJournalScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }

        composable(
            route = Routes.NUTRITION_CATALOGUE,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            FoodCatalogueScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }

        composable(
            route = Routes.NUTRITION_FOOD_DETAIL_PATTERN,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) { backStackEntry ->
            val foodUuid = backStackEntry.arguments?.getString(Routes.ARG_FOOD_UUID) ?: return@composable
            FoodDetailScreen(
                foodUuid = foodUuid,
                navController = navController,
            )
        }

        composable(
            route = Routes.NUTRITION_RECIPES,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            RecipesScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }

        composable(
            route = Routes.NUTRITION_GOALS,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            NutritionGoalsScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }

        composable(
            route = Routes.NUTRITION_STATS,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            NutritionStatsScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }

        // Activity screens

        composable(
            route = Routes.NOTIFICATIONS,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            NotificationsScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }
        composable(
            route = Routes.TASKS,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            TasksScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }
        composable (
            route = Routes.CONVERSATIONS,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            ConversationsScreen(
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }

        composable(
            route = Routes.PROGRAM,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            WeekViewScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }
        composable(
            route = Routes.PLANNED_WORKOUT_PATTERN,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            PlannedWorkoutScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }
        composable(
            route = Routes.EXERCISES,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            ExerciseListScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }
        composable(
            route = Routes.MUSCLES,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            MuscleListScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }
        composable(
            route = Routes.MATERIAL,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            EquipmentListScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }
        composable(Routes.MATERIAL_DETAIL_PATTERN) { backStackEntry ->
            val equipmentName = requireNotNull(
                backStackEntry.arguments?.getString(Routes.ARG_EQUIPMENT_NAME)
            ) { "equipmentName should not be null" }
            EquipmentDetailScreen(
                equipmentName = equipmentName,
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }
        composable(
            route = Routes.CALENDAR,
            enterTransition = { railEnter() },
            exitTransition = { railExit() },
            popEnterTransition = { railEnter() },
            popExitTransition = { railExit() }
        ) {
            CalendarViewScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }
        composable(
            route = Routes.STATS,
            enterTransition = { railEnter() },
            exitTransition = { railExit() },
            popEnterTransition = { railEnter() },
            popExitTransition = { railExit() }
        ) {
            StatsScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }

        // Account & Settings screens

        composable(
            route = Routes.PROFILE,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            ProfileScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }
        composable(
            route = Routes.SETTINGS,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            SettingsScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }
        composable(
            route = Routes.SETTINGS_APPEARANCE,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            com.example.sportapp.feature.settings.ui.AppearanceSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.SETTINGS_LANGUAGE_FORMAT,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            com.example.sportapp.feature.settings.ui.LanguageFormatSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.SETTINGS_NOTIFICATIONS,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            com.example.sportapp.feature.settings.ui.NotificationSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.SETTINGS_STARTUP,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            com.example.sportapp.feature.settings.ui.StartupSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.SETTINGS_HEALTH,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            com.example.sportapp.feature.health.ui.HealthSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.SETTINGS_SERVER_URL,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            // Visible uniquement pour les users admin -- la route reste exposee
            // techniquement (pour eviter du dead-code conditionnel dans NavHost),
            // mais la category Row dans SettingsScreen est gated par `isAdmin`
            // donc un user non-admin ne peut pas y naviguer via l'UI.
            com.example.sportapp.feature.settings.ui.ServerUrlSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.LANGUAGE_DISPLAY,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            LanguageDisplayScreen(
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }
        composable(
            route = Routes.EXPORT_DATAS,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            ExportDatasScreen(
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }
        composable(
            route = Routes.LOGOUT,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            LogoutScreen(
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = Routes.SYNC_SETTINGS,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { fullWidth -> fullWidth } + fadeIn(tween(300)) },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { fullWidth -> -fullWidth } + fadeOut(tween(300)) },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { fullWidth -> -fullWidth } + fadeIn(tween(300)) },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { fullWidth -> fullWidth } + fadeOut(tween(300)) }
        ) {
            SyncSettingsScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } },
                onBack = { navController.popBackStack() },
                onRequireLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Routes.SYNC_TABLE_DETAIL_PATTERN,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { fullWidth -> fullWidth } + fadeIn(tween(300)) },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { fullWidth -> -fullWidth } + fadeOut(tween(300)) },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { fullWidth -> -fullWidth } + fadeIn(tween(300)) },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { fullWidth -> fullWidth } + fadeOut(tween(300)) }
        ) {
            com.example.sportapp.feature.settings.ui.SyncTableDetailScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.ADMIN_USERS,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { fullWidth -> fullWidth } + fadeIn(tween(300)) },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { fullWidth -> -fullWidth } + fadeOut(tween(300)) },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { fullWidth -> -fullWidth } + fadeIn(tween(300)) },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { fullWidth -> fullWidth } + fadeOut(tween(300)) }
        ) {
            com.example.sportapp.feature.admin.ui.AdminUsersScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.ADMIN_UI_SHOWCASE,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { fullWidth -> fullWidth } + fadeIn(tween(300)) },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { fullWidth -> -fullWidth } + fadeOut(tween(300)) },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { fullWidth -> -fullWidth } + fadeIn(tween(300)) },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { fullWidth -> fullWidth } + fadeOut(tween(300)) }
        ) {
            com.example.sportapp.feature.admin.ui.UiShowcaseScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.QUOTES,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { fullWidth -> fullWidth } + fadeIn(tween(300)) },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { fullWidth -> -fullWidth } + fadeOut(tween(300)) },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { fullWidth -> -fullWidth } + fadeIn(tween(300)) },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { fullWidth -> fullWidth } + fadeOut(tween(300)) }
        ) {
            com.example.sportapp.feature.quotes.ui.QuotesScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.HEALTH_DASHBOARD,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { fullWidth -> fullWidth } + fadeIn(tween(300)) },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { fullWidth -> -fullWidth } + fadeOut(tween(300)) },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { fullWidth -> -fullWidth } + fadeIn(tween(300)) },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { fullWidth -> fullWidth } + fadeOut(tween(300)) }
        ) {
            com.example.sportapp.feature.health.ui.HealthDashboardScreen(
                onOpenDrawer = { scope.launch { drawerState.open() } }
            )
        }

        composable(
            route = Routes.ONBOARDING,
            enterTransition = { fadeIn(tween(300)) },
            exitTransition = { fadeOut(tween(300)) },
        ) {
            com.example.sportapp.feature.onboarding.ui.OnboardingScreen(
                onFinish = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                    // Si l'user a fini avec runDemoTour ON, OnboardingViewModel a
                    // déjà markTourActive + insert sample data. On déclenche ici
                    // l'orchestrateur tour (lit le flag + précharge UUID + WELCOME).
                    demoTourViewModel.checkAndStartTour()
                }
            )
        }

        // Others

        // SessionExerciseScreen
        composable(
            route = Routes.SESSION_EXERCISE_PATTERN,
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut() }
        ) {
            SessionExerciseScreen(
                navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }

        // ExerciseScreen
        composable(Routes.EXERCISE_PATTERN) { backStackEntry ->
            ExerciseScreen(navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } })
        }

        // MuscleScreen
        composable(Routes.MUSCLE_PATTERN) { backStackEntry ->
            val muscleUUID = requireNotNull(backStackEntry.arguments?.getString(Routes.ARG_MUSCLE_UUID)) {
                "muscleName should not be null"
            }
            MuscleScreen(muscleUuid = muscleUUID, navController = navController,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } })
        }

        // SessionTab via weekViewScreen
        // NOTE V7.4-E : route legacy "session/..." conservee pour la nav UI
        // alors que l'entite data a ete renommee actual_workout (cf. memory
        // "sessions_*" = code mort cote serveur). Le param sessionUUID
        // contient en realite un actual_workout.uuid. Garder le path UI pour
        // ne pas casser les callsites navigate(...) existants (DrawerContent,
        // CalendarViewScreen x3).
        composable(Routes.SESSION_PATTERN) { backStackEntry ->
            val sessionUUID = backStackEntry.arguments?.getString(Routes.ARG_SESSION_UUID) ?: return@composable
            val sessionTabViewModel: SessionTabViewModel = hiltViewModel()
            LaunchedEffect(sessionUUID) {
                sessionTabViewModel.setSessionUUID(sessionUUID)
            }
            SessionTab(
                navController = navController, sessionUUID = sessionUUID,
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } },
                viewModel = sessionTabViewModel
            )
        }

        // Delavier Method Screen
        composable(Routes.DELAVIER_METHOD) {
            DelavierMethodScreen(
                drawerState = drawerState,
                closeDrawer = { scope.launch { drawerState.close() } },
                onBack = { navController.popBackStack() }
            )
        }

        // B3-2 Stats sous-ecrans (2026-05-07)
        composable(Routes.MUSCLE_STATS_PATTERN) { backStackEntry ->
            val muscleUUID = requireNotNull(backStackEntry.arguments?.getString(Routes.ARG_MUSCLE_UUID)) {
                "muscleUUID should not be null"
            }
            MuscleStatsScreen(muscleUUID = muscleUUID, navController = navController)
        }

        composable(Routes.EXERCISE_STATS_PATTERN) { backStackEntry ->
            val exerciseUUID = requireNotNull(backStackEntry.arguments?.getString(Routes.ARG_EXERCISE_UUID)) {
                "exerciseUUID should not be null"
            }
            ExerciseStatsScreen(exerciseUUID = exerciseUUID, navController = navController)
        }

    }
}
