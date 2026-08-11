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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.feature.onboarding.ui.OnboardingViewModel
import com.example.sportapp.feature.onboarding.ui.components.BirthDateField
import com.example.sportapp.designsystem.common_components.CustomDatePickerDialog
import com.example.sportapp.designsystem.common_components.CustomRadioButton
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.theme.appColors
import java.time.LocalDate

/**
 * Step BIO de l'onboarding -- 4 fields nullable optionnels :
 * - birthDate (DatePicker M3)
 * - sex (RadioGroup MALE/FEMALE/OTHER UPPER_CASE policy 11)
 * - heightCm (TextField numeric, canonique cm)
 * - weightKg (TextField numeric, canonique kg)
 *
 * Tous skippables -- l'user peut Next sans rien remplir. Patch /me/profile
 * uniquement si au moins un champ est rempli (cf. OnboardingViewModel.applyBioIfNeeded).
 */
@Composable
fun OnboardingBioScreen(viewModel: OnboardingViewModel) {
    val birthDate by viewModel.birthDateDraft.collectAsState()
    val sex by viewModel.sexDraft.collectAsState()
    val heightInput by viewModel.heightCmDraft.collectAsState()
    val weightInput by viewModel.weightKgDraft.collectAsState()
    val prefs by viewModel.preferencesDraft.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    val heightPlaceholder = stringResource(
        if (prefs.lengthUnit == com.example.sportapp.feature.onboarding.data.LengthUnit.INCHES)
            R.string.onboarding_bio_placeholder_height_in
        else R.string.onboarding_bio_placeholder_height_cm
    )
    val weightPlaceholder = stringResource(
        if (prefs.weightUnit == com.example.sportapp.feature.onboarding.data.WeightUnit.LBS)
            R.string.onboarding_bio_placeholder_weight_lb
        else R.string.onboarding_bio_placeholder_weight_kg
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Page main title centré hors card
        Text(
            text = stringResource(R.string.onboarding_bio_title),
            style = MaterialTheme.typography.headlineSmall,
            color = appColors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.onboarding_bio_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = appColors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // Birth date : widget custom (label en haut style CustomTextField +
        // trailing icon ActionIconButton calendrier + valeur MM/DD/YYYY).
        BirthDateField(
            isoValue = birthDate,
            onClick = { showDatePicker = true },
        )

        // Card 2 : Sex (UPPER_CASE wire codes MALE/FEMALE/OTHER conserves -- politique 11)
        SectionCard(title = stringResource(R.string.onboarding_bio_card_sex)) {
            BioRadioOptions(
                options = listOf(
                    "MALE" to stringResource(R.string.onboarding_bio_radio_male),
                    "FEMALE" to stringResource(R.string.onboarding_bio_radio_female),
                    "OTHER" to stringResource(R.string.onboarding_bio_radio_other),
                ),
                selected = sex,
                onSelected = { viewModel.setSex(it) },
            )
        }

        // Height + Weight : pas de SectionCard wrapper -- le CustomTextField a
        // déjà son fond appColors.bgRecessed propre + label animé au focus (style firstName).
        // L'unité est dans le placeholder (adapté kg/lbs, cm/inches).
        CustomTextField(
            value = heightInput,
            onValueChange = { viewModel.setHeightCm(it.filter { c -> c.isDigit() || c == '.' || c == ',' }) },
            placeholder = heightPlaceholder,
            label = stringResource(R.string.onboarding_bio_label_height),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        CustomTextField(
            value = weightInput,
            onValueChange = { viewModel.setWeightKg(it.filter { c -> c.isDigit() || c == '.' || c == ',' }) },
            placeholder = weightPlaceholder,
            label = stringResource(R.string.onboarding_bio_label_weight),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }

    if (showDatePicker) {
        CustomDatePickerDialog(
            initialIso = birthDate,
            title = stringResource(R.string.onboarding_bio_card_birthdate),
            defaultDate = LocalDate.now().minusYears(25),
            onConfirm = { iso ->
                viewModel.setBirthDate(iso)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

/**
 * RadioGroup local au BIO step : sélection nullable (l'user peut tap pour
 * select une valeur, ou laisser vide = `selected == null`).
 */
@Composable
private fun BioRadioOptions(
    options: List<Pair<String, String>>,
    selected: String?,
    onSelected: (String) -> Unit,
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
 * Pattern réutilisé (dupliqué depuis OnboardingPreferencesScreen pour
 * éviter une dépendance horizontale ; à factoriser si on en réutilise plus).
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
