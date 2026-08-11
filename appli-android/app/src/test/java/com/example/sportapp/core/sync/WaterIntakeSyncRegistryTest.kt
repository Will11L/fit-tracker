package com.example.sportapp.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.local.AppDatabase
import com.example.sportapp.core.data.model.WaterIntake
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Hydratation (2026-07-05) — verrouille l'enregistrement de l'entité `water_intakes`
 * dans le [SyncRegistry].
 *
 * `SyncEngine.pushAll` itère sur `registry.all` (push) et `registry.reversed` (delete) :
 * une entité absente du registry ne serait ni poussée ni pull-mergée. WaterIntake n'a
 * aucune FK inter-entité (FK vers User seul), donc pas de contrainte d'ordre relatif —
 * on vérifie qu'elle est bien enregistrée et présente des deux côtés, et que son
 * `sqlTableName` matche la table serveur (data grid Sync Settings).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class WaterIntakeSyncRegistryTest {

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
    fun `water intake entity class is registered`() {
        assertNotNull(registry.findByClass(WaterIntake::class))
    }

    @Test
    fun `water intake is present in push and delete orders`() {
        assertTrue("WaterIntakes doit être dans l'ordre de push", registry.all.any { it.entityName == "WaterIntakes" })
        assertTrue("WaterIntakes doit être dans l'ordre de delete", registry.reversed.any { it.entityName == "WaterIntakes" })
    }

    @Test
    fun `water intake entity name maps to the server table name`() {
        assertTrue(registry.all.any { it.entityName == "WaterIntakes" && it.sqlTableName == "water_intakes" })
    }
}
