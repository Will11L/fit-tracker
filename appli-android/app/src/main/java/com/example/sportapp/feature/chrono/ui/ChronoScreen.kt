package com.example.sportapp.feature.chrono.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.sportapp.R
import com.example.sportapp.feature.chrono.data.ChronoTab
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import com.example.sportapp.feature.demo_tour.ui.components.demoHighlight
import com.example.sportapp.feature.chrono.ui.components.StopwatchPage
import com.example.sportapp.feature.chrono.ui.components.TimerPage
import com.example.sportapp.designsystem.common_components.DualTabMenu
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun ChronoScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: ChronoScreenViewModel
) {
    BackHandler(enabled = drawerState.isOpen) {
        closeDrawer()
    }

    // Index-based switching (0 = Stopwatch, 1 = Timer) pour rester stable a travers
    // les locales -- les labels sont localises.
    val tabs = listOf(
        stringResource(R.string.chrono_tab_stopwatch),
        stringResource(R.string.chrono_tab_timer),
    )
    val settings by viewModel.settings.collectAsState()
    var selectedTab by remember(settings.lastActiveTab) {
        mutableIntStateOf(if (settings.lastActiveTab == ChronoTab.TIMER) 1 else 0)
    }

    LaunchedEffect(selectedTab) {
        viewModel.setActiveTab(if (selectedTab == 1) ChronoTab.TIMER else ChronoTab.STOPWATCH)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
    ) {
        ScreenTitleBar(
            title = stringResource(R.string.chrono_screen_title),
            onClick = { /* later: options sheet */ }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 18.dp)
        ) {
            when (selectedTab) {
                0 -> StopwatchPage(viewModel = viewModel)
                1 -> TimerPage(viewModel = viewModel)
            }
        }

        Box(modifier = Modifier.demoHighlight("chrono.tabs", expand = 0.dp)) {
            DualTabMenu(
                topTabs = tabs,
                subTabsMap = emptyMap(),
                selectedTopIndex = selectedTab,
                selectedSubIndex = null,
                onTopTabSelected = { selectedTab = it },
                onSubTabSelected = { /* none */ }
            )
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.5.dp,
            color = appColors.dividerStrong
        )
    }
}
