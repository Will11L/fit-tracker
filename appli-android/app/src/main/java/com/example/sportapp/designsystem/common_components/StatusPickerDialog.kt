package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.appColors

/** Une option de [StatusPickerDialog] : code wire UPPER_CASE + label affiché + icône + couleur. */
data class StatusOption(
    val value: String,
    val label: String,
    val icon: Int,
    val color: Color,
)

/**
 * Dialog de choix d'un statut : chaque option est une ligne sélectionnable
 * (fond teinté par la couleur de l'option si sélectionnée). Bâti sur
 * [FormDialog]. Canonique partagé — remplace `ChangeGoalStatusDialog` et
 * `ChangeSetStatusDialog` (R13). [onConfirm] reçoit le `value` sélectionné.
 */
@Composable
fun StatusPickerDialog(
    title: String,
    options: List<StatusOption>,
    selected: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = stringResource(R.string.common_update),
) {
    var selectedValue by remember(selected) { mutableStateOf(selected) }

    FormDialog(
        title = title,
        confirmText = confirmText,
        onConfirm = { onConfirm(selectedValue) },
        onDismiss = onDismiss,
    ) {
        options.forEach { option ->
            val isSelected = selectedValue == option.value
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { selectedValue = option.value }
                    .background(
                        if (isSelected) option.color.copy(alpha = 0.1f) else appColors.bgRecessed,
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = option.label,
                    color = if (isSelected) option.color else appColors.textPrimary
                )
                ActionIconButton(
                    iconRes = option.icon,
                    tint = option.color,
                    hasBackground = false,
                    clickable = false
                )
            }
        }
    }
}
