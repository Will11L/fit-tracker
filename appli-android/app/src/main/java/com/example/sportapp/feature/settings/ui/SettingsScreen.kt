package com.example.sportapp.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.sportapp.R
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.feature.settings.SettingsViewModel
import com.example.sportapp.feature.settings.ui.SettingsCategoryRow
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import com.example.sportapp.designsystem.theme.appColors
import androidx.navigation.NavHostController

/**
 * Ecran racine Settings : liste des categories. Chaque tap navigue vers
 * un sous-ecran dedie (drill-down). Pattern iOS/Android natif : scale a
 * l'infini quand on ajoute des categories.
 */
@Composable
fun SettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val isAdmin by CurrentUserManager.isAdminFlow.collectAsState()

    BackHandler(enabled = drawerState.isOpen) {
        closeDrawer()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
    ) {
        // Header canonique ScreenTitleBar (même style que Nutrition/Santé, demande user 2026-07-14).
        ScreenTitleBar(title = stringResource(R.string.settings_title))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            // Liste des categories dans une seule card -- chaque row navigue
            // vers son sous-ecran via popUpTo conserve.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(appColors.bgRecessed)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                SettingsCategoryRow(
                    title = stringResource(R.string.settings_category_appearance),
                    description = stringResource(R.string.settings_category_appearance_desc),
                    onClick = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                )
                HorizontalDivider(thickness = 1.dp, color = appColors.divider)
                SettingsCategoryRow(
                    title = stringResource(R.string.settings_category_startup),
                    description = stringResource(R.string.settings_category_startup_desc),
                    onClick = { navController.navigate(Routes.SETTINGS_STARTUP) },
                )
                HorizontalDivider(thickness = 1.dp, color = appColors.divider)
                SettingsCategoryRow(
                    title = stringResource(R.string.settings_category_language_format),
                    description = stringResource(R.string.settings_category_language_format_desc),
                    onClick = { navController.navigate(Routes.SETTINGS_LANGUAGE_FORMAT) },
                )
                HorizontalDivider(thickness = 1.dp, color = appColors.divider)
                SettingsCategoryRow(
                    title = stringResource(R.string.drawer_item_notifications),
                    description = stringResource(R.string.settings_category_notifications_desc),
                    onClick = { navController.navigate(Routes.SETTINGS_NOTIFICATIONS) },
                )
                HorizontalDivider(thickness = 1.dp, color = appColors.divider)
                SettingsCategoryRow(
                    title = stringResource(R.string.settings_category_health),
                    description = stringResource(R.string.settings_category_health_desc),
                    onClick = { navController.navigate(Routes.SETTINGS_HEALTH) },
                )
                HorizontalDivider(thickness = 1.dp, color = appColors.divider)
                SettingsCategoryRow(
                    title = stringResource(R.string.settings_category_rerun_onboarding),
                    description = stringResource(R.string.settings_category_rerun_onboarding_desc),
                    onClick = {
                        viewModel.restartOnboarding {
                            navController.navigate(Routes.ONBOARDING) {
                                popUpTo(Routes.SETTINGS) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                )
                // Section Server URL : visible si user.is_admin (cf. tache Notion
                // 36b1776c "UI switcher d'URL serveur sans rebuild"). Coherent
                // avec le pattern admin du drawer + ecran "Manage users".
                if (isAdmin) {
                    HorizontalDivider(thickness = 1.dp, color = appColors.divider)
                    SettingsCategoryRow(
                        title = stringResource(R.string.settings_category_server_url),
                        description = stringResource(R.string.settings_category_server_url_desc),
                        onClick = { navController.navigate(Routes.SETTINGS_SERVER_URL) },
                    )
                }
            }
        }
    }
}
