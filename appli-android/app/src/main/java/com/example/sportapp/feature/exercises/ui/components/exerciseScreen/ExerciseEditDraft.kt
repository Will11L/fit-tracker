package com.example.sportapp.feature.exercises.ui.components.exerciseScreen

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.sportapp.core.data.model.Equipment
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.core.utils.parseRestTimeToSeconds

/**
 * Draft partagé entre les dialogs d’édition de l’exercice.
 * Il permet d’éditer par sections sans perdre l’état.
 */
data class ExerciseEditDraft(
    var name: String,
    var description: String,
    var instructionFields: SnapshotStateList<String>,

    var repsMin: Int,
    var repsMax: Int,
    var sets: Int,
    var restTimeLabel: String,

    val selectedMuscles: SnapshotStateList<Muscle>,
    val selectedEquipments: SnapshotStateList<Equipment>
)

fun buildUpdatedExercise(
    original: Exercise,
    draft: ExerciseEditDraft
): Exercise {
    val cleanedInstructions = draft.instructionFields
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .let { if (it.isEmpty()) null else it }

    val minReps = minOf(draft.repsMin, draft.repsMax)
    val maxReps = maxOf(draft.repsMin, draft.repsMax)

    return original.copy(
        // on ne touche pas au name ici (tu peux le faire si tu veux)
        description = draft.description.trim().ifEmpty { null },
        instructions = cleanedInstructions,
        recommendedReps = "$minReps-$maxReps",
        recommendedSets = draft.sets,
        restTimeSeconds = parseRestTimeToSeconds(draft.restTimeLabel)
    )
}
