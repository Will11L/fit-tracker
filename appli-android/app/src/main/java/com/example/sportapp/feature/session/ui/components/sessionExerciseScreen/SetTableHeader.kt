package com.example.sportapp.feature.session.ui.components.sessionExerciseScreen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.CustomSpacer
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun SetTableHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.session_exercise_table_num), modifier = Modifier.weight(1.6f), fontSize = 14.sp, color = appColors.textSecondary, textAlign = TextAlign.Center)
        Text(stringResource(R.string.session_exercise_table_reps), modifier = Modifier.weight(2f), fontSize = 14.sp, color = appColors.textSecondary, textAlign = TextAlign.Center)
        CustomSpacer()
        Text(stringResource(R.string.session_exercise_table_weight), modifier = Modifier.weight(2f), fontSize = 14.sp, color = appColors.textSecondary, textAlign = TextAlign.Center)
        CustomSpacer()
        Text(stringResource(R.string.session_exercise_table_trend), modifier = Modifier.weight(1.6f), fontSize = 14.sp, color = appColors.textSecondary, textAlign = TextAlign.Center)
        Text(stringResource(R.string.session_exercise_table_done), modifier = Modifier.weight(1.6f), fontSize = 14.sp, color = appColors.textSecondary, textAlign = TextAlign.Center)
        CustomSpacer()
        Text(stringResource(R.string.session_exercise_table_del), modifier = Modifier.weight(1.6f), fontSize = 14.sp, color = appColors.textSecondary, textAlign = TextAlign.Center)
        CustomSpacer()
        Text(stringResource(R.string.session_exercise_table_notes), modifier = Modifier.weight(1.6f), fontSize = 14.sp, color = appColors.textSecondary, textAlign = TextAlign.Center)
    }
}
