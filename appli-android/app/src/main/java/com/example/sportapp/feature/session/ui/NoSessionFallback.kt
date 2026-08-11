package com.example.sportapp.feature.session.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.PlannedWorkout
import com.example.sportapp.feature.session.ui.components.sessionTab.CreateActualWorkoutDialog
import com.example.sportapp.designsystem.common_components.ActionIconWithTextButton
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun NoSessionFallback(
    plannedWorkout: PlannedWorkout? = null,
    onNavigateToProgram: () -> Unit = {},
    onStartPlanned: (PlannedWorkout) -> Unit = {},
    onCreateActualWorkout: (sessionName: String) -> Unit = {}
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.material3.Text(
            text = stringResource(R.string.home_currently_sleeping),
            style = MaterialTheme.typography.headlineSmall,
            color = appColors.textPrimary
        )

        Spacer(Modifier.height(32.dp))

        // ✅ View program
        ActionIconWithTextButton(
            iconRes = R.drawable.ic_calendar_month,
            text = stringResource(R.string.home_view_program),
            onClick = onNavigateToProgram
        )

        Spacer(Modifier.height(20.dp))

        if (plannedWorkout != null) {
            // ✅ Start planned workout -- nom user-typed conserve tel quel (politique 6.2)
            ActionIconWithTextButton(
                iconRes = R.drawable.ic_rounded_double_arrow,
                text = stringResource(R.string.home_start_planned, plannedWorkout.name),
                backgroundColor = appColors.selectedFill,
                onClick = { onStartPlanned(plannedWorkout) }
            )
        } else {
            // ✅ Start a new session (sans background si tu veux style outlined)
            ActionIconWithTextButton(
                iconRes = R.drawable.ic_rounded_add_box,
                text = stringResource(R.string.home_start_new_session),
                backgroundColor = appColors.selectedFill,
                onClick = { showCreateDialog = true }
            )
        }
    }

    if (showCreateDialog) {
        CreateActualWorkoutDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                val normalized = name.trim()
                if (normalized.isNotEmpty() && !normalized.equals("Rest Day", ignoreCase = true)) {
                    onCreateActualWorkout(normalized)
                }
                showCreateDialog = false
            }
        )
    }
}
