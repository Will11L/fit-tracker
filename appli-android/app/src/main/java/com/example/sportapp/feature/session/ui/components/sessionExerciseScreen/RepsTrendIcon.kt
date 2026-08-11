package com.example.sportapp.feature.session.ui.components.sessionExerciseScreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun RepsTrendIcon(
    pendingDeletion: Boolean,
    actualReps: Int,
    targetRange: IntRange,
    modifier: Modifier = Modifier
) {
    val (iconId, tintColor) = when {
        pendingDeletion -> R.drawable.ic_check_indeterminate_small to appColors.textTertiary
        actualReps == 0 -> R.drawable.ic_check_indeterminate_small to appColors.textTertiary
        actualReps > targetRange.last -> R.drawable.ic_north to blueMedium
        actualReps < targetRange.first -> R.drawable.ic_south to redMedium
        actualReps == targetRange.first -> R.drawable.ic_rounded_check to orangeMedium
        else -> R.drawable.ic_rounded_check to mediumGreen
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconId),
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(20.dp)
        )
    }
}