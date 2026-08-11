package com.example.sportapp.feature.session.ui.components.sessionExerciseScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun ExerciseInstructionsSection(
    instructions: List<String>,
    modifier: Modifier = Modifier
) {
    val steps = instructions.map { it.trim() }.filter { it.isNotBlank() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(appColors.bgRecessed, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        if (steps.isEmpty()) {
            Text(
                text = "No instructions.",
                color = appColors.textTertiary,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                steps.forEachIndexed { index, step ->
                    InstructionRow(stepNumber = index + 1, text = step)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
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
