package com.example.sportapp.core.utils

import com.example.sportapp.core.data.model.MuscleGoal

/**
 * Parse le `target` d'un MuscleGoal (ex. "10", "10-15", "12+") et retourne
 * la valeur minimale a atteindre.
 *
 * - "10+"   -> 10 (min, ouvert vers le haut)
 * - "10-15" -> 10 (min de l'intervalle)
 * - "12"    -> 12 (valeur exacte)
 * - parsing fail -> Int.MAX_VALUE (jamais atteint)
 */
fun parseTargetMinimum(target: String): Int {
    return when {
        target.endsWith("+") -> target.removeSuffix("+").toIntOrNull() ?: Int.MAX_VALUE
        "-" in target -> target.split("-").firstOrNull()?.toIntOrNull() ?: Int.MAX_VALUE
        else -> target.toIntOrNull() ?: Int.MAX_VALUE
    }
}

/**
 * Calcule le progress global d'une liste de MuscleGoal (0..1).
 * Un goal `status=DONE` est compte comme atteint a 100% (effectiveDone = targetMin).
 * Sinon, on cumule done / target sur l'ensemble.
 */
fun calculateGoalProgress(muscles: List<MuscleGoal>): Float {
    if (muscles.isEmpty()) return 1f

    var totalDone = 0
    var totalTarget = 0

    for (goal in muscles) {
        val targetMin = parseTargetMinimum(goal.target)
        val effectiveDone =
            if (goal.status.trim().equals("DONE", ignoreCase = true)) targetMin else goal.done

        totalDone += effectiveDone
        totalTarget += targetMin
    }

    return if (totalTarget > 0) totalDone.toFloat() / totalTarget else 1f
}
