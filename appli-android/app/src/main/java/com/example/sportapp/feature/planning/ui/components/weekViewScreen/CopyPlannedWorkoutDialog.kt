package com.example.sportapp.feature.planning.ui.components.weekViewScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.core.utils.localizedDayOfWeek
import com.example.sportapp.designsystem.common_components.DialogPrimaryButton
import com.example.sportapp.designsystem.common_components.DialogSecondaryButton
import com.example.sportapp.designsystem.common_components.SingleSelectDropdown
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun CopyPlannedWorkoutDialog(
    currentDay: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val days = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
    val availableDays = days.filter { it != currentDay }

    var selectedDay by remember {
        mutableStateOf(availableDays.firstOrNull() ?: "Monday")
    }

    // ✅ si currentDay change / recomposition, on force selectedDay à rester valide
    LaunchedEffect(currentDay) {
        if (selectedDay == currentDay || selectedDay !in availableDays) {
            selectedDay = availableDays.firstOrNull() ?: "Monday"
        }
    }

    // Jours affichés localisés, mais la valeur passée à onConfirm reste le jour
    // canonique EN ("Monday"…). Mapping label localisé -> jour canonique.
    val canonicalByLocalized = availableDays.associateBy { localizedDayOfWeek(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DialogPrimaryButton(
                text = stringResource(R.string.copy_workout_confirm),
                onClick = { onConfirm(selectedDay) }
            )
        },
        dismissButton = {
            DialogSecondaryButton(text = stringResource(R.string.common_cancel), onClick = onDismiss)
        },
        title = {
            Text(
                text = stringResource(R.string.copy_workout_title),
                color = appColors.primaryAction,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.copy_workout_choose_day),
                    color = appColors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                // ✅ IMPORTANT: options = availableDays (pas days)
                SingleSelectDropdown(
                    label = stringResource(R.string.copy_workout_day_label),
                    selected = localizedDayOfWeek(selectedDay),
                    options = availableDays.map { localizedDayOfWeek(it) },
                    onSelect = { localized -> canonicalByLocalized[localized]?.let { selectedDay = it } }
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.copy_workout_current_day, localizedDayOfWeek(currentDay)),
                    color = appColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        containerColor = appColors.bgScreen
    )
}
