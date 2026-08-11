package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.WheelPicker
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun HmsWheelPicker(
    hours: Int,
    minutes: Int,
    seconds: Int,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit,
    onSecondsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Affichage "00:00:00"
    val preview = "%02d:%02d:%02d".format(hours, minutes, seconds)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(appColors.bgRecessed, shape = MaterialTheme.shapes.small)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = preview,
                style = MaterialTheme.typography.titleLarge,
                color = appColors.accentText
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WheelColumn(
                label = stringResource(R.string.hms_hours_label),
                range = 0..23,
                selected = hours,
                onSelected = onHoursChange,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = ":",
                color = appColors.textPrimary.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 27.dp)
            )

            WheelColumn(
                label = stringResource(R.string.hms_minutes_label),
                range = 0..59,
                selected = minutes,
                onSelected = onMinutesChange,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = ":",
                color = appColors.textPrimary.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 27.dp)
            )

            WheelColumn(
                label = stringResource(R.string.hms_seconds_label),
                range = 0..59,
                selected = seconds,
                onSelected = onSecondsChange,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = stringResource(R.string.hms_swipe_hint),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = appColors.textPrimary.copy(alpha = 0.55f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
    }
}

@Composable
private fun WheelColumn(
    label: String,
    range: IntRange,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = appColors.textPrimary.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(8.dp))

        WheelPicker(
            range = range,
            selected = selected,
            onSelected = onSelected,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
