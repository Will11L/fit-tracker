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
import com.example.sportapp.feature.onboarding.data.AppLocale
import com.example.sportapp.feature.onboarding.data.LengthUnit
import com.example.sportapp.feature.onboarding.data.OnboardingPreferences
import com.example.sportapp.feature.onboarding.data.WeightUnit
import com.example.sportapp.feature.settings.SettingsViewModel
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun LanguageFormatSettingsScreen(
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
            title = stringResource(R.string.settings_category_language_format),
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Language card -- recreate Activity au tap (DataStore + AppCompat.setApplicationLocales)
            SettingsSectionCard(title = stringResource(R.string.settings_section_language)) {
                SettingsRadioOptions(
                    options = listOf(
                        AppLocale.SYSTEM to stringResource(R.string.common_theme_system),
                        AppLocale.EN to "English",
                        AppLocale.FR to "Français",
                    ),
                    selected = userPrefs.appLocale,
                    onSelected = viewModel::setAppLocale,
                )
            }

            // Weight unit card
            SettingsSectionCard(title = stringResource(R.string.onboarding_preferences_card_weight_unit)) {
                Text(
                    text = stringResource(R.string.settings_weight_unit_desc),
                    color = appColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
                SettingsRadioOptions(
                    options = listOf(
                        WeightUnit.KG to stringResource(R.string.onboarding_preferences_weight_kg),
                        WeightUnit.LBS to stringResource(R.string.onboarding_preferences_weight_lbs),
                    ),
                    selected = userPrefs.weightUnit,
                    onSelected = viewModel::setWeightUnit,
                )
            }

            // Length unit card
            SettingsSectionCard(title = stringResource(R.string.onboarding_preferences_card_length_unit)) {
                Text(
                    text = stringResource(R.string.settings_length_unit_desc),
                    color = appColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
                SettingsRadioOptions(
                    options = listOf(
                        LengthUnit.CM to stringResource(R.string.onboarding_preferences_length_cm),
                        LengthUnit.INCHES to stringResource(R.string.onboarding_preferences_length_in),
                    ),
                    selected = userPrefs.lengthUnit,
                    onSelected = viewModel::setLengthUnit,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
