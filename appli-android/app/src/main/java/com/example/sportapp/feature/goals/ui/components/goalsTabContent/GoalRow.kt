package com.example.sportapp.feature.goals.ui.components.goalsTabContent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.MuscleGoal
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.ActionTextButton
import com.example.sportapp.designsystem.theme.*


@Composable
fun GoalRow(
    muscleGoal: MuscleGoal,
    muscleName: String,
    onMuscleClick: (MuscleGoal) -> Unit,
    onTargetClick: (MuscleGoal) -> Unit,
    onPriorityChanged: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .padding(vertical = 4.dp)
    ) {
        val isPendingDeletion = muscleGoal.pendingDeletion

        val textFontSize = 14.sp
        val rowHeight = 36.dp

        val showPriorityDialog = remember { mutableStateOf(false) }


        val status = muscleGoal.status
            .trim()
            .uppercase()
            .replace(" ", "_")

        val iconRes = if (muscleGoal.pendingDeletion) {
            R.drawable.ic_rounded_cancel
        } else {
            when (status) {
                "DONE" -> R.drawable.ic_rounded_check
                "SKIPPED" -> R.drawable.ic_rounded_close
                "IN_PROGRESS" -> R.drawable.ic_arrow_progress
                else -> R.drawable.ic_check_indeterminate_small
            }
        }

        val tintColor = if (muscleGoal.pendingDeletion) {
            redMedium.copy(alpha = 0.7f)
        } else {
            when (status) {
                "DONE" -> mediumGreen
                "SKIPPED" -> redMedium
                "IN_PROGRESS" -> blueMedium
                else -> appColors.textTertiary
            }
        }

        val rowBackgroundColor = if (isPendingDeletion) darkGray else appColors.bgRecessed
        val boxBackgroundColor = if (isPendingDeletion) darkGray else appColors.bgSurface
        val textColor = if (isPendingDeletion) appColors.textTertiary else appColors.textPrimary
        val iconTint = if (isPendingDeletion) appColors.textTertiary else tintColor
        val isRowInteractive = !isPendingDeletion
        val iconBackground = rowBackgroundColor
        val priority = muscleGoal.priority.uppercase()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .background(rowBackgroundColor, shape = MaterialTheme.shapes.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .height(rowHeight) // ⬅️ Aligne avec la Row
                    .background(boxBackgroundColor, shape = MaterialTheme.shapes.small)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = isRowInteractive) {
                        if (isRowInteractive) onMuscleClick(muscleGoal)
                    }
                    .weight(5f),  // Aligne sur header table (refonte 2026-05-09 iter 2 : 4f -> 5f).
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = muscleName,
                    fontSize = textFontSize,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,  // Refonte 2026-05-09 iter 3 : tronque "Vastus Lateralis" -> "Vastus Lateral...".
                    modifier = Modifier.padding(start = 14.dp, end = 6.dp)
                )
            }

            // Priority icon with colored border
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(2f),
                contentAlignment = Alignment.Center
            ) {
                PriorityIcon(
                    priority = priority,
                    isDisabled = isPendingDeletion,
                    clickable = isRowInteractive,
                    onClick = {
                        if (isRowInteractive) showPriorityDialog.value = true
                    }
                )
            }

            // Done
            Box(
                modifier = Modifier
                    .height(rowHeight)
                    .weight(2f),
                contentAlignment = Alignment.Center
            ) {
                ActionTextButton(
                    text = muscleGoal.done.toString(),
                    textColor = textColor,
                    hasBackground = false,
                    clickable = false
                )
            }

            // Small space between Done and Todo
            Spacer(modifier = Modifier.weight(0.3f))

            // To Do
            Box(
                modifier = Modifier
                    .height(rowHeight)
                    .clip(MaterialTheme.shapes.small)
                    .weight(2f),
                contentAlignment = Alignment.Center
            ) {
                ActionTextButton(
                    text = muscleGoal.target,
                    textColor = textColor,
                    hasBackground = true,
                    backgroundColor = boxBackgroundColor,
                    clickable = isRowInteractive,
                    onClick = {
                        if (isRowInteractive) onTargetClick(muscleGoal)
                    },
                )
            }

            // Small space between Todo and Status
            Spacer(modifier = Modifier.weight(0.3f))

            // Status
            Box(
                modifier = Modifier
                    .height(rowHeight)
                    .weight(2f),
                contentAlignment = Alignment.Center
            ) {
                ActionIconButton(
                    iconRes = iconRes,
                    tint = iconTint,
                    hasBackground = false,
                    customBackgroundColor = iconBackground,
                    clickable = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                )
            }
        }

        if (showPriorityDialog.value) {
            EditPriorityDialog(
                currentPriority = muscleGoal.priority,
                onDismiss = { showPriorityDialog.value = false },
                onPrioritySelected = {newPriority ->
                    onPriorityChanged(newPriority)
                    showPriorityDialog.value = false
                }
            )
        }
    }
}