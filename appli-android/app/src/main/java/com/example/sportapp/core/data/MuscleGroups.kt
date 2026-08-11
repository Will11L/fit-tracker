package com.example.sportapp.core.data

/**
 * Liste canonique des 17 muscle groups (niveau intermediaire de la hierarchie
 * anatomique 3 niveaux : zone > muscle_group > muscle precis).
 *
 * Source de verite = serveur `app/seed_database.py:_STARTER_MUSCLE_SPECS`
 * (refactor 2026-05-08, cf. CLAUDE.md historique). Ordre conserve pour palette
 * de couleurs deterministe et legende stable au rendu chart.
 *
 * Mapping muscle precis -> group / zone n'est PLUS fait cote client (avant
 * 2026-05-08, MuscleGroups.MAPPING faisait le mapping name->zone). Desormais
 * la DB Room contient les 2 champs `muscle_group` + `zone` directement, et le
 * DAO les lit via JOIN. Cf. queries `observeAllGroups*` + `observeAllZones*`.
 */
object MuscleGroups {

    /** Ordre canonique pour palette stable + tri legende. */
    val ALL: List<String> = listOf(
        // === Chest zone ===
        "Pecs",
        // === Back zone ===
        "Lats",
        "Rhomboids",
        "Erector Spinae",
        "Traps",
        // === Shoulders zone ===
        "Delts",
        // === Arms zone ===
        "Biceps",
        "Triceps",
        "Brachialis",
        "Forearms",
        // === Legs zone ===
        "Quads",
        "Hamstrings",
        "Glutes",
        "Calves",
        "Adductors",
        // === Core zone ===
        "Abs",
        "Obliques",
    )
}
