package com.example.sportapp.feature.goals.ui.components.goalsTabContent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconWithTextButton
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun EmptyGoalsWeekState(
    onCopyFromLastWeek: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(appColors.bgScreen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "No goals for this week",
            color = appColors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )

        Text(
            text = "You can copy last week's goals to get started quickly.",
            color = appColors.textTertiary,
            fontSize = 13.sp
        )

        ActionIconWithTextButton(
            iconRes = R.drawable.ic_rounded_content_copy,
            text = "Copy goals from last week",
            tint = appColors.textPrimary,
            textColor = appColors.textPrimary,
            onClick = onCopyFromLastWeek,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

