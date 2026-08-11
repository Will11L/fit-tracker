package com.example.sportapp.feature.home.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.example.sportapp.designsystem.common_components.DualTabMenu
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.*
import com.example.sportapp.feature.home.viewmodel.HomeViewModel
import com.example.sportapp.feature.session.viewmodel.SessionTabViewModel
import com.example.sportapp.feature.session.ui.SessionTab
import com.example.sportapp.feature.session.ui.NoSessionFallback
import com.example.sportapp.feature.goals.ui.GoalsTabContent
import com.example.sportapp.feature.planning.ui.WeekViewScreen
import com.example.sportapp.feature.calendar.ui.CalendarViewScreen

@Composable
fun HomeScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    // Indices stables (0 SESSION, 1 GOALS, 2 PROGRAM ; sub 0 WEEK, 1 CALENDAR).
    // Le routing interne se fait par index pour rester stable a travers les
    // locales. Les labels sont localises via stringResource.
    val topTabs = listOf(
        stringResource(R.string.home_tab_session),
        stringResource(R.string.home_tab_goals),
        stringResource(R.string.home_tab_program),
    )
    val programLabel = topTabs[2]
    val subTabs = mapOf(
        programLabel to listOf(
            stringResource(R.string.home_tab_week),
            stringResource(R.string.home_tab_calendar),
        )
    )

    var selectedTopTab by remember { mutableStateOf(0) }
    var selectedSubTab by remember { mutableStateOf(0) }

    val sessionUUID by viewModel.sessionUUID.collectAsState()
    val loading = viewModel.loading.value
    val initialSessionLoaded by viewModel.initialSessionLoaded.collectAsState()

    val plannedToday by viewModel.plannedToday.collectAsState()

    val sessionTabViewModel: SessionTabViewModel = hiltViewModel()

    LaunchedEffect(sessionUUID) {
        if (sessionUUID != null) {
            sessionUUID?.let { sessionTabViewModel.setSessionUUID(it) }
        }
    }

    // Retour système : ferme le drawer s'il est ouvert ; sinon le handler global de
    // MainActivity prend la main (dialog unique « Quitter l'application ? »).
    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    Column(modifier = Modifier.fillMaxSize()) {
        DualTabMenu(
            topTabs = topTabs,
            subTabsMap = subTabs,
            selectedTopIndex = selectedTopTab,
            selectedSubIndex = if (selectedTopTab == 2) selectedSubTab else null,
            onTopTabSelected = { selectedTopTab = it },
            onSubTabSelected = { selectedSubTab = it }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTopTab) {
                0 -> {
                    when {
                        loading || !initialSessionLoaded -> {
                            // Loading tant que Flow Room n'a pas émis -- évite
                            // le flash "Currently sleeping" -> SessionTab au 1er
                            // render quand le user a en fait une session du jour.
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        sessionUUID != null -> {
                            SessionTab(
                                navController = navController,
                                viewModel = sessionTabViewModel,
                                sessionUUID = sessionUUID!!,
                                drawerState = drawerState,
                                closeDrawer = closeDrawer
                            )
                        }
                        else -> {
                            NoSessionFallback(
                                plannedWorkout = plannedToday,
                                onStartPlanned = { pw ->
                                    viewModel.startActualWorkoutFromPlanned(pw)
                                },
                                onCreateActualWorkout = { name ->
                                    viewModel.startNewActualWorkout(name)
                                },
                                onNavigateToProgram = {
                                    selectedTopTab = 2
                                    selectedSubTab = 0
                                }
                            )
                        }
                    }
                }
                1 -> GoalsTabContent(
                    navController = navController,
                    drawerState = drawerState,
                    closeDrawer = closeDrawer
                )
                2 -> when (selectedSubTab) {
                    0 -> WeekViewScreen(
                        navController = navController,
                        drawerState = drawerState,
                        closeDrawer = closeDrawer
                    )
                    1 -> CalendarViewScreen(
                        navController = navController,
                        drawerState = drawerState,
                        closeDrawer = closeDrawer
                    )
                }
            }
        }
    }
}
