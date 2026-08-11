package com.example.sportapp.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.sportapp.R
import com.example.sportapp.feature.onboarding.data.OnboardingPreferences
import com.example.sportapp.feature.onboarding.data.StartScreen
import com.example.sportapp.feature.settings.SettingsViewModel
import com.example.sportapp.designsystem.common_components.SingleSelectDropdown
import com.example.sportapp.designsystem.theme.appColors

/**
 * Settings : choix de la page d'accueil au lancement de l'app.
 * Persistance : OnboardingPreferences.startScreen via OnboardingRepository.
 * Lecture au launch : SplashScreenViewModel (post-onboarding-check).
 */
@Composable
fun StartupSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val userPrefs by viewModel.userPreferences.collectAsState(initial = OnboardingPreferences())

    val labels: Map<StartScreen, String> = mapOf(
        StartScreen.HOME to stringResource(R.string.settings_start_screen_home),
        StartScreen.TASKS to stringResource(R.string.settings_start_screen_tasks),
        StartScreen.CALENDAR to stringResource(R.string.settings_start_screen_calendar),
        StartScreen.STATS to stringResource(R.string.settings_start_screen_stats),
        StartScreen.CHRONO to stringResource(R.string.settings_start_screen_chrono),
        StartScreen.PROGRAM to stringResource(R.string.settings_start_screen_program),
        StartScreen.NOTIFICATIONS to stringResource(R.string.settings_start_screen_notifications),
        StartScreen.CONVERSATIONS to stringResource(R.string.settings_start_screen_conversations),
    )
    val labelToValue: Map<String, StartScreen> = labels.entries.associate { (k, v) -> v to k }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
    ) {
        SettingsSubScreenHeader(
            title = stringResource(R.string.settings_category_startup),
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSectionCard(title = stringResource(R.string.settings_start_screen_title)) {
                Text(
                    text = stringResource(R.string.settings_start_screen_description),
                    color = appColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
                SingleSelectDropdown(
                    label = stringResource(R.string.settings_start_screen_title),
                    selected = labels.getValue(userPrefs.startScreen),
                    options = StartScreen.entries.map { labels.getValue(it) },
                    onSelect = { picked ->
                        labelToValue[picked]?.let(viewModel::setStartScreen)
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
