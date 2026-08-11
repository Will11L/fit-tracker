package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.appColors

/**
 * Bloc « chart titré » réutilisable : un [TitledDivider] suivi soit d'un état vide
 * (Box 120dp centré avec [emptyText]), soit du [chart] et d'une [legend] optionnelle.
 *
 * Slot-based : le contenu du graphe est injecté via le slot [chart] (et la légende via
 * le slot optionnel [legend]) pour rester agnostique du type de chart utilisé par l'appelant.
 */
@Composable
fun StatsChartCard(
    title: String,
    isEmpty: Boolean,
    emptyText: String,
    modifier: Modifier = Modifier,
    legend: (@Composable () -> Unit)? = null,
    chart: @Composable () -> Unit,
) {
    // Titre optionnel : vide = pas de divider (ex. section Stats de ExerciseScreen,
    // déjà coiffée par le divider "Stats").
    if (title.isNotEmpty()) TitledDivider(title)
    if (isEmpty) {
        // État vide style chart Objectifs : fond bgRecessed + bordure bleue arrondie
        // en retrait (padding interne) + texte bleu centré.
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(appColors.bgRecessed)
                .padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.5.dp, appColors.primaryAction, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(emptyText, color = appColors.primaryAction, fontSize = 12.sp)
            }
        }
    } else {
        chart()
        if (legend != null) {
            Spacer(Modifier.height(8.dp))
            legend()
        }
    }
}
