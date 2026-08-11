package com.example.sportapp.feature.chrono.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sportapp.feature.chrono.utils.formatTimeWithCentiseconds
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium

@Composable
fun LapRow(
    index: Int,
    lapMillis: Long,
    totalMillis: Long
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(color = appColors.bgRecessed)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$index",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = formatTimeWithCentiseconds(lapMillis),
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            color = blueMedium
        )
        Text(
            text = formatTimeWithCentiseconds(totalMillis),
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            color = appColors.textTertiary
        )
    }
}
