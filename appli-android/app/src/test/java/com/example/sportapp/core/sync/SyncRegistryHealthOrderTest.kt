package com.example.sportapp.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.local.AppDatabase
import com.example.sportapp.core.data.model.HealthGoal
import com.example.sportapp.core.data.model.HealthMetric
import com.example.sportapp.core.data.model.HealthStepCount
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Santé / Health Connect V1 (2026-06-17) — verrouille l'enregistrement des 3 entités
 * santé dans le [SyncRegistry].
 *
 * `SyncEngine.pushAll` itère sur `registry.all` (push) et `registry.reversed` (delete) :
 * une entité absente du registry ne serait ni poussée ni pull-mergée. Les 3 entités
 * santé n'ont aucune FK inter-santé (FK vers User seul), donc pas de contrainte d'ordre
 * relatif — on vérifie juste qu'elles sont bien enregistrées et présentes des deux côtés.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SyncRegistryHealthOrderTest {

    private lateinit var db: AppDatabase
    private lateinit var registry: SyncRegistry

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        registry = SyncRegistry(
            actualWorkoutDao = db.actualWorkoutDao(),
            actualWorkoutExerciseDao = db.actualWorkoutExerciseDao(),
            actualWorkoutSetDao = db.actualWorkoutSetDao(),
            availableEquipmentDao = db.availableEquipmentDao(),
            cycleWorkoutDao = db.cycleWorkoutDao(),
            equipmentDao = db.equipmentDao(),
            exerciseDao = db.exerciseDao(),
            exerciseEquipmentDao = db.exerciseEquipmentDao(),
            exerciseMuscleDao = db.exerciseMuscleDao(),
            muscleDao = db.muscleDao(),
            muscleGoalDao = db.muscleGoalDao(),
            notificationDao = db.notificationDao(),
            plannedWorkoutDao = db.plannedWorkoutDao(),
            plannedWorkoutExerciseDao = db.plannedWorkoutExerciseDao(),
            quoteDao = db.quoteDao(),
            routinePeriodDao = db.routinePeriodDao(),
            taskDao = db.taskDao(),
            taskCheckDao = db.taskCheckDao(),
            supersetExerciseDao = db.supersetExerciseDao(),
            supersetGroupDao = db.supersetGroupDao(),
            trainingCycleDao = db.trainingCycleDao(),
            foodDao = db.foodDao(),
            foodPortionDao = db.foodPortionDao(),
            mealPresetDao = db.mealPresetDao(),
            mealDao = db.mealDao(),
            mealEntryDao = db.mealEntryDao(),
            nutritionGoalDao = db.nutritionGoalDao(),
            recipeDao = db.recipeDao(),
            recipeIngredientDao = db.recipeIngredientDao(),
            healthStepCountDao = db.healthStepCountDao(),
            healthMetricDao = db.healthMetricDao(),
            healthGoalDao = db.healthGoalDao(),
            waterIntakeDao = db.waterIntakeDao(),
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `the 3 health entity classes are registered`() {
        assertNotNull(registry.findByClass(HealthStepCount::class))
        assertNotNull(registry.findByClass(HealthMetric::class))
        assertNotNull(registry.findByClass(HealthGoal::class))
    }

    @Test
    fun `health entities are present in push and delete orders`() {
        val names = listOf("HealthStepCounts", "HealthMetrics", "HealthGoals")

        names.forEach { name ->
            assertTrue("$name doit être dans l'ordre de push", registry.all.any { it.entityName == name })
            assertTrue("$name doit être dans l'ordre de delete", registry.reversed.any { it.entityName == name })
        }
    }

    @Test
    fun `health entity names map to the server table names`() {
        // sqlTableName est dérivé de entityName (PascalToSnake) et sert aux requêtes
        // SQL brutes de la data grid Sync Settings : il doit matcher la table serveur.
        assertTrue(registry.all.any { it.entityName == "HealthStepCounts" && it.sqlTableName == "health_step_counts" })
        assertTrue(registry.all.any { it.entityName == "HealthMetrics" && it.sqlTableName == "health_metrics" })
        assertTrue(registry.all.any { it.entityName == "HealthGoals" && it.sqlTableName == "health_goals" })
    }
}
