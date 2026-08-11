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
import com.example.sportapp.feature.onboarding.data.ThemeMode
import com.example.sportapp.feature.settings.SettingsViewModel
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val userPrefs by viewModel.userPreferences.collectAsState(initial = OnboardingPreferences())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
    ) {
        SettingsSubScreenHeader(
            title = stringResource(R.string.settings_category_appearance),
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSectionCard(title = stringResource(R.string.onboarding_preferences_card_theme)) {
                Text(
                    text = stringResource(R.string.settings_theme_desc),
                    color = appColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
                SettingsRadioOptions(
                    options = listOf(
                        ThemeMode.LIGHT to stringResource(R.string.common_theme_light),
                        ThemeMode.DARK to stringResource(R.string.common_theme_dark),
                        ThemeMode.SYSTEM to stringResource(R.string.common_theme_system),
                    ),
                    selected = userPrefs.themeMode,
                    onSelected = viewModel::setThemeMode,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
