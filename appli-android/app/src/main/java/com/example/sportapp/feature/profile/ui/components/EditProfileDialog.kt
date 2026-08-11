package com.example.sportapp.feature.profile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.core.network.MeProfileUpdateRequest
import com.example.sportapp.core.network.UserInfo
import com.example.sportapp.designsystem.common_components.CustomDatePickerDialog
import com.example.sportapp.designsystem.common_components.CustomRadioButton
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.DialogPrimaryButton
import com.example.sportapp.designsystem.common_components.DialogSecondaryButton
import com.example.sportapp.designsystem.theme.appColors
import java.time.LocalDate

/**
 * Dialog d'édition du profil (PATCH /me/profile self-only). Pré-rempli depuis
 * [user]. Champs : prénom / nom + bio (date de naissance via [CustomDatePickerDialog],
 * sexe en RadioGroup UPPER_CASE politique 11, taille cm / poids kg canoniques).
 * Tous optionnels — un champ vidé envoie `null` (= inchangé côté serveur,
 * `exclude_unset`). [onSave] reçoit le [MeProfileUpdateRequest] prêt.
 */
@Composable
fun EditProfileDialog(
    user: UserInfo,
    onDismiss: () -> Unit,
    onSave: (MeProfileUpdateRequest) -> Unit,
) {
    var email by remember { mutableStateOf(user.email ?: "") }
    var firstName by remember { mutableStateOf(user.firstName ?: "") }
    var lastName by remember { mutableStateOf(user.lastName ?: "") }
    var birthDate by remember { mutableStateOf(user.birthDate ?: "") }
    var sex by remember { mutableStateOf(user.sex ?: "") }
    var height by remember { mutableStateOf(user.heightCm?.let(::trimNum) ?: "") }
    var weight by remember { mutableStateOf(user.weightKg?.let(::trimNum) ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = appColors.bgScreen,
        title = {
            Text(stringResource(R.string.profile_edit_title), color = appColors.primaryAction)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CustomTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = stringResource(R.string.profile_field_email),
                    label = stringResource(R.string.profile_field_email),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                CustomTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    placeholder = stringResource(R.string.profile_field_first_name),
                    label = stringResource(R.string.profile_field_first_name),
                )
                CustomTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    placeholder = stringResource(R.string.profile_field_last_name),
                    label = stringResource(R.string.profile_field_last_name),
                )

                // Date de naissance : champ cliquable -> CustomDatePickerDialog
                Text(stringResource(R.string.profile_field_birthdate), color = appColors.textSecondary)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(appColors.bgRecessed)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = birthDate.ifBlank { stringResource(R.string.profile_value_not_set) },
                        color = if (birthDate.isBlank()) appColors.textTertiary else appColors.textPrimary,
                    )
                }

                // Sexe : RadioGroup (codes wire UPPER_CASE conservés)
                Text(stringResource(R.string.profile_field_sex), color = appColors.textSecondary)
                Column(modifier = Modifier.selectableGroup()) {
                    listOf(
                        "MALE" to stringResource(R.string.onboarding_bio_radio_male),
                        "FEMALE" to stringResource(R.string.onboarding_bio_radio_female),
                        "OTHER" to stringResource(R.string.onboarding_bio_radio_other),
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clickable { sex = value },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CustomRadioButton(selected = sex == value, onClick = null)
                            Text(
                                text = label,
                                color = appColors.textPrimary,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }

                CustomTextField(
                    value = height,
                    onValueChange = { height = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    placeholder = stringResource(R.string.onboarding_bio_placeholder_height_cm),
                    label = stringResource(R.string.profile_field_height),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                CustomTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    placeholder = stringResource(R.string.onboarding_bio_placeholder_weight_kg),
                    label = stringResource(R.string.profile_field_weight),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            DialogPrimaryButton(
                text = stringResource(R.string.common_save),
                onClick = {
                    onSave(
                        MeProfileUpdateRequest(
                            email = email.trim().ifBlank { null },
                            firstName = firstName.trim().ifBlank { null },
                            lastName = lastName.trim().ifBlank { null },
                            birthDate = birthDate.ifBlank { null },
                            sex = sex.ifBlank { null },
                            heightCm = height.replace(',', '.').toFloatOrNull(),
                            weightKg = weight.replace(',', '.').toFloatOrNull(),
                        )
                    )
                },
            )
        },
        dismissButton = {
            DialogSecondaryButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismiss,
            )
        },
    )

    if (showDatePicker) {
        CustomDatePickerDialog(
            initialIso = birthDate,
            title = stringResource(R.string.profile_field_birthdate),
            defaultDate = LocalDate.now().minusYears(25),
            onConfirm = { iso ->
                birthDate = iso
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

/** "175.0" -> "175" ; "72.5" -> "72.5". Évite les `.0` parasites à l'affichage. */
private fun trimNum(f: Float): String =
    if (f % 1f == 0f) f.toInt().toString() else f.toString()
