package com.example.sportapp.designsystem.bottomNavigationBar

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.sportapp.R
import com.example.sportapp.app.navigation.MODE_TOGGLE_ROUTE
import com.example.sportapp.app.navigation.NavMode
import com.example.sportapp.app.navigation.NavModeManager
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.app.navigation.homeRouteForMode
import com.example.sportapp.app.navigation.nextMode
import com.example.sportapp.core.sync.SyncEvents
import com.example.sportapp.designsystem.drawer.DrawerSectionStateManager
import com.example.sportapp.designsystem.drawer.DrawerViewModel
import com.example.sportapp.designsystem.drawer.sectionKeyForMode
import com.example.sportapp.designsystem.icons.AppIcons
import com.example.sportapp.designsystem.theme.*


data class BottomNavItem(
    val label: String,
    val route: String,
    @DrawableRes val iconRes: Int
)

@Composable
fun BottomNavBar(
    navController: NavController,
    drawerViewModel: DrawerViewModel = hiltViewModel(),
) {
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    val context = LocalContext.current
    val navMode by NavModeManager.mode.collectAsState()

    // A7 — Le mode suit la page : on recale NavModeManager sur la route courante
    // (et on persiste). La barre n'est rendue que sur les écrans de contenu (cf.
    // MainActivity, branche !isAuthScreen), jamais sur login/splash → le mode
    // persisté n'est pas écrasé au boot avant que le splash ne choisisse sa cible.
    LaunchedEffect(currentRoute) {
        NavModeManager.updateFromRoute(context, currentRoute)
    }

    // Iter "offline indicator" + "unread notifications badge" :
    // - badge wifi-off (orangeMedium) en bas-droite si offline
    // - badge mail+count (appColors.primaryAction) en haut-droite si unread > 0
    // Portés par le 1er slot (la bascule de mode, ex-emplacement du burger).
    val isConnected by SyncEvents.isNetworkAvailable.collectAsState()
    val unreadNotifications by drawerViewModel.unreadNotificationsCount.collectAsState()
    val totalPending by drawerViewModel.totalPendingCount.collectAsState()
    val isWsConnected by drawerViewModel.isWsConnected.collectAsState()

    // A7 — Accent couleur par domaine (tokens app, jamais de M3 brut) : bleu en
    // Sport, dark orange (#9D5300 = darkOrange) en Nutrition, vert (mediumGreen,
    // identité santé) en Santé. Pose le fond (pill) de l'item actif. La teinte de
    // l'icône de bascule reste plus claire (lisible sur la barre sombre) :
    // primaryAction bleu / orangeMedium / lightGreen (miroir web accentColorForMode).
    val accentFill = when (navMode) {
        NavMode.NUTRITION -> darkOrange
        NavMode.HEALTH -> mediumGreen
        else -> appColors.selectedFill
    }
    val toggleTint = when (navMode) {
        NavMode.NUTRITION -> orangeMedium
        NavMode.HEALTH -> lightGreen
        else -> appColors.primaryAction
    }

    // 1er slot = bascule de mode (remplace le burger ; drawer ouvrable au swipe).
    // Son icône reflète le mode COURANT (haltère en Sport, assiette en Nutrition,
    // cœur ecg en Santé) ; tap = cycle vers le mode suivant (Sport → Nutrition →
    // Santé → Sport, miroir web). Les raccourcis du milieu dépendent du mode.
    val toggleItem = BottomNavItem(
        label = stringResource(
            when (navMode) {
                NavMode.SPORT -> R.string.nav_switch_to_nutrition
                NavMode.NUTRITION -> R.string.nav_switch_to_health
                NavMode.HEALTH -> R.string.nav_switch_to_sport
            }
        ),
        route = MODE_TOGGLE_ROUTE,
        iconRes = when (navMode) {
            NavMode.SPORT -> R.drawable.ic_exercise
            NavMode.NUTRITION -> R.drawable.ic_rounded_restaurant
            NavMode.HEALTH -> R.drawable.ic_rounded_ecg_heart
        },
    )
    val items = when (navMode) {
        NavMode.NUTRITION -> listOf(
            toggleItem,
            BottomNavItem(stringResource(R.string.nav_nutrition_journal), Routes.NUTRITION, R.drawable.ic_rounded_local_fire),
            BottomNavItem(stringResource(R.string.nav_nutrition_goals), Routes.NUTRITION_GOALS, R.drawable.ic_rounded_flag),
            BottomNavItem(stringResource(R.string.nav_nutrition_catalogue), Routes.NUTRITION_CATALOGUE, R.drawable.ic_rounded_list_alt),
            BottomNavItem(stringResource(R.string.nav_nutrition_stats), Routes.NUTRITION_STATS, R.drawable.ic_equalizer),
        )
        // Santé = un seul hub (onglets internes) : raccourci hub + écran Données
        // santé (connexion Health Connect), même domaine que la section drawer.
        NavMode.HEALTH -> listOf(
            toggleItem,
            BottomNavItem(stringResource(R.string.nav_health), Routes.HEALTH_DASHBOARD, R.drawable.ic_rounded_ecg_heart),
            BottomNavItem(stringResource(R.string.settings_category_health), Routes.SETTINGS_HEALTH, R.drawable.ic_settings),
        )
        else -> listOf(
            toggleItem,
            BottomNavItem(stringResource(R.string.nav_calendar), Routes.CALENDAR, R.drawable.ic_calendar_month),
            BottomNavItem(stringResource(R.string.nav_home), Routes.HOME, R.drawable.ic_home),
            BottomNavItem(stringResource(R.string.nav_chrono), Routes.CHRONO, R.drawable.ic_timer),
            BottomNavItem(stringResource(R.string.nav_stats), Routes.STATS, R.drawable.ic_rounded_monitoring),
        )
    }

    NavigationBar(
        containerColor = appColors.bgBottomNav,
        modifier = Modifier
            .shadow(
                elevation = 24.dp,
                clip = false
            )
            .height(100.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        items.forEach { item ->
            val isToggle = item.route == MODE_TOGGLE_ROUTE
            val isSelected = currentRoute == item.route
            val iconSize = if (isSelected) 38.dp else 28.dp
            val backgroundIconColor = if (isSelected) accentFill else Color.Transparent
            val iconColor = when {
                isToggle -> toggleTint
                isSelected -> appColors.textOnSelected
                else -> appColors.textTertiary
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (isToggle) {
                        // Bascule de mode : cycle vers le mode suivant + navigue
                        // vers sa page d'accueil (le route-following confirmera).
                        val target = nextMode(navMode)
                        NavModeManager.setMode(context, target)
                        // Bascule de mode = SEUL cas de reset de l'accordéon du
                        // drawer : ouvre la section du mode, referme les autres.
                        // SEULE cette section reste ouverte : on neutralise le
                        // ensureOpen de la navigation induite (sinon Sport → Accueil
                        // ∈ « Général » rouvrirait Général par-dessus Sport).
                        DrawerSectionStateManager.resetTo(sectionKeyForMode(target))
                        val targetRoute = homeRouteForMode(target)
                        if (currentRoute != targetRoute) {
                            DrawerSectionStateManager.suppressNextRouteOpen()
                            navController.navigate(targetRoute) {
                                popUpTo(Routes.HOME)
                                launchSingleTop = true
                            }
                        }
                    } else if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(Routes.HOME)
                            launchSingleTop = true
                        }
                    }
                },
                icon = {
                    // iter 13 : Box.size fixe = iconSize. Sans cette contrainte,
                    // les badges en overflow (offset hors-bornes) faisaient
                    // grandir le Box et repoussaient les voisins. Avec size fixe,
                    // les overflow sont rendus VISUELLEMENT mais ne contribuent
                    // pas a la taille mesuree -> badges totalement independants.
                    Box(
                        modifier = Modifier.size(iconSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(id = item.iconRes),
                            contentDescription = item.label,
                            colorFilter = ColorFilter.tint(iconColor),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(iconSize)
                        )
                        // Badge wifi-off sur le slot bascule uniquement, en bas-droite.
                        // Icone orange grande sur fond transparent (user feedback iter 2).
                        // Iter 3 : decale plus dans le coin pour ne pas toucher
                        // l'icone du toggle.
                        if (isToggle && !isConnected) {
                            Image(
                                painter = painterResource(id = AppIcons.NETWORK_OFF),
                                contentDescription = "Offline",
                                colorFilter = ColorFilter.tint(orangeMedium),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .wrapContentSize(align = Alignment.BottomEnd, unbounded = true)
                                    .offset(x = 21.dp, y = 6.dp)
                                    .requiredSize(16.dp)
                            )
                        }
                        // Badge unread notifications en HAUT-droite (iter 3+4) :
                        // bleu sur fond transparent (user feedback iter 4) + decale
                        // plus dans le coin (offset y plus negatif).
                        if (isToggle && unreadNotifications > 0) {
                            // iter 16 : juste l'icone, plus de chiffre (user feedback :
                            // simplification, evite tous les problemes de layout chiffre).
                            Image(
                                painter = painterResource(id = AppIcons.NOTIFICATIONS_UNREAD),
                                contentDescription = "Unread notifications",
                                colorFilter = ColorFilter.tint(appColors.primaryAction),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .wrapContentSize(align = Alignment.TopEnd, unbounded = true)
                                    .offset(x = 21.dp, y = (-8).dp)
                                    .requiredSize(14.dp)
                            )
                        }
                        // Badge sync state en HAUT-droite (iter 12) :
                        // TOUJOURS visible. Si pending > 0 : orange + cloud_off
                        // + count tronque "999+". Si OK : bleu + cloud_done
                        // (sans chiffre). User feedback : "inverse logique +
                        // afficher etat sync meme quand 0".
                        if (isToggle) {
                            val syncIsPending = totalPending > 0
                            val syncColor = if (syncIsPending) yellowMedium else appColors.primaryAction
                            val syncIcon = if (syncIsPending) AppIcons.SYNC_PENDING else AppIcons.SYNC_DONE
                            // iter 16 : juste l'icone, plus de chiffre.
                            Image(
                                painter = painterResource(id = syncIcon),
                                contentDescription = if (syncIsPending) "Sync pending" else "All synced",
                                colorFilter = ColorFilter.tint(syncColor),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                                    .offset(x = (-21).dp, y = (-8).dp)
                                    .requiredSize(16.dp)  // iter 17 : 14->16 = aligne taille avec WS, centre visuel sync au-dessus du WS
                            )
                        }
                        // WS connection status en BAS-droite (iter 12) :
                        // icone router (meme que drawer pour coherence cross-screen,
                        // via AppIcons centralise). Vert si connecte, orange si non.
                        if (isToggle) {
                            Image(
                                painter = painterResource(
                                    id = if (isWsConnected) AppIcons.WS_ON else AppIcons.WS_OFF
                                ),
                                contentDescription = if (isWsConnected) "WS connected" else "WS disconnected",
                                colorFilter = ColorFilter.tint(
                                    if (isWsConnected) mediumGreen else orangeMedium
                                ),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .wrapContentSize(align = Alignment.BottomStart, unbounded = true)
                                    .offset(x = (-21).dp, y = 6.dp)
                                    .requiredSize(16.dp)
                            )
                        }
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = Color.Transparent,
                    selectedIconColor = Color.Transparent
                ),
                modifier = Modifier
                    .background(color = backgroundIconColor, shape = MaterialTheme.shapes.small))
        }
    }
}
