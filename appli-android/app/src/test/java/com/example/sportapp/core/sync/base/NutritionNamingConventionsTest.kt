package com.example.sportapp.core.sync.base

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Nutrition A1 (2026-06-17) — verrouille le changement de `camelToSnake` introduit
 * par la couche données nutrition : la gestion des frontières chiffres
 * (`kcalPer100g` -> `kcal_per_100g`, `vitaminB12Per100g` -> `vitamin_b12_per_100g`).
 *
 * Pourquoi c'est load-bearing : la data grid de Sync Settings dérive le nom de
 * colonne SQL d'une row depuis le NOM DE FIELD Kotlin via [NamingConventions.fieldToSqlColumn]
 * (annotations Room invisibles en reflection, cf. doc de la classe). Si la conversion
 * produit `kcal_per100g` au lieu de `kcal_per_100g`, la requête `ORDER BY` / `WHERE`
 * générée référence une colonne inexistante -> crash runtime sur la grille nutrition.
 *
 * Le test compare donc la sortie de la conversion aux noms `@ColumnInfo` réels des
 * modèles Food / MealEntry, et vérifie l'absence de régression sur les colonnes
 * pré-nutrition (aucune n'a de chiffre, donc le nouveau branchement ne doit rien changer).
 */
class NutritionNamingConventionsTest {

    @Test
    fun `camelToSnake handles digit boundaries on nutrition macro fields`() {
        // Frontières chiffres : underscore avant un chiffre précédé d'une lettre minuscule.
        assertEquals("kcal_per_100g", NamingConventions.camelToSnake("kcalPer100g"))
        assertEquals("protein_per_100g", NamingConventions.camelToSnake("proteinPer100g"))
        assertEquals("carbs_per_100g", NamingConventions.camelToSnake("carbsPer100g"))
        assertEquals("fat_per_100g", NamingConventions.camelToSnake("fatPer100g"))
        assertEquals("fiber_per_100g", NamingConventions.camelToSnake("fiberPer100g"))
        assertEquals("sat_fat_per_100g", NamingConventions.camelToSnake("satFatPer100g"))
        assertEquals("salt_per_100g", NamingConventions.camelToSnake("saltPer100g"))
    }

    @Test
    fun `camelToSnake handles vitamin fields mixing acronyms and digits`() {
        assertEquals("vitamin_c_per_100g", NamingConventions.camelToSnake("vitaminCPer100g"))
        assertEquals("vitamin_d_per_100g", NamingConventions.camelToSnake("vitaminDPer100g"))
        assertEquals("vitamin_a_per_100g", NamingConventions.camelToSnake("vitaminAPer100g"))
        // Cas le plus piégeux : lettre majuscule + 2 chiffres au milieu d'un mot.
        assertEquals("vitamin_b12_per_100g", NamingConventions.camelToSnake("vitaminB12Per100g"))
        assertEquals("iron_per_100g", NamingConventions.camelToSnake("ironPer100g"))
        assertEquals("magnesium_per_100g", NamingConventions.camelToSnake("magnesiumPer100g"))
    }

    @Test
    fun `camelToSnake leaves pre-nutrition columns untouched (no regression)`() {
        // Aucune colonne pré-nutrition n'a de chiffre -> la conversion doit rester identique.
        assertEquals("user_id", NamingConventions.camelToSnake("userId"))
        assertEquals("muscle_group", NamingConventions.camelToSnake("muscleGroup"))
        assertEquals("updated_at", NamingConventions.camelToSnake("updatedAt"))
        assertEquals("is_favorite", NamingConventions.camelToSnake("isFavorite"))
        assertEquals("week_iso", NamingConventions.camelToSnake("weekISO"))
        assertEquals("uuid", NamingConventions.camelToSnake("UUID"))
        assertEquals(
            "actual_workout_exercise_uuid",
            NamingConventions.camelToSnake("actualWorkoutExerciseUUID"),
        )
    }

    @Test
    fun `fieldToSqlColumn keeps the camelCase exception and otherwise snakes`() {
        // pendingDeletion = flag sync local-only (politique 17) -> jamais converti.
        assertEquals("pendingDeletion", NamingConventions.fieldToSqlColumn("pendingDeletion"))
        // Tout le reste passe par camelToSnake.
        assertEquals("kcal_per_100g", NamingConventions.fieldToSqlColumn("kcalPer100g"))
        assertEquals("food_group", NamingConventions.fieldToSqlColumn("foodGroup"))
        assertEquals("source_ref", NamingConventions.fieldToSqlColumn("sourceRef"))
        assertEquals("synced", NamingConventions.fieldToSqlColumn("synced"))
    }
}
