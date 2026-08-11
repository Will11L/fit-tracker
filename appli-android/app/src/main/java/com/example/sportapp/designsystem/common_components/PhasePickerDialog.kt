package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.appColors

/**
 * Dialog de choix de phase d'un exercice : WARMUP / TRAINING / POST_TRAINING.
 * Canonique partagé — remplace les dialogs inline dupliqués de SessionTab et
 * PlannedWorkoutScreen (R4). [onPhaseSelected] reçoit le code wire UPPER_CASE.
 */
@Composable
fun PhasePickerDialog(
    onPhaseSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        containerColor = appColors.bgScreen,
        title = {
            Text(
                text = stringResource(R.string.session_phase_dialog_title),
                color = appColors.textPrimary,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Clé internal = UPPER_CASE wire (politique 11). Label localisé.
                val phaseMap = mapOf(
                    stringResource(R.string.session_phase_warmup) to "WARMUP",
                    stringResource(R.string.session_phase_training) to "TRAINING",
                    stringResource(R.string.session_phase_posttraining) to "POST_TRAINING",
                )

                phaseMap.forEach { (label, value) ->
                    Button(
                        onClick = { onPhaseSelected(value) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColors.bgRecessed,
                            contentColor = appColors.textPrimary
                        ),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            color = appColors.textPrimary
                        )
                    }
                }
            }
        }
    )
}
