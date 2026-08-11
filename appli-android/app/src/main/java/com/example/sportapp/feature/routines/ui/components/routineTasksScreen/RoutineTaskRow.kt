package com.example.sportapp.feature.routines.ui.components.routineTasksScreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Task
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.CustomCheckbox
import com.example.sportapp.designsystem.common_components.EntityListRow
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.yellowMedium

@Composable
fun RoutineTaskRow(
    modifier: Modifier = Modifier,
    task: Task,
    isChecked: Boolean,
    isCheckSynced: Boolean,
    backgroundColor: Color,
    nameBoxColor: Color = Color.Transparent,
    onClickOptions: (Task) -> Unit,
    onToggleChecked: (Task, Boolean) -> Unit,
    onClickDetails: (Task) -> Unit = {},
    dragHandle: (@Composable () -> Unit)? = null,
    /** Couleur de l'icone check quand isChecked=true. Default mediumGreen
     * (utilise partout : DAILY + non-DAILY pour coherence visuelle "fait"). */
    checkedIconColor: Color = mediumGreen,
    /** Couleur de l'icone quand isChecked=false. Default dividerColor (gris
     * clair, visible sur DAILY dark bleu). Override pour non-DAILY -> couleur
     * de la row (mediumGreen pour W/M/Y, orangeMedium pour NONE) pour se
     * demarquer du fond tintee. */
    uncheckedIconColor: Color = appColors.divider,
) {
    EntityListRow(
        modifier = modifier,
        isPendingDeletion = task.pendingDeletion,
        backgroundColor = backgroundColor,
        nameBoxColor = nameBoxColor,
        name = task.title,
        nameWeight = 1f,
        nameMaxLines = 1,
        onNameClick = { onClickOptions(task) },
        verticalPadding = 5.dp,
        contentEndPadding = 8.dp,  // 2026-05-13 : gap egal a droite de la checkbox
        leadingContent = if (dragHandle != null) {
            {
                // ✅ Drag handle (zone dédiée)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    dragHandle()
                }
            }
        } else null,
        trailingContent = {
            // 2026-05-13 : gaps egaux 8.dp entre titre / sync / checkbox / bord
            Spacer(modifier = Modifier.width(8.dp))

            // ☁️ Sync status (ActionIconButton 40dp box)
            ActionIconButton(
                iconRes = if (isCheckSynced) R.drawable.ic_cloud_done else R.drawable.ic_cloud_off,
                tint = if (isCheckSynced) appColors.primaryAction else yellowMedium,
                iconSize = 20.dp,
                hasBackground = false,
                clickable = false,
            )

            Spacer(modifier = Modifier.width(8.dp))

            // ✅ Checkbox 44dp
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                CustomCheckbox(
                    checked = isChecked,
                    enabled = task.isActive,
                    pendingDeletion = task.pendingDeletion,
                    backgroundColor = Color.Transparent,
                    iconTint = if (isChecked) checkedIconColor else uncheckedIconColor,
                    onClick = { onToggleChecked(task, !isChecked) }
                )
            }
        }
    )
}
