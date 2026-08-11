package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium

/**
 * Ligne d'état vide d'une liste : texte italique sur fond [bgRecessed], avec
 * une icône optionnelle. Canonique partagé — remplace les ex-doublons partiels
 * `RoutineTaskEmptyRow` (avec icône) et `SessionEmptyPhaseRow` (sans) (R8).
 * Quand [iconRes] est fourni : icône + texte centrés ; sinon : texte aligné à
 * gauche avec un padding horizontal.
 */
@Composable
fun EmptyListRow(
    text: String,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    backgroundColor: Color = appColors.bgRecessed,
    contentColor: Color = blueMedium,
    fontSize: TextUnit = 14.sp,
    verticalPadding: Dp = 4.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(backgroundColor, shape = MaterialTheme.shapes.small)
                .then(if (iconRes == null) Modifier.padding(horizontal = 12.dp) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (iconRes != null) Arrangement.Center else Arrangement.Start
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic
            )
        }
    }
}
