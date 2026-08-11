package com.example.sportapp.designsystem.drawer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.sportapp.R
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.designsystem.theme.*
import kotlinx.coroutines.launch
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.network.TokenManager
import com.example.sportapp.core.sync.SyncEvents
import com.example.sportapp.feature.health.ui.HEALTH_SECTIONS
import com.example.sportapp.feature.health.ui.HealthNavRequest
import com.example.sportapp.feature.session.viewmodel.SessionTabViewModel
import kotlin.math.min


@Composable
fun DrawerContent(
    navController: NavHostController,
    drawerState: DrawerState,
    bottomPadding: Dp = 0.dp,
    viewModel: DrawerViewModel =  hiltViewModel()
) {
    val hasUnsynced by viewModel.hasUnsyncedData.collectAsState(initial = false)
    val totalPending by viewModel.totalPendingCount.collectAsState()
    val isConnected by SyncEvents.isNetworkAvailable.collectAsState()
    val isWsConnected by viewModel.isWsConnected.collectAsState()
    val lastSyncText by viewModel.lastSyncText.collectAsState()

    val unreadNotifications by viewModel.unreadNotificationsCount.collectAsState()
    val isAdmin by CurrentUserManager.isAdminFlow.collectAsState()

    val todaySession by viewModel.todaySession.collectAsState()
    val sessionTabViewModel: SessionTabViewModel = hiltViewModel()

    // ✅ Se relance dès que la session du jour change (créée/supprimée/etc.)
    LaunchedEffect(todaySession?.uuid) {
        todaySession?.uuid?.let { uuid ->
            sessionTabViewModel.setSessionUUID(uuid)
        }
    }

    // ✅ maintenant ces flows suivent automatiquement la bonne session
    val sets by sessionTabViewModel.allActualWorkoutSets.collectAsState()

    val actualExercises by sessionTabViewModel.allActualWorkoutExercises.collectAsState()
    val allExercises by sessionTabViewModel.allExercises.collectAsState()
    val plannedWorkoutExercises by sessionTabViewModel.plannedWorkoutExercises.collectAsState()

    // ✅ calcule une progression "capée" : bonus sets ne font pas dépasser 100%
    val exerciseStats = remember(actualExercises, sets, allExercises, plannedWorkoutExercises) {
        actualExercises
            .filter { !it.pendingDeletion }
            .mapNotNull { actual ->
                val ex = allExercises.firstOrNull { it.uuid == actual.exerciseUUID } ?: return@mapNotNull null

                val setsDone = sets.count { s ->
                    !s.pendingDeletion &&
                            s.actualWorkoutExerciseUUID == actual.uuid &&
                            s.status.equals("DONE", ignoreCase = true)
                }

                val setsToDo = if (actual.addedManually) {
                    ex.recommendedSets ?: 0
                } else {
                    plannedWorkoutExercises
                        .firstOrNull { it.exerciseUUID == actual.exerciseUUID }
                        ?.sets
                        ?: (ex.recommendedSets ?: 0)
                }

                Pair(setsToDo, setsDone)
            }
    }

    val totalSets = exerciseStats.sumOf { it.first }
    val doneSetsCapped = exerciseStats.sumOf { (toDo, done) -> min(done, toDo) }

    val todaySessionProgress = if (totalSets > 0) doneSetsCapped.toFloat() / totalSets else 0f
    val tasksTodayStats by viewModel.tasksTodayStats.collectAsState()

    val sessionItemTitle = todaySession?.name ?: stringResource(R.string.drawer_item_no_session)
    val sessionUuid = todaySession?.uuid

    LaunchedEffect(Unit) {
        viewModel.checkForUnsyncedData()
    }
    LaunchedEffect(Unit) {
        SyncEvents.onReconnected.collect {
            viewModel.checkForUnsyncedData()
        }
    }
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            viewModel.checkForUnsyncedData()
        }
    }

    val scope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()

    // Accordéon (miroir web) : sections repliées par défaut, sauf celle de la route
    // courante ; état session-only via DrawerSectionStateManager. À chaque
    // navigation, la section de la nouvelle route se déplie en plus (add-only).
    val openSections by DrawerSectionStateManager.openSections.collectAsState()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(currentRoute) {
        DrawerSectionStateManager.ensureOpen(sectionKeyForRoute(currentRoute))
    }

    ModalDrawerSheet(
        drawerContainerColor = appColors.bgScreen,
        // Coin bas-droit droit (le bas de la sheet s'arrête au-dessus de la
        // BottomNavBar, l'arrondi par défaut y serait visible).
        drawerShape = RoundedCornerShape(topEnd = 16.dp),
        // bottomPadding (innerPadding Scaffold) inclut déjà l'inset système :
        // on retire le bottom des insets internes de la sheet (sinon double compte
        // et le footer flotte trop haut).
        windowInsets = DrawerDefaults.windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight()
            .statusBarsPadding()   // ✅ pousse le drawer sous la status bar du haut
            // Le drawer vit DANS le contenu du Scaffold : la BottomNavBar se dessine
            // par-dessus. bottomPadding = innerPadding du Scaffold (hauteur bottom bar
            // + inset système) pour que le footer reste visible au-dessus.
            .padding(bottom = bottomPadding)
    ) {
        // Footer épinglé en bas : les sections défilent au-dessus (LazyColumn
        // weight 1f), la ligne de statut + les icônes de sync restent visibles.
        Column(modifier = Modifier.fillMaxHeight()) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier.weight(1f)
        ) {
            // Header

            // Section « Général » : entrées transverses (Accueil, Notifications,
            // Conversations, Routines, Citations). Conversations est une feature
            // Android-only (agent IA in-app, cf. ANDROID_PARITY.md « parité
            // inverse ») sans équivalent web → rangée ici faute de thème dédié.
            item {
                DrawerSection(
                    title = stringResource(R.string.drawer_section_general),
                    expanded = openSections.contains(DrawerSectionStateManager.KEY_GENERAL),
                    onHeaderClick = {
                        DrawerSectionStateManager.toggle(DrawerSectionStateManager.KEY_GENERAL)
                    },
                    items = listOf(
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_home),
                            iconRes = R.drawable.ic_home,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.HOME)
                                }
                            }
                        ),
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_notifications),
                            iconRes = R.drawable.ic_notifications,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.NOTIFICATIONS)
                                }
                            },
                            trailingContent = {
                                DrawerIconCountIndicator(
                                    iconRes = R.drawable.ic_rounded_mail,
                                    count = unreadNotifications,
                                    tint = appColors.primaryAction,
                                    textColor = appColors.primaryAction
                                )
                            }
                        ),
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_conversations),
                            iconRes = R.drawable.ic_chat,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.CONVERSATIONS)
                                }
                            }
                        ),
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_tasks),
                            iconRes = R.drawable.ic_rounded_list_alt,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.TASKS)
                                }
                            },
                            trailingContent = {
                                if (tasksTodayStats.total > 0) {
                                    TasksTodayStatsBadge(
                                        done = tasksTodayStats.done,
                                        total = tasksTodayStats.total,
                                    )
                                }
                            }
                        ),
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_quotes),
                            iconRes = R.drawable.ic_rounded_book,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.QUOTES)
                                }
                            }
                        )
                    )
                )
                HorizontalDivider(color = appColors.dividerStrong, thickness = 1.5.dp)
            }

            // Section « Sport » : Séance, Calendrier, Programme, Stats, Matériel,
            // Exercices, Muscles, Chrono (miroir du drawer web).
            item {
                DrawerSection(
                    title = stringResource(R.string.drawer_section_sport),
                    expanded = openSections.contains(DrawerSectionStateManager.KEY_SPORT),
                    onHeaderClick = {
                        DrawerSectionStateManager.toggle(DrawerSectionStateManager.KEY_SPORT)
                    },
                    items = listOf(
                        DrawerItem(
                            title = sessionItemTitle,
                            iconRes = R.drawable.ic_rounded_expand_circle_right,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    if (sessionUuid != null) navController.navigate(Routes.session(sessionUuid))
                                    else navController.navigate(Routes.HOME)
                                }
                            },
                            trailingContent = {
                                if (sessionUuid != null && totalSets > 0) {
                                    DrawerMiniProgress(progress = todaySessionProgress) // + ton % si tu veux
                                }
                            }
                        ),
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_calendar),
                            iconRes = R.drawable.ic_calendar_month,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.CALENDAR)
                                }
                            }
                        ),
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_program),
                            iconRes = R.drawable.ic_rounded_list_alt,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.PROGRAM)
                                }
                            }
                        ),
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_statistics),
                            iconRes = R.drawable.ic_equalizer,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.STATS)
                                }
                            }
                        ),
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_material),
                            iconRes = R.drawable.ic_exercise,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.MATERIAL)
                                }
                            }
                        ),
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_exercises),
                            iconRes = R.drawable.ic_exercise,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.EXERCISES)
                                }
                            }
                        ),
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_muscles),
                            iconRes = R.drawable.ic_rounded_neurology,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.MUSCLES)
                                }
                            }
                        ),
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_chrono),
                            iconRes = R.drawable.ic_timer,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.CHRONO)
                                }
                            }
                        )
                    )
                )
                HorizontalDivider(color = appColors.dividerStrong, thickness = 1.5.dp)
            }

            // A7 — Section Nutrition dédiée (miroir du drawer web) : Journal /
            // Objectifs / Stats / Catalogue / Recettes. Coexiste avec la bascule
            // de mode de la barre basse (accès direct aux 5 écrans nutrition).
            item {
                DrawerSection(
                    title = stringResource(R.string.drawer_section_nutrition),
                    expanded = openSections.contains(DrawerSectionStateManager.KEY_NUTRITION),
                    onHeaderClick = {
                        DrawerSectionStateManager.toggle(DrawerSectionStateManager.KEY_NUTRITION)
                    },
                    items = listOf(
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_nutrition),
                            iconRes = R.drawable.ic_rounded_local_fire,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.NUTRITION)
                                }
                            }
                        ),
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_nutrition_goals),
                            iconRes = R.drawable.ic_rounded_flag,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.NUTRITION_GOALS)
                                }
                            }
                        ),
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_nutrition_stats),
                            iconRes = R.drawable.ic_equalizer,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.NUTRITION_STATS)
                                }
                            }
                        ),
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_food_catalogue),
                            iconRes = R.drawable.ic_rounded_list_alt,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.NUTRITION_CATALOGUE)
                                }
                            }
                        ),
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_recipes),
                            iconRes = R.drawable.ic_rounded_book,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.NUTRITION_RECIPES)
                                }
                            }
                        )
                    )
                )
                HorizontalDivider(color = appColors.dividerStrong, thickness = 1.5.dp)
            }

            // Section « Santé » (miroir du drawer web) : UN ITEM PAR SECTION du hub
            // (source unique HEALTH_SECTIONS) — le clic ouvre le hub ET anime son pager
            // sur la section (HealthNavRequest, pendant du HealthNavService web).
            item {
                DrawerSection(
                    title = stringResource(R.string.drawer_section_health),
                    expanded = openSections.contains(DrawerSectionStateManager.KEY_HEALTH),
                    onHeaderClick = {
                        DrawerSectionStateManager.toggle(DrawerSectionStateManager.KEY_HEALTH)
                    },
                    items = HEALTH_SECTIONS.mapIndexed { index, section ->
                        DrawerItem(
                            title = stringResource(section.titleRes),
                            iconRes = section.iconRes,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    HealthNavRequest.request(index)
                                    navController.navigate(Routes.HEALTH_DASHBOARD) {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        )
                    }
                )
                HorizontalDivider(color = appColors.dividerStrong, thickness = 1.5.dp)
            }

            item {
                DrawerSection(
                    title = stringResource(R.string.drawer_section_account_settings),
                    expanded = openSections.contains(DrawerSectionStateManager.KEY_ACCOUNT_SETTINGS),
                    onHeaderClick = {
                        DrawerSectionStateManager.toggle(DrawerSectionStateManager.KEY_ACCOUNT_SETTINGS)
                    },
                    items = listOf(
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_profile),
                            iconRes = R.drawable.ic_account_circle,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.PROFILE)
                                }
                            }
                        ),
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_settings),
                            iconRes = R.drawable.ic_settings,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.SETTINGS)
                                }
                            }
                        ),
                        // Note 2026-05-12 : item "Language & Display" supprime du
                        // drawer. La locale se change desormais dans Settings
                        // (Card Language en haut), pas de page dediee.
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_export_datas),
                            iconRes = R.drawable.ic_file_export,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.EXPORT_DATAS)
                                }
                            }
                        ),
                        DrawerItem(
                            title = stringResource(R.string.drawer_item_logout),
                            iconRes = R.drawable.ic_logout,
                            onClick = {
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    navController.navigate(Routes.LOGOUT)
                                }
                            }
                        )
                    )
                )
            }

            // Admin section -- visible UNIQUEMENT si currentUser.isAdmin == true.
            // Cohérent avec require_admin guard côté serveur (403 sinon).
            if (isAdmin) {
                item {
                    HorizontalDivider(color = appColors.dividerStrong, thickness = 1.5.dp)
                    DrawerSection(
                        title = stringResource(R.string.drawer_section_admin),
                        expanded = openSections.contains(DrawerSectionStateManager.KEY_ADMIN),
                        onHeaderClick = {
                            DrawerSectionStateManager.toggle(DrawerSectionStateManager.KEY_ADMIN)
                        },
                        items = listOf(
                            DrawerItem(
                                title = stringResource(R.string.drawer_item_manage_users),
                                iconRes = R.drawable.ic_account_circle,
                                onClick = {
                                    scope.launch {
                                        drawerState.snapTo(DrawerValue.Closed)
                                        navController.navigate(Routes.ADMIN_USERS)
                                    }
                                }
                            ),
                            DrawerItem(
                                title = stringResource(R.string.drawer_item_ui_showcase),
                                iconRes = R.drawable.ic_exercise,
                                onClick = {
                                    scope.launch {
                                        drawerState.snapTo(DrawerValue.Closed)
                                        navController.navigate(Routes.ADMIN_UI_SHOWCASE)
                                    }
                                }
                            ),
                            DrawerItem(
                                title = stringResource(R.string.drawer_item_sync_settings),
                                iconRes = R.drawable.ic_home,
                                onClick = {
                                    scope.launch {
                                        drawerState.snapTo(DrawerValue.Closed)
                                        navController.navigate(Routes.SYNC_SETTINGS)
                                    }
                                }
                            )
                        )
                    )
                }
            }

        }

            HorizontalDivider(color = appColors.dividerStrong, thickness = 1.5.dp)

            DrawerFooter(
                lastSyncText = lastSyncText,
                isConnected = isConnected,
                hasUnsynced = hasUnsynced,
                totalPending = totalPending,
                isWsConnected = isWsConnected,
                onSyncClick = { viewModel.syncAllAndRefresh() },
                onWsRestartClick = {
                    val token = TokenManager.token
                    if (token != null && !isWsConnected) {
                        viewModel.restartWebSocket(token)
                    }
                }
            )
        }
    }
}

/**
 * D4 (2026-05-12) : pill "done/total" affichee a droite de l'entree Tasks
 * du drawer. Style cale sur EntityStatsBadge de SyncSettingsScreen
 * (RoundedCornerShape 8dp, fond color alpha 0.15, texte 12sp Medium).
 * Couleur conditionnelle :
 * - total == 0    : lightGrayBlue (pas de tasks aujourd'hui ; en pratique
 *                    la pill est cachee par l'appelant via if total > 0)
 * - done == total : mediumGreen (tout fait)
 * - sinon         : orangeMedium (en cours)
 *
 * Padding rectangle (12dp horizontal / 2dp vertical) pour rester pill plate
 * et ne pas agrandir la hauteur de la row du drawer.
 */
@Composable
private fun TasksTodayStatsBadge(done: Int, total: Int) {
    val color = when {
        total == 0 -> lightGrayBlue
        done == total -> mediumGreen
        else -> orangeMedium
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = "$done/$total",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
