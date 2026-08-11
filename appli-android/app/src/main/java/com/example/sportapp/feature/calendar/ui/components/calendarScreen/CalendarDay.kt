package com.example.sportapp.feature.calendar.ui.components.calendarScreen

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.StatusIcon
import com.example.sportapp.designsystem.theme.*

@Composable
fun CalendarDay(
    dayNumber: Int,
    cellSize: Dp,
    isToday: Boolean = false,
    isRestDay: Boolean = false,
    isCompleted: Boolean = false,
    isSynced: Boolean = false,
    showCloudOff: Boolean = false,
    isSkipped: Boolean = false,
    isMissedPlanned: Boolean = false, // ✅ "-" rouge
    isInProgress: Boolean = false,    // ✅ arrow explicite
    onClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .size(cellSize)
            .clip(MaterialTheme.shapes.small)
            .border(
                width = 1.dp,
                color = if (isToday) appColors.primaryAction else Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .height(18.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // cloud
                if (isSynced) {
                    StatusIcon(R.drawable.ic_cloud_done, appColors.primaryAction)
                } else if (showCloudOff) {
                    StatusIcon(R.drawable.ic_cloud_off, yellowMedium)
                }

                // statut day
                when {
                    isRestDay -> StatusIcon(R.drawable.ic_rounded_bedtime, blueMedium)

                    isMissedPlanned -> {
                        // ✅ à créer/choisir : un drawable de "minus"
                        // ex: ic_rounded_remove / ic_minus / etc.
                        StatusIcon(R.drawable.ic_check_indeterminate_small, darkOrange)
                    }

                    isCompleted -> StatusIcon(R.drawable.ic_rounded_check, mediumGreen)
                    isSkipped -> StatusIcon(R.drawable.ic_rounded_close, redMedium)
                    isInProgress -> StatusIcon(R.drawable.ic_arrow_progress, orangeMedium)

                    else -> {
                        // fallback neutre (optionnel)
                        StatusIcon(R.drawable.ic_arrow_progress, darkOrange)
                    }
                }
            }
        }

        Text(
            text = dayNumber.toString(),
            fontSize = 16.sp,
            color = appColors.textTertiary,
            fontWeight = FontWeight.Medium
        )
    }
}
