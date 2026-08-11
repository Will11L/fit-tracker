package com.example.sportapp.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

import com.example.sportapp.core.data.model.*

const val DATABASE_VERSION = 26 // Change manuellement quand tu modifies le schéma

@Database(
    entities = [
        MuscleGoal::class,
        Exercise::class,
        ActualWorkout::class,
        ActualWorkoutExercise::class,
        ActualWorkoutSet::class,
        AvailableEquipment::class,
        CycleWorkout::class,
        Equipment::class,
        ExerciseEquipment::class,
        ExerciseMuscle::class,
        Muscle::class,
        Notification::class,
        PlannedWorkout::class,
        PlannedWorkoutExercise::class,
        Quote::class,
        RoutinePeriod::class,
        Task::class,
        TaskCheck::class,
        SupersetGroup::class,
        SupersetExercise::class,
        TrainingCycle::class,
        User::class,
        // Nutrition A1 (2026-06-17) : 8 tables portées du serveur
        Food::class,
        FoodPortion::class,
        MealPreset::class,
        Meal::class,
        MealEntry::class,
        NutritionGoal::class,
        Recipe::class,
        RecipeIngredient::class,
        // Santé / Health Connect V1 (2026-06-17) : 3 tables portées du serveur
        HealthStepCount::class,
        HealthMetric::class,
        HealthGoal::class,
        // Hydratation (2026-07-05) : prises d'eau horodatées, portées du serveur
        WaterIntake::class
        // Add any new entities here as needed
    ],
    version = DATABASE_VERSION,
    exportSchema = true
)
@TypeConverters(
    InstructionsConverter::class,
    NotificationDataConverter::class,
    IntListConverter::class
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun actualWorkoutDao(): ActualWorkoutDao
    abstract fun actualWorkoutExerciseDao(): ActualWorkoutExerciseDao
    abstract fun actualWorkoutSetDao(): ActualWorkoutSetDao
    abstract fun availableEquipmentDao(): AvailableEquipmentDao
    abstract fun cycleWorkoutDao(): CycleWorkoutDao
    abstract fun equipmentDao(): EquipmentDao
    abstract fun exerciseEquipmentDao(): ExerciseEquipmentDao
    abstract fun exerciseMuscleDao(): ExerciseMuscleDao
    abstract fun muscleDao(): MuscleDao
    abstract fun notificationDao(): NotificationDao
    abstract fun plannedWorkoutDao(): PlannedWorkoutDao
    abstract fun plannedWorkoutExerciseDao(): PlannedWorkoutExerciseDao
    abstract fun quoteDao(): QuoteDao
    abstract fun routinePeriodDao(): RoutinePeriodDao
    abstract fun taskDao(): TaskDao
    abstract fun taskCheckDao(): TaskCheckDao
    abstract fun supersetGroupDao(): SupersetGroupDao
    abstract fun supersetExerciseDao(): SupersetExerciseDao
    abstract fun trainingCycleDao(): TrainingCycleDao
    abstract fun userDao(): UserDao

    abstract fun muscleGoalDao(): MuscleGoalDao

    // Nutrition A1 (2026-06-17)
    abstract fun foodDao(): FoodDao
    abstract fun foodPortionDao(): FoodPortionDao
    abstract fun mealPresetDao(): MealPresetDao
    abstract fun mealDao(): MealDao
    abstract fun mealEntryDao(): MealEntryDao
    abstract fun nutritionGoalDao(): NutritionGoalDao
    abstract fun recipeDao(): RecipeDao
    abstract fun recipeIngredientDao(): RecipeIngredientDao

    // Santé / Health Connect V1 (2026-06-17)
    abstract fun healthStepCountDao(): HealthStepCountDao
    abstract fun healthMetricDao(): HealthMetricDao
    abstract fun healthGoalDao(): HealthGoalDao

    // Hydratation (2026-07-05)
    abstract fun waterIntakeDao(): WaterIntakeDao
}