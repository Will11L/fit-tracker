package com.example.sportapp.feature.routines.ui.components.routineTasksScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun SummaryInlineDivider() {
    Spacer(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .height(18.dp)
            .width(1.dp)
            .background(appColors.divider)
    )
}
