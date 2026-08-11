package com.example.sportapp.feature.routines.ui.components.routineTasksScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.theme.*

@Composable
fun DateNavBar(
    dateIso: String,            // "yyyy-MM-dd"
    isToday: Boolean,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onClickDate: () -> Unit = {}
) {
    val dateColor = if (isToday) appColors.primaryAction else appColors.textTertiary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent, shape = MaterialTheme.shapes.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // ⬅️
        ActionIconButton(
            iconRes = R.drawable.ic_rounded_keyboard_arrow_left,
            tint = appColors.textPrimary,
            customBackgroundColor = appColors.bgButton,
            onClick = onPrevDay
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Date
        Text(
            text = dateIso,
            color = dateColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(appColors.bgRecessed, shape = MaterialTheme.shapes.small)
                .clickable(onClick = onClickDate)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // ➡️ (option: grisé/désactivé si today)
        ActionIconButton(
            iconRes = R.drawable.ic_keyboard_arrow_right,
            tint = if (isToday) appColors.divider else appColors.textPrimary,
            customBackgroundColor = appColors.bgButton,
            clickable = !isToday,
            onClick = onNextDay
        )
    }
}
