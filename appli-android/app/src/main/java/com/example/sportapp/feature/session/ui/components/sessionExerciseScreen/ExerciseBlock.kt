package com.example.sportapp.feature.session.ui.components.sessionExerciseScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sportapp.core.data.model.ActualWorkoutSet
import com.example.sportapp.feature.onboarding.data.WeightUnit
import com.example.sportapp.designsystem.common_components.SetRow

/**
 * Bloc de saisie des sets d'un exercice (cf. frame Figma `ExerciseBlock`,
 * page « 4 · Organisms ») : en-tête de tableau + liste des [SetRow] avec leurs
 * actions par set (édition reps/poids, statut, suppression, note).
 *
 * Extrait de [com.example.sportapp.feature.session.ui.SessionExerciseScreen] sans
 * changement fonctionnel ni visuel. Le nombre de sets par exercice est borné
 * (quelques unités), un [Column] non-lazy est donc suffisant.
 */
@Composable
fun ExerciseBlock(
    sets: List<ActualWorkoutSet>,
    targetRepsRange: IntRange,
    weightUnit: WeightUnit,
    onIndexClick: (ActualWorkoutSet) -> Unit,
    onEditRepsClick: (ActualWorkoutSet) -> Unit,
    onEditWeightClick: (ActualWorkoutSet) -> Unit,
    onDeleteClick: (ActualWorkoutSet) -> Unit,
    onAddNoteClick: (ActualWorkoutSet) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SetTableHeader()

        Spacer(modifier = Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sets.forEach { set ->
                SetRow(
                    set = set,
                    targetRepsRange = targetRepsRange,
                    weightUnit = weightUnit,
                    onIndexClick = { onIndexClick(set) },
                    onEditRepsClick = { onEditRepsClick(set) },
                    onEditWeightClick = { onEditWeightClick(set) },
                    onDeleteClick = { onDeleteClick(set) },
                    onAddNoteClick = { onAddNoteClick(set) }
                )
            }
        }
    }
}

fun parseRepsRange(reps: String?): IntRange? {
    val s = reps?.trim() ?: return null

    return when {
        s.endsWith("+") -> {
            val min = s.removeSuffix("+").trim().toIntOrNull() ?: return null
            min..100
        }
        s.contains("-") -> {
            val parts = s.split("-").map { it.trim().toIntOrNull() }
            val a = parts.getOrNull(0) ?: return null
            val b = parts.getOrNull(1) ?: return null
            a..b
        }
        else -> {
            val v = s.toIntOrNull() ?: return null
            v..v
        }
    }
}
