package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sportapp.designsystem.theme.GrayBlue
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.firstBlue

/** Ligne du tooltip : intitulé de série (null = valeur seule alignée à droite),
 *  valeur formatée (+ unité) et couleur de la série (intitulé ET valeur). */
data class ChartTooltipRow(
    val name: String?,
    val valueText: String,
    val color: Color,
)

/**
 * Tooltip de chart au TAP — miroir du tooltip de survol web (`themedAxisTooltip`) :
 * cadre bgRecessed à bord firstBlue et coins arrondis, EN-TÊTE = [label] (date/heure
 * du slot) en gris-bleu entre deux lignes, puis UNE LIGNE PAR SÉRIE ([rows]) —
 * intitulé à gauche, valeur (+ unité) en gras à droite, les deux à la couleur de la
 * série (ex. 4 phases de sommeil). Largeur ADAPTATIVE au contenu (min 160 dp, comme
 * le min-width web). Affiché en overlay (le rendu ne décale pas le chart).
 */
@Composable
fun ChartTapTooltip(
    label: String,
    rows: List<ChartTooltipRow>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(min = 160.dp)
            .width(IntrinsicSize.Max)
            .clip(RoundedCornerShape(8.dp))
            .background(appColors.bgRecessed)
            .border(1.dp, firstBlue, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (label.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(GrayBlue.copy(alpha = 0.6f)),
                )
                Text(
                    text = label,
                    color = GrayBlue,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(GrayBlue.copy(alpha = 0.6f)),
                )
            }
        }
        rows.forEach { row ->
            if (row.name.isNullOrBlank()) {
                Text(
                    text = row.valueText,
                    color = row.color,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.name,
                        color = row.color,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.weight(1f).widthIn(min = 14.dp))
                    Text(
                        text = row.valueText,
                        color = row.color,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
