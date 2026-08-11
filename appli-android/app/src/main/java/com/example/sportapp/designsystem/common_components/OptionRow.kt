package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.appColors


@Composable
fun OptionRow(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    hasBackground: Boolean = true,
    customColor: Color = appColors.bgButton
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.bgRecessed, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = appColors.textPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )

        ActionIconButton(
            iconRes = iconRes,
            onClick = onClick,
            tint = appColors.textPrimary,
            hasBackground = hasBackground,
            customBackgroundColor = customColor
        )
    }
}
