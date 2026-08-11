package com.example.sportapp.feature.health.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.FormDialog
import com.example.sportapp.designsystem.theme.yellowMedium
import java.time.LocalDate

/**
 * Dialog de saisie d'une pesée (kg, décimales, style app). Le calendrier partagé
 * [HealthEntryCalendar] (accent jaune, identité Poids) permet de choisir le jour —
 * point sous les jours déjà renseignés, jours futurs non sélectionnables ;
 * sélectionner un jour renseigné pré-remplit sa valeur (la re-saisie écrase, uuid
 * déterministe user+type+date). [initialDate] (ISO, opt.) ouvre le dialog
 * pré-positionné sur ce jour — ex. tap sur le point d'un jour manquant dans un
 * chart. [onConfirm] reçoit (kg, date ISO).
 */
@Composable
fun WeightEntryDialog(
    current: Float?,
    existingByDate: Map<String, Float>,
    onConfirm: (Float, String) -> Unit,
    onDismiss: () -> Unit,
    initialDate: String? = null,
) {
    fun fmt(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else v.toString()

    val today = remember { LocalDate.now() }
    val initial = remember { initialDate?.let(LocalDate::parse) ?: today }
    var text by remember {
        mutableStateOf((initialDate?.let(existingByDate::get) ?: current)?.let(::fmt) ?: "")
    }
    var date by remember { mutableStateOf(initial) }
    val parsed = text.trim().replace(',', '.').toFloatOrNull()
    val valid = parsed != null && parsed > 0f && parsed < 500f

    FormDialog(
        title = stringResource(R.string.health_dash_weight_dialog_title),
        confirmText = stringResource(R.string.health_dash_goal_save),
        onConfirm = { parsed?.let { onConfirm(it, date.toString()) } },
        onDismiss = onDismiss,
        confirmEnabled = valid,
        disabledReason = if (!valid) stringResource(R.string.health_dash_weight_invalid) else null,
        scrollable = true,
    ) {
        HealthEntryCalendar(
            today = today,
            selected = date,
            existingByDate = existingByDate,
            accentColor = yellowMedium,
            onSelect = { day ->
                date = day
                // Jour déjà renseigné : pré-remplit sa valeur (écrasable).
                existingByDate[day.toString()]?.let { text = fmt(it) }
            },
        )
        CustomTextField(
            value = text,
            onValueChange = { new -> text = new.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
            placeholder = stringResource(R.string.health_dash_weight_placeholder),
            label = stringResource(R.string.health_dash_weight_label),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }
}
