package com.example.sportapp.feature.calendar.ui.components.calendarScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.AppBottomSheet
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.*

data class LegendEntry(
    val iconRes: Int,
    val label: String,
    val color: Color
)

/**
 * Bottom sheet de légende du calendrier (icônes & couleurs des états des
 * jours). Extrait de [MonthViewProgressBar] (B6).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarLegendBottomSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()

    AppBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentColor = appColors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            TitledDivider(title = stringResource(R.string.calendar_legend_title))
            Spacer(modifier = Modifier.height(12.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                // 3 colonnes si on a la place, sinon 2.
                val columns = if (maxWidth >= 360.dp) 3 else 2

                val legendItems = listOf(
                    LegendEntry(R.drawable.ic_rounded_check, stringResource(R.string.calendar_legend_completed), mediumGreen),
                    LegendEntry(R.drawable.ic_rounded_bedtime, stringResource(R.string.calendar_legend_rest_day), blueMedium),
                    LegendEntry(R.drawable.ic_rounded_close, stringResource(R.string.calendar_legend_uncompleted), redMedium),
                    LegendEntry(R.drawable.ic_arrow_progress, stringResource(R.string.calendar_legend_planned), orangeMedium),
                    LegendEntry(R.drawable.ic_cloud_done, stringResource(R.string.calendar_legend_synced), appColors.primaryAction),
                    LegendEntry(R.drawable.ic_check_indeterminate_small, stringResource(R.string.calendar_legend_missed), redMedium)
                )

                LegendGrid(items = legendItems, columns = columns)
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun LegendGrid(
    items: List<LegendEntry>,
    columns: Int
) {
    val rows = (items.size + columns - 1) / columns

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (r in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (c in 0 until columns) {
                    val index = r * columns + c
                    if (index < items.size) {
                        LegendCell(
                            entry = items[index],
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendCell(
    entry: LegendEntry,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(appColors.bgRecessed)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(entry.iconRes),
            contentDescription = entry.label,
            tint = entry.color,
            modifier = Modifier.size(18.dp)
        )

        Text(
            text = entry.label,
            color = appColors.textTertiary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1
        )
    }
}
