package com.example.sportapp.feature.goals.ui.components.goalsTabContent

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.core.data.model.MuscleGoal
import com.example.sportapp.feature.goals.ui.getMuscleName
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun ZoneGoalsCard(
    zoneName: String,
    color: Color,
    goals: List<MuscleGoal>,
    allMuscles: List<Muscle>,
    onMuscleClick: (MuscleGoal) -> Unit,
    onTargetClick: (MuscleGoal) -> Unit,
    onPriorityChanged: (uuid: String, newPriority: String) -> Unit,
    // User feedback runtime 2026-05-09 : header table dupliquee dans chaque
    // card prenait trop de place. Quand l'appelant l'affiche une seule fois
    // au-dessus de la liste de cards, passer showTableHeader=false.
    showTableHeader: Boolean = true,
    // User feedback 2026-05-09 (iter 2) : titre redondant avec le label X
    // axis du chart footer (memes noms group/zone). Quand false, le cadre
    // colore et la palette restent suffisants comme reperes visuels.
    showTitle: Boolean = true,
) {
    val tintedBg = Brush.verticalGradient(      // couleur degrade
        colorStops = arrayOf(
            0.0f to color.copy(alpha = 0.12f),   // haut PLUS visible
            0.35f to color.copy(alpha = 0.06f)  // bas PLUS discret
        )
    )

    val tintedBg2 = color.copy(alpha = 0.08f)   // couleur unie (2026-06-04 : fond moins visible, aligne sur Figma ZoneGoalsCard op 0.08)

    val borderBrush = Brush.verticalGradient(
        listOf(
            color.copy(alpha = 0.35f),
            Color.Transparent
        )
    )

    val borderColor2 = color.copy(alpha = 0.35f)

    // Box wrapper pour heberger le label flottant style OutlinedTextField
    // (user feedback runtime 2026-05-09 iter 5+6). Le top padding fait de
    // la place au label : label height ~20dp, centre = 10dp, on prevoit
    // 12dp de padding (10dp pour la moitie superieure du label + 2dp pour
    // pas toucher la card precedente).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (showTitle) 14.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .border(
                    width = 1.dp,
                    color = borderColor2,
                    shape = MaterialTheme.shapes.medium
                )
                .background(tintedBg2)
                // top padding plus large quand showTitle pour que la 1ere
                // GoalRow ne chevauche pas la moitie inferieure du label
                // (user feedback runtime 2026-05-09 iter 6).
                .padding(
                    top = if (showTitle) 16.dp else 6.dp,
                    start = 6.dp,
                    end = 6.dp,
                    bottom = 6.dp,
                )
        ) {
            if (showTableHeader) {
                Row(Modifier.fillMaxWidth()) {
                    TableHeaderCell("Muscle", Modifier.weight(4f))
                    TableHeaderCell("Priority", Modifier.weight(2f))
                    TableHeaderCell("Done", Modifier.weight(2f))
                    Spacer(modifier = Modifier.weight(0.3f))
                    TableHeaderCell("ToDo", Modifier.weight(2f))
                    Spacer(modifier = Modifier.weight(0.3f))
                    TableHeaderCell("Status", Modifier.weight(2f))
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            goals.forEach { goal ->
                GoalRow(
                    muscleGoal = goal,
                    muscleName = getMuscleName(goal.muscleUUID, allMuscles),
                    onMuscleClick = onMuscleClick,
                    onTargetClick = onTargetClick,
                    onPriorityChanged = { newPriority ->
                        onPriorityChanged(goal.uuid, newPriority)
                    }
                )
            }
        }

        // Label flottant style OutlinedTextField : centre VISUEL du texte
        // (pas le centre geometrique du Box) align sur la bordure top de
        // la Column (user feedback iter 6+7). Le centre geometrique du Box
        // n'est pas le centre visuel du texte a cause du line-height : le
        // texte apparait plus bas que le centre du widget. Compensation :
        //  - contentAlignment = Center : force le Text au centre du Box
        //    (vs TopStart par defaut) pour limiter le decalage interne.
        //  - offset y -10 -> -12 : remonte de 2dp supplementaires pour que
        //    le centre visuel des glyphes ("Lats" cap height middle) tombe
        //    pile sur la bordure.
        // Corner radius identique a la card (MaterialTheme.shapes.medium).
        if (showTitle) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 12.dp, y = (-14).dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(appColors.bgScreen)
                    .border(1.dp, borderColor2, MaterialTheme.shapes.medium)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = zoneName,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                )
            }
        }
    }
}