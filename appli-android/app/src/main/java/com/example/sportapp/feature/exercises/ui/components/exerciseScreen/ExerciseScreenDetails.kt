package com.example.sportapp.feature.exercises.ui.components.exerciseScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Exercise
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.core.utils.formatRestTime


@Composable
fun ExerciseScreenDetails(
    exercise: Exercise,
    equipment: List<String>,
    onEditStatsClick: () -> Unit,
    onEditInfoClick: () -> Unit,
    onEditInstructionsClick: () -> Unit
) {
    // 🔹 SECTION 1 — Stats
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 120.dp)
                .background(appColors.bgRecessed, shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.Start
            ) {
                DetailText("Sets", exercise.recommendedSets?.toString() ?: "N/A")
                DetailText("Reps", exercise.recommendedReps ?: "N/A")
                DetailText("Rest Time", formatRestTime(exercise.restTimeSeconds))
            }
            // (Les images de l'exercice sont affichées via le bouton Delavier Method,
            //  plus de vignettes muscles ici — 2026-06-11.)
        }

        // ✏️ bouton edit stats
        ActionIconButton(
            iconRes = R.drawable.ic_rounded_edit,
            tint = appColors.textTertiary,
            hasBackground = false,
            modifier = Modifier.align(Alignment.TopEnd),
            onClick = onEditStatsClick
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // 🔹 SECTION 2 — Equipment + Description
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(appColors.bgRecessed, shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.Start
        ) {
            DetailTextWithIndentation(
                label = "Equipment",
                value = equipment.joinToString(", ").ifEmpty { "No equipment specified" }
            )
            Spacer(modifier = Modifier.height(8.dp))
            DetailTextWithIndentation(
                label = "Description",
                value = exercise.description ?: "No description available"
            )
        }

        // ✏️ bouton edit info
        ActionIconButton(
            iconRes = R.drawable.ic_rounded_edit,
            tint = appColors.textTertiary,
            hasBackground = false,
            modifier = Modifier.align(Alignment.TopEnd),
            onClick = onEditInfoClick
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // 🔹 SECTION 3 — Instructions
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(appColors.bgRecessed, shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.Start
        ) {
            InstructionsSection(exercise.instructions)
        }

        // ✏️ bouton edit instructions
        ActionIconButton(
            iconRes = R.drawable.ic_rounded_edit,
            tint = appColors.textTertiary,
            hasBackground = false,
            modifier = Modifier.align(Alignment.TopEnd),
            onClick = onEditInstructionsClick
        )
    }
}


@Composable
private fun DetailText(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            color = appColors.primaryAction,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            color = appColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun DetailTextWithIndentation(label: String, value: String) {
    Text(
        text = buildAnnotatedString {
            withStyle(style = SpanStyle(color = appColors.primaryAction)) {
                append("$label: ")
            }
            append(" ")
            withStyle(style = SpanStyle(color = appColors.textPrimary, fontWeight = FontWeight.Normal)) {
                append(value)
            }
        },
        fontSize = 13.sp,
        lineHeight = 20.sp,
        modifier = Modifier.fillMaxWidth(),
        softWrap = true
    )
}

@Composable
private fun InstructionsSection(instructions: List<String>?) {
    val steps = instructions?.filter { it.isNotBlank() }.orEmpty()
    if (steps.isEmpty()) return

    Text(
        text = "Instructions",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = appColors.textTertiary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal
    )

    Spacer(modifier = Modifier.height(16.dp))

    steps.forEachIndexed { index, step ->
        InstructionRow(stepNumber = index + 1, text = step.trim())
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun InstructionRow(stepNumber: Int, text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "• Step $stepNumber: ",
            color = appColors.primaryAction,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            color = appColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
            softWrap = true
        )
    }
}
