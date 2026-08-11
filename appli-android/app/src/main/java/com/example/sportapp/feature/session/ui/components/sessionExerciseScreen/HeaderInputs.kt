package com.example.sportapp.feature.session.ui.components.sessionExerciseScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.theme.*

@Composable
fun HeaderInputs(
    onSyncClick: () -> Unit,
    onGoToExerciseClick: () -> Unit,
    checkedSets: Int,
    totalSets: Int,
    recommendedReps: String?,
    isDone: Boolean,
    allSetsSynced: Boolean,
    actualWorkoutExerciseOrder: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.bgRecessed, shape = MaterialTheme.shapes.small)
            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // 👉 Détails (N°, Sets, Reps)
        Row(modifier = Modifier
            .weight(4f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderDetailColumn(title = "N°", value = actualWorkoutExerciseOrder, hasBackground = false, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            HeaderDetailColumn(title = "Sets", value = "$checkedSets / $totalSets", modifier = Modifier.weight(2f))
            Spacer(modifier = Modifier.width(8.dp))
            HeaderDetailColumn(title = "Reps", value = recommendedReps ?: "-", modifier = Modifier.weight(2f))
            Spacer(modifier = Modifier.width(8.dp))
        }

        Spacer(modifier = Modifier
            .weight(0.2f)
            .background(redMedium, shape = RoundedCornerShape(6.dp))
        )

        // 👉 Actions (title + buttons)
        Column(
            modifier = Modifier
                .padding(start = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
        ) {
            Text(
                text = "Actions",
                color = appColors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.width(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                // 👉 Boutons d'action
                ActionIconButton(
                    iconRes = if (isDone) R.drawable.ic_rounded_check_circle else R.drawable.ic_arrow_progress,
                    tint = if (isDone) mediumGreen else appColors.textPrimary,
                    iconSize = 26.dp,
                    hasBackground = false,
                    clickable = true
                )
                ActionIconButton(
                    iconRes = if (allSetsSynced) R.drawable.ic_cloud_done else R.drawable.ic_cloud_off,
                    tint = if (allSetsSynced) appColors.primaryAction else yellowMedium,
                    iconSize = 26.dp,
                    hasBackground = allSetsSynced,
                    customBackgroundColor = Color.Transparent,
                    onClick = onSyncClick
                )
                Spacer(modifier = Modifier.width(6.dp))
                // Stats de l'exo : fond secondBlue (demande user 2026-07-15, parité web).
                ActionIconButton(
                    iconRes = R.drawable.ic_rounded_monitoring,
                    customBackgroundColor = secondBlue,
                    onClick = onGoToExerciseClick
                )
            }
        }
    }
}