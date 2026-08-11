package com.example.sportapp.feature.routines.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.example.sportapp.feature.routines.ui.components.tasksScreen.TasksTabMenu
import com.example.sportapp.designsystem.theme.appColors

/**
 * Phase 1 (2026-05-12) : ecran unifie "Tasks". Header en haut (44dp, style
 * RoutineHeader) qui affiche les 2 onglets cote a cote (Daily | Agenda) et
 * sert aussi de toggle (tap sur l'autre = switch). Hauteur fixe pour
 * preserver le meme visuel entre les 2 vues.
 *
 * Les sub-screens (RoutineTasksScreen / TasksCalendarScreen) ont leur
 * header propre retire ; le contenu commence directement sous TasksTabMenu.
 *
 * rememberSaveable preserve la selection a travers le navigate-back.
 */
@Composable
fun TasksScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen),
    ) {
        TasksTabMenu(
            selectedTab = tab,
            onChange = { tab = it },
        )

        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                0 -> RoutineTasksScreen(
                    navController = navController,
                    drawerState = drawerState,
                    closeDrawer = closeDrawer,
                )
                else -> TasksCalendarScreen(
                    navController = navController,
                    drawerState = drawerState,
                    closeDrawer = closeDrawer,
                )
            }
        }
    }
}
