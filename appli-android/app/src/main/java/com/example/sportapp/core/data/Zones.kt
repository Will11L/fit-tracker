package com.example.sportapp.core.data

/**
 * Liste canonique des 6 zones du corps (niveau haut de la hierarchie
 * anatomique 3 niveaux : zone > muscle_group > muscle precis).
 *
 * Source de verite = serveur `app/seed_database.py:_STARTER_MUSCLE_SPECS`.
 * Ordre conserve pour palette de couleurs deterministe et legende stable.
 *
 * Avant le refactor 2026-05-08, ce concept etait represente par
 * `MuscleGroups.ALL` (mal nomme — c'etait deja les zones). Depuis le refactor,
 * `MuscleGroups` represente vraiment les groups intermediaires (Pecs, Lats,
 * Triceps...) et `Zones` les regions macros.
 */
object Zones {

    /** Ordre canonique pour palette stable + tri legende. */
    val ALL: List<String> = listOf(
        "Chest",
        "Back",
        "Shoulders",
        "Arms",
        "Legs",
        "Core",
    )
}
