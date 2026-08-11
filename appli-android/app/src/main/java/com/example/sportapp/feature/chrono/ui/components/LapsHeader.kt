package com.example.sportapp.feature.chrono.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium

@Composable
fun LapsHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.chrono_laps_col_num),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = appColors.primaryAction
        )
        Text(
            text = stringResource(R.string.chrono_laps_col_delta),
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.labelLarge,
            color = blueMedium,
            textAlign = TextAlign.End
        )
        Text(
            text = stringResource(R.string.chrono_laps_col_time),
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.labelLarge,
            color = appColors.textTertiary,
            textAlign = TextAlign.End
        )
    }
}
