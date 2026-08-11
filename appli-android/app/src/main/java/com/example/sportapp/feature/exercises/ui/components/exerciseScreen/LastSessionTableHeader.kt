package com.example.sportapp.feature.exercises.ui.components.exerciseScreen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.CustomSpacer
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun LastSessionTableHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.exercise_last_session_col_date),
            modifier = Modifier.weight(2.5f),
            fontSize = 14.sp,
            color = appColors.textSecondary,
            textAlign = TextAlign.Center
        )
        CustomSpacer()
        Text(
            text = stringResource(R.string.exercise_last_session_col_sets),
            modifier = Modifier.weight(2f),
            fontSize = 14.sp,
            color = appColors.textSecondary,
            textAlign = TextAlign.Center
        )
        CustomSpacer()
        Text(
            text = stringResource(R.string.exercise_last_session_col_reps),
            modifier = Modifier.weight(2.5f),
            fontSize = 14.sp,
            color = appColors.textSecondary,
            textAlign = TextAlign.Center
        )
        CustomSpacer()
        Text(
            text = stringResource(R.string.exercise_last_session_col_view),
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = appColors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}
