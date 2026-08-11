package com.example.sportapp.feature.exercises.ui.components.exerciseScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.projections.ActualWorkoutExerciseWithWorkoutDateAndSets
import com.example.sportapp.designsystem.theme.appColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Section « Last Sessions » de ExerciseScreen (implémentée 2026-06-11) : les 3 dernières
 * séances où l'exercice a été fait (DAO observeLast3SessionsForExercise : date séance +
 * nb de séries + total reps). Flèche → ouvre SessionExerciseScreen de l'occurrence.
 */
@Composable
fun LastSessionsSection(
    lastSessions: List<ActualWorkoutExerciseWithWorkoutDateAndSets>,
    onOpenSession: (actualWorkoutExerciseUUID: String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 📋 Table Header
        LastSessionTableHeader()

        if (lastSessions.isEmpty()) {
            // Placeholder même style que les charts vides : fond bgRecessed + bordure
            // bleue arrondie en retrait + texte bleu centré.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
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
                    Text(
                        text = stringResource(R.string.exercise_no_recent_sessions),
                        color = appColors.primaryAction,
                        fontSize = 12.sp
                    )
                }
            }
            return@Column
        }

        lastSessions.forEach { session ->
            LastSessionRow(
                date = formatWorkoutDate(session.workoutDate),
                sets = "${session.doneSetsCount}/${session.setsCount}",
                reps = "${session.doneReps}/${session.totalReps}",
                onOpenClick = { onOpenSession(session.actualWorkoutExercise.uuid) }
            )
        }
    }
}

/** "2026-06-11" → "11/06/2026" ; brut si format inattendu. */
private fun formatWorkoutDate(dateIso: String): String =
    runCatching {
        LocalDate.parse(dateIso.take(10))
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault()))
    }.getOrDefault(dateIso)
