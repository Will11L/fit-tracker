package com.example.sportapp.feature.goals.ui.components.goalsTabContent

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.utils.localizedPriority
import com.example.sportapp.designsystem.common_components.DialogSecondaryButton
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun EditPriorityDialog(
    currentPriority: String,
    onDismiss: () -> Unit,
    onPrioritySelected: (String) -> Unit
) {
    val priorities = listOf("HIGH", "MEDIUM", "LOW")
    val selected = remember { mutableStateOf(currentPriority) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.goals_priority_title), color = appColors.primaryAction)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.goals_priority_select), color = appColors.textPrimary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    priorities.forEach { priority ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable {
                                        selected.value = priority
                                        onPrioritySelected(priority)
                                    }
                                    .border(
                                        width = if (priority == selected.value) 1.5.dp else 1.dp,
                                        color = if (priority == selected.value) appColors.primaryAction else appColors.divider,
                                        shape = MaterialTheme.shapes.extraSmall
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                PriorityIcon(priority = priority, showBorder = false)
                            }

                            Text(
                                text = localizedPriority(priority),
                                fontSize = 12.sp,
                                color = if (priority == selected.value) appColors.primaryAction else appColors.textSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogSecondaryButton(text = stringResource(R.string.common_close), onClick = onDismiss)
        },
        containerColor = appColors.bgRecessed
    )
}
