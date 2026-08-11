package com.example.sportapp.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.local.AppDatabase
import com.example.sportapp.core.data.model.Food
import com.example.sportapp.core.data.model.FoodPortion
import com.example.sportapp.core.data.model.Meal
import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.core.data.model.MealPreset
import com.example.sportapp.core.data.model.NutritionGoal
import com.example.sportapp.core.data.model.Recipe
import com.example.sportapp.core.data.model.RecipeIngredient
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Nutrition A1 (2026-06-17) — verrouille l'enregistrement des 8 entités nutrition
 * dans le [SyncRegistry] et, surtout, leur ORDRE FK-aware.
 *
 * Pourquoi c'est load-bearing : `SyncEngine.pushAll` itère sur `registry.all` dans
 * l'ordre, et delete sur `registry.reversed`. Si un enfant (ex. MealEntry) est poussé
 * AVANT son parent (Meal), le serveur rejette l'INSERT pour violation de clé étrangère.
 * Cet ordre est posé à la main dans `SyncRegistry.all` — un reorder accidentel passe
 * inaperçu sans ce garde-fou.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SyncRegistryNutritionOrderTest {

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

    private fun pushIndex(entityName: String): Int =
        registry.all.indexOfFirst { it.entityName == entityName }

    @Test
    fun `the 8 nutrition entity classes are registered`() {
        assertNotNull(registry.findByClass(Food::class))
        assertNotNull(registry.findByClass(FoodPortion::class))
        assertNotNull(registry.findByClass(MealPreset::class))
        assertNotNull(registry.findByClass(Meal::class))
        assertNotNull(registry.findByClass(MealEntry::class))
        assertNotNull(registry.findByClass(NutritionGoal::class))
        assertNotNull(registry.findByClass(Recipe::class))
        assertNotNull(registry.findByClass(RecipeIngredient::class))
    }

    @Test
    fun `nutrition children are pushed after their FK parents`() {
        val food = pushIndex("Foods")
        val recipe = pushIndex("Recipes")
        val mealPreset = pushIndex("MealPresets")
        val foodPortion = pushIndex("FoodPortions")
        val meal = pushIndex("Meals")
        val recipeIngredient = pushIndex("RecipeIngredients")
        val mealEntry = pushIndex("MealEntries")

        // Tous présents.
        assertTrue(listOf(food, recipe, mealPreset, foodPortion, meal, recipeIngredient, mealEntry).all { it >= 0 })

        assertTrue("FoodPortion (-> Food) doit être après Food", foodPortion > food)
        assertTrue("Meal (-> MealPreset) doit être après MealPreset", meal > mealPreset)
        assertTrue("RecipeIngredient (-> Recipe) doit être après Recipe", recipeIngredient > recipe)
        assertTrue("RecipeIngredient (-> Food) doit être après Food", recipeIngredient > food)
        assertTrue("MealEntry (-> Meal) doit être après Meal", mealEntry > meal)
        assertTrue("MealEntry (-> Food) doit être après Food", mealEntry > food)
        assertTrue("MealEntry (-> Recipe) doit être après Recipe", mealEntry > recipe)
    }

    @Test
    fun `reversed order deletes children before parents`() {
        val reversed = registry.reversed
        val mealEntry = reversed.indexOfFirst { it.entityName == "MealEntries" }
        val meal = reversed.indexOfFirst { it.entityName == "Meals" }
        val food = reversed.indexOfFirst { it.entityName == "Foods" }

        assertTrue("en delete, MealEntry doit passer avant Meal", mealEntry < meal)
        assertTrue("en delete, Meal doit passer avant Food", meal < food)
    }
}
