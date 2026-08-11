package com.example.sportapp.feature.session.ui.components.sessionTab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.ActualWorkoutExercise
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.EntityListRow
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.designsystem.theme.yellowMedium

@Composable
fun SessionExerciseRow(
    exercise: Exercise,
    actualWorkoutExercise: ActualWorkoutExercise,
    setsToDo: Int,
    setsDone: Int,
    backgroundColor: Color,
    nameBoxColor: Color = Color.Transparent,
    onClickOptions: (Exercise) -> Unit,
    onClickDetails: (Exercise) -> Unit
) {
    EntityListRow(
        isPendingDeletion = actualWorkoutExercise.pendingDeletion,
        backgroundColor = backgroundColor,
        nameBoxColor = nameBoxColor,
        name = exercise.name,
        nameWeight = 2.5f,
        onNameClick = { onClickOptions(exercise) },
        verticalPadding = 5.dp,
        trailingContent = {
            // ☁️ Sync status
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!actualWorkoutExercise.synced) {
                    ActionIconButton(
                        iconRes = R.drawable.ic_cloud_off,
                        tint = yellowMedium,
                        iconSize = 20.dp,
                        hasBackground = false,
                        clickable = false
                    )
                } else {
                    ActionIconButton(
                        iconRes = R.drawable.ic_cloud_done,
                        tint = appColors.primaryAction,
                        iconSize = 20.dp,
                        hasBackground = false,
                        clickable = false
                    )
                }
            }

            // 🔁 Sets Done / Sets à faire
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${setsDone}/${setsToDo}",
                    color = appColors.textPrimary,
                    fontSize = 14.sp
                )
            }

            // ✅ Status / icône — Bg coloré 44dp (pill visible) + ActionIconButton
            // interne qui porte le click (tap-cycle DONE/IN_PROGRESS/NEXT/NOT_STARTED
            // → onClickDetails). Fix 2026-05-23 : avant, le click était sur l'outer
            // Box.clickable et l'inner ActionIconButton(clickable=false) absorbait
            // le tap → onClickDetails jamais appelé. Compose : `clickable(enabled=false)`
            // consomme quand même les pointer events, ne les laisse pas passer au parent.
            val statusBackground = if (actualWorkoutExercise.pendingDeletion) {
                Color.Transparent
            } else {
                when (actualWorkoutExercise.status) {
                    "DONE" -> mediumGreen
                    "IN_PROGRESS" -> orangeMedium
                    "NEXT" -> blueMedium
                    "SKIPPED" -> redMedium
                    "NOT_STARTED" -> blueMedium
                    else -> appColors.textTertiary
                }
            }

            val statusIcon = if (actualWorkoutExercise.pendingDeletion) {
                R.drawable.ic_rounded_close
            } else {
                when (actualWorkoutExercise.status) {
                    "DONE" -> R.drawable.ic_rounded_check
                    "IN_PROGRESS" -> R.drawable.ic_arrow_progress
                    "NEXT" -> R.drawable.ic_keyboard_arrow_right
                    "SKIPPED" -> R.drawable.ic_rounded_cancel
                    "NOT_STARTED" -> R.drawable.ic_keyboard_arrow_right
                    else -> R.drawable.ic_rounded_info
                }
            }

            val statusTint = if (actualWorkoutExercise.pendingDeletion) {
                appColors.textTertiary
            } else {
                appColors.textPrimary
            }

            val statusClickable = !actualWorkoutExercise.pendingDeletion &&
                (actualWorkoutExercise.status == "DONE" ||
                    actualWorkoutExercise.status == "NEXT" ||
                    actualWorkoutExercise.status == "IN_PROGRESS" ||
                    actualWorkoutExercise.status == "NOT_STARTED")

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(statusBackground),
                contentAlignment = Alignment.Center
            ) {
                ActionIconButton(
                    iconRes = statusIcon,
                    tint = statusTint,
                    iconSize = 30.dp,
                    hasBackground = false,
                    clickable = statusClickable,
                    onClick = { onClickDetails(exercise) },
                )
            }
        }
    )
}
