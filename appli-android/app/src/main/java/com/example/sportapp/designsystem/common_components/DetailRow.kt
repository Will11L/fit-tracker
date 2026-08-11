package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.appColors


@Composable
fun DetailRow(
    iconRes: Int,
    iconColor : Color = appColors.textTertiary,
    label: String,
    labelColor: Color = appColors.textTertiary,
    value: String,
    valueColor: Color = appColors.textTertiary
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "$label:",
            fontSize = 13.sp,
            color = labelColor
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = valueColor,
            fontWeight = FontWeight.Medium
        )
    }
}
