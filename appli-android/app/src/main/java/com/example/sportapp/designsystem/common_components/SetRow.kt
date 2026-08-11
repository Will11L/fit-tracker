package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.ActualWorkoutSet
import com.example.sportapp.feature.onboarding.data.WeightUnit
import com.example.sportapp.feature.onboarding.data.formatWeightValue
import com.example.sportapp.feature.session.ui.components.sessionExerciseScreen.RepsTrendIcon
import com.example.sportapp.designsystem.theme.*

@Composable
fun SetRow(
    set: ActualWorkoutSet,
    targetRepsRange : IntRange,
    onIndexClick: () -> Unit,
    onEditRepsClick: () -> Unit,
    onEditWeightClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onAddNoteClick: () -> Unit,
    weightUnit: WeightUnit = WeightUnit.KG,
) {
    val rowHeight = 35.dp
    Row(
        modifier = Modifier
            .height(rowHeight)
            .fillMaxWidth()
            .background(
                color = when {
                    set.pendingDeletion -> darkGray
                    set.isDropset -> appColors.bgRecessed.copy(alpha = 0.5f)
                    else -> appColors.bgRecessed
                },
                shape = RoundedCornerShape(6.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (set.isDropset) {
            Icon(
                painter = painterResource(id = R.drawable.ic_rounded_subdirectory_arrow_right),
                contentDescription = "Drop Set",
                tint = appColors.divider,
                modifier = Modifier
                    .weight(1.6f)
                    .size(20.dp)
            )
        } else {
            SetRowBoxContent(
                text = "${set.setOrder}",
                modifier = Modifier.weight(1.6f).height(rowHeight).clip(RoundedCornerShape(6.dp)),
                hasBackground = false,
                onClick = onIndexClick
            )
        }
        SetRowBoxContent(
            text = "${set.reps}",
            modifier = Modifier.weight(2f).height(rowHeight),
            hasBackground = !set.pendingDeletion,
            onClick = onEditRepsClick
        )
        CustomSpacer()
        SetRowBoxContent(
            text = formatWeightValue(set.weight, weightUnit),
            modifier = Modifier.weight(2f).height(rowHeight),
            hasBackground = !set.pendingDeletion,
            onClick = onEditWeightClick
        )
        CustomSpacer()

        RepsTrendIcon(
            pendingDeletion = set.pendingDeletion,
            actualReps = set.reps,
            targetRange = targetRepsRange,
            modifier = Modifier
                .weight(1.6f)
                .height(32.dp)
        )
        val (iconRes, iconColor) = getStatusIconAndColor(set.status, set.pendingDeletion)
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier
                .weight(1.6f)
                .size(24.dp)
        )

        CustomSpacer()

        ActionIconButton(
            iconRes = R.drawable.ic_rounded_delete_sweep,
            tint = if (set.pendingDeletion) appColors.textTertiary else appColors.textPrimary,
            hasBackground = !set.pendingDeletion,
            customBackgroundColor = redMedium,
            modifier = Modifier
                .weight(1.6f),
            onClick = onDeleteClick
        )

        CustomSpacer()

        ActionIconButton(
            iconRes = if (set.notes.isNullOrEmpty()) R.drawable.ic_rounded_add_notes else R.drawable.ic_rounded_notes,
            tint = if (set.pendingDeletion) appColors.textTertiary else appColors.textPrimary,
            hasBackground = !set.pendingDeletion,
            modifier = Modifier
                .weight(1.6f),
            onClick = onAddNoteClick,
        )
    }
}

@Composable
fun getStatusIconAndColor(status: String, pendingDeletion: Boolean): Pair<Int, Color> {
    if (pendingDeletion) {
        return R.drawable.ic_check_indeterminate_small to appColors.textTertiary
    }

    return when (status.uppercase()) {
        "DONE" -> R.drawable.ic_rounded_check_circle to mediumGreen
        "IN_PROGRESS" -> R.drawable.ic_arrow_progress to orangeMedium
        "NOT_STARTED" -> R.drawable.ic_rounded_help to appColors.textTertiary
        "SKIPPED" -> R.drawable.ic_rounded_cancel to redMedium
        else -> R.drawable.ic_rounded_question_mark to appColors.textTertiary
    }
}
