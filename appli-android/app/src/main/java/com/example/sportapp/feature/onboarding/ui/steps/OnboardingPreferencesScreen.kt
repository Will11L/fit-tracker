package com.example.sportapp.feature.onboarding.ui.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.feature.onboarding.data.AppLocale
import com.example.sportapp.feature.onboarding.data.LengthUnit
import com.example.sportapp.feature.onboarding.data.ThemeMode
import com.example.sportapp.feature.onboarding.data.WeekStart
import com.example.sportapp.feature.onboarding.data.WeightUnit
import com.example.sportapp.feature.onboarding.ui.OnboardingViewModel
import com.example.sportapp.feature.onboarding.ui.components.RoutineTimePickerDialog
import com.example.sportapp.designsystem.common_components.CustomRadioButton
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun OnboardingPreferencesScreen(viewModel: OnboardingViewModel) {
    val prefs by viewModel.preferencesDraft.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Page main title centré hors card
        Text(
            text = stringResource(R.string.onboarding_preferences_title),
            style = MaterialTheme.typography.headlineSmall,
            color = appColors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // Card 1 : Language (i18n Session A 2026-05-11) -- en haut car flippe TOUT
        // l'écran live au tap, effet WOW pour le user. "English"/"Français" en
        // langue native (convention pickers, pas traduit).
        SectionCard(title = stringResource(R.string.onboarding_preferences_card_language)) {
            RadioOptions(
                options = listOf(
                    AppLocale.SYSTEM to stringResource(R.string.common_theme_system),
                    AppLocale.EN to "English",
                    AppLocale.FR to "Français",
                ),
                selected = prefs.appLocale,
                onSelected = { viewModel.setAppLocale(it) },
            )
        }

        // Card 2 : Week start
        SectionCard(title = stringResource(R.string.onboarding_preferences_card_week_start)) {
            RadioOptions(
                options = listOf(
                    WeekStart.MONDAY to stringResource(R.string.onboarding_preferences_week_monday),
                    WeekStart.SUNDAY to stringResource(R.string.onboarding_preferences_week_sunday),
                ),
                selected = prefs.weekStart,
                onSelected = { viewModel.setWeekStart(it) },
            )
        }

        // Card 3 : Weight unit
        SectionCard(title = stringResource(R.string.onboarding_preferences_card_weight_unit)) {
            RadioOptions(
                options = listOf(
                    WeightUnit.KG to stringResource(R.string.onboarding_preferences_weight_kg),
                    WeightUnit.LBS to stringResource(R.string.onboarding_preferences_weight_lbs),
                ),
                selected = prefs.weightUnit,
                onSelected = { viewModel.setWeightUnit(it) },
            )
        }

        // Card 4 : Length unit
        SectionCard(title = stringResource(R.string.onboarding_preferences_card_length_unit)) {
            RadioOptions(
                options = listOf(
                    LengthUnit.CM to stringResource(R.string.onboarding_preferences_length_cm),
                    LengthUnit.INCHES to stringResource(R.string.onboarding_preferences_length_in),
                ),
                selected = prefs.lengthUnit,
                onSelected = { viewModel.setLengthUnit(it) },
            )
        }

        // Card 5 : Theme mode (Light / Dark / System)
        SectionCard(title = stringResource(R.string.onboarding_preferences_card_theme)) {
            RadioOptions(
                options = listOf(
                    ThemeMode.LIGHT to stringResource(R.string.common_theme_light),
                    ThemeMode.DARK to stringResource(R.string.common_theme_dark),
                    ThemeMode.SYSTEM to stringResource(R.string.common_theme_system),
                ),
                selected = prefs.themeMode,
                onSelected = { viewModel.setThemeMode(it) },
            )
        }

        // Card 6 : Default morning routine time (TimePicker M3 cliquable)
        SectionCard(title = stringResource(R.string.onboarding_preferences_card_default_time)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { showTimePicker = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = String.format("%02d:%02d", prefs.morningRoutineHour, prefs.morningRoutineMinute),
                    color = appColors.textPrimary,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.onboarding_preferences_tap_to_change),
                    color = appColors.primaryAction,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = stringResource(R.string.onboarding_preferences_routine_helper),
                color = appColors.textTertiary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // NOTE 2026-05-11 : les toggle switches des settings sont rassemblés
        // dans l'étape 3 (Permissions) -- ne dupliquer ici que des choix
        // exclusifs (RadioGroup) ou des champs valeurs (TimePicker, etc.).
        // Les BOOLEANS settings (Switch) -> step 3.
    }

    if (showTimePicker) {
        RoutineTimePickerDialog(
            initialHour = prefs.morningRoutineHour,
            initialMinute = prefs.morningRoutineMinute,
            onConfirm = { h, m ->
                viewModel.setMorningRoutineTime(h, m)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

/**
 * RadioGroup générique : liste d'options (T, label) + selected courant +
 * callback. Factorise le pattern Row+CustomRadioButton+Text répété 3+ fois.
 */
@Composable
private fun <T> RadioOptions(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        options.forEach { (value, label) ->
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp).selectable(
                    selected = (value == selected),
                    onClick = { onSelected(value) },
                    role = Role.RadioButton,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CustomRadioButton(selected = (value == selected), onClick = null)
                Spacer(modifier = Modifier.height(0.dp))
                Text(
                    text = label,
                    color = appColors.textPrimary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * Card section appColors.bgRecessed : titre en appColors.primaryAction + content custom.
 * Pattern réutilisé Preferences/Permissions onboarding.
 */
@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(appColors.bgRecessed)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = appColors.primaryAction,
        )
        content()
    }
}
