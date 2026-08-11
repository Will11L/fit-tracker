package com.example.sportapp.feature.exercises.ui.components.exerciseScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.CustomSpacer
import com.example.sportapp.designsystem.common_components.SetRowBoxContent
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium

/** Row d'une séance passée (Last Sessions) : date | séries x/y | reps x/y | flèche → SessionExerciseScreen. */
@Composable
fun LastSessionRow(
    date: String,
    sets: String,
    reps: String,
    onOpenClick: () -> Unit
) {
    val rowHeight = 40.dp

    Row(
        modifier = Modifier
            .height(rowHeight)
            .fillMaxWidth()
            .background(appColors.bgRecessed, shape = RoundedCornerShape(6.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SetRowBoxContent(
            text = date,
            hasBackground = false,
            modifier = Modifier
                .weight(2.5f)
                .height(rowHeight),
        )

        CustomSpacer()

        SetRowBoxContent(
            text = sets,
            hasBackground = false,
            modifier = Modifier
                .weight(2f)
                .height(rowHeight),
        )

        CustomSpacer()

        SetRowBoxContent(
            text = reps,
            hasBackground = false,
            modifier = Modifier
                .weight(2.5f)
                .height(rowHeight),
        )

        CustomSpacer()

        // → Voir la séance de l'exo (SessionExerciseScreen) — fond blueMedium comme
        // le bouton navigation de ExerciseCard.
        ActionIconButton(
            iconRes = R.drawable.ic_arrow_right_alt,
            tint = appColors.textPrimary,
            hasBackground = true,
            customBackgroundColor = blueMedium,
            modifier = Modifier
                .weight(1f),
            onClick = onOpenClick
        )
    }
}
