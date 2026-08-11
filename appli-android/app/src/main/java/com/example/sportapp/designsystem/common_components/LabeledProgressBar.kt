package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.*

@Composable
fun LabeledProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    showPercent: Boolean = true,
    troughColor: Color = appColors.bgRecessed,
    fillColor: Color? = null,
    rightContent: @Composable (() -> Unit)? = null
) {
    val progressPercent = (progress * 100).toInt()

    // fillColor override (ex. dégradé d'hydratation) sinon seuils R/O/V par défaut.
    val color = fillColor ?: progressColor(progress)

    Row(
        modifier = modifier
            .fillMaxWidth()
            //.heightIn(max = 54.dp) // 👈 limite la hauteur du composant
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Progress bar (R18 : delegue au primitif partage)
        ProgressBarPrimitive(
            progress = progress,
            color = color,
            modifier = Modifier.weight(1f),
            troughColor = troughColor,
        )

        if (showPercent) {
            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .widthIn(min = 48.dp)
                    .height(40.dp)
                    .background(Color.Transparent, shape = MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$progressPercent%",
                    color = color,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (rightContent != null) {
            Spacer(modifier = Modifier.width(12.dp))
            rightContent()
        }
    }
}

/**
 * Couleur d'une barre de progression selon son avancement, par seuils.
 * Helper canonique partagé — remplace les copies locales de
 * `RoutineTasksProgressBar` et `PlannedDayProgressBar` (R6).
 */
@Composable
fun progressColor(value: Float): Color = when {
    value >= 1f -> appColors.primaryAction
    value >= 0.75f -> mediumGreen
    value >= 0.5f -> lightGreen
    value >= 0.2f -> orangeMedium
    else -> redMedium
}
