package com.example.sportapp.feature.routines.ui.components.routineTasksScreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun SummaryInlineItem(
    icon: Int,
    value: String,
    label: String? = null,
    tint: Color,
    clickable: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.then(
            if (clickable && onClick != null) {
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onClick)
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            } else {
                Modifier
            }
        )
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )

        // Si value vide -> juste l'icone (e.g. cloud_done quand 0 unsynced).
        if (value.isNotBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = value,
                color = appColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!label.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    color = appColors.textTertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
