package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.*

/**
 * Cellule de résumé : icône tintée + (valeur / label) sur fond [bgRecessed].
 * Canonique partagé — remplace les ex-doublons `SessionSummaryItem`,
 * `PlannedWorkoutSummaryItem` (R1) et `CalendarSummaryItem` (R10).
 *
 * [compact] = variante resserrée (icône 24dp, texte 13sp, lineHeight serré,
 * 1 ligne + ellipsis) utilisée quand la rangée a 3+ cellules (ex. calendrier) ;
 * sinon variante standard (icône 36dp, texte 14sp).
 */
@Composable
fun SummaryItem(
    icon: Int,
    value: String,
    label: String,
    iconTint: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    // includeFontPadding=false : retire le padding vertical hérité d'Android
    // pour coller value/label en mode compact (cf. ex-CalendarSummaryItem).
    val compactTextStyle = LocalTextStyle.current.copy(
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
    Row(
        modifier = modifier
            .background(appColors.bgRecessed, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = if (compact) 10.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (compact) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Box(modifier = Modifier.weight(0.4f), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(if (compact) 8.dp else 5.dp))

        Column(
            modifier = if (compact) Modifier.weight(1f, fill = false)
                       else Modifier.weight(0.5f).padding(start = 8.dp)
        ) {
            Text(
                text = value,
                color = appColors.textPrimary,
                fontSize = if (compact) 13.sp else 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = if (compact) 20.sp else TextUnit.Unspecified,
                maxLines = if (compact) 1 else Int.MAX_VALUE,
                overflow = if (compact) TextOverflow.Ellipsis else TextOverflow.Clip,
                style = if (compact) compactTextStyle else LocalTextStyle.current,
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = appColors.textTertiary,
                lineHeight = if (compact) 20.sp else TextUnit.Unspecified,
                maxLines = if (compact) 1 else Int.MAX_VALUE,
                overflow = if (compact) TextOverflow.Ellipsis else TextOverflow.Clip,
                style = if (compact) compactTextStyle else LocalTextStyle.current,
            )
        }
    }
}
