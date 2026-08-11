package com.example.sportapp.core.di

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.sportapp.core.data.local.ActualWorkoutDao
import com.example.sportapp.core.data.local.ActualWorkoutExerciseDao
import com.example.sportapp.core.data.local.ActualWorkoutSetDao
import com.example.sportapp.core.data.local.AppDatabase
import com.example.sportapp.core.data.local.migrations.Migrations
import com.example.sportapp.core.data.local.AvailableEquipmentDao
import com.example.sportapp.core.data.local.CycleWorkoutDao
import com.example.sportapp.core.data.local.EquipmentDao
import com.example.sportapp.core.data.local.ExerciseDao
import com.example.sportapp.core.data.local.ExerciseEquipmentDao
import com.example.sportapp.core.data.local.ExerciseMuscleDao
import com.example.sportapp.core.data.local.FoodDao
import com.example.sportapp.core.data.local.HealthGoalDao
import com.example.sportapp.core.data.local.HealthMetricDao
import com.example.sportapp.core.data.local.HealthStepCountDao
import com.example.sportapp.core.data.local.WaterIntakeDao
import com.example.sportapp.core.data.local.FoodPortionDao
import com.example.sportapp.core.data.local.MealDao
import com.example.sportapp.core.data.local.MealEntryDao
import com.example.sportapp.core.data.local.MealPresetDao
import com.example.sportapp.core.data.local.MuscleDao
import com.example.sportapp.core.data.local.MuscleGoalDao
import com.example.sportapp.core.data.local.NotificationDao
import com.example.sportapp.core.data.local.NutritionGoalDao
import com.example.sportapp.core.data.local.RecipeDao
import com.example.sportapp.core.data.local.RecipeIngredientDao
import com.example.sportapp.core.data.local.PlannedWorkoutDao
import com.example.sportapp.core.data.local.PlannedWorkoutExerciseDao
import com.example.sportapp.core.data.local.QuoteDao
import com.example.sportapp.core.data.local.RoutinePeriodDao
import com.example.sportapp.core.data.local.SupersetExerciseDao
import com.example.sportapp.core.data.local.SupersetGroupDao
import com.example.sportapp.core.data.local.TaskCheckDao
import com.example.sportapp.core.data.local.TaskDao
import com.example.sportapp.core.data.local.TrainingCycleDao
import com.example.sportapp.core.data.local.UserDao
import com.example.sportapp.core.utils.NetworkMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule{

    @Provides
    @Singleton
    fun provideDatabase(app: Application): AppDatabase {
        return Room.databaseBuilder(
            app,
            AppDatabase::class.java,
            "sport_db"
        )
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)   // WAL : lectures concurrentes pendant écritures (sync layer écrit pendant qu'un Flow lit)
            .addMigrations(*Migrations.ALL)                          // Migrations enregistrees (cf. data/local/migrations/Migrations.kt)
            .fallbackToDestructiveMigration(false)      // Permet de migrer la base de données
            .build()
    }

    // Provide DAOs
    @Provides fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun provideActualWorkoutDao(db: AppDatabase): ActualWorkoutDao = db.actualWorkoutDao()
    @Provides fun provideActualWorkoutExerciseDao(db: AppDatabase): ActualWorkoutExerciseDao = db.actualWorkoutExerciseDao()
    @Provides fun provideActualWorkoutSetDao(db: AppDatabase): ActualWorkoutSetDao = db.actualWorkoutSetDao()
    @Provides fun provideAvailableEquipmentDao(db: AppDatabase): AvailableEquipmentDao = db.availableEquipmentDao()
    @Provides fun provideCycleWorkoutDao(db: AppDatabase): CycleWorkoutDao = db.cycleWorkoutDao()
    @Provides fun provideEquipmentDao(db: AppDatabase): EquipmentDao = db.equipmentDao()
    @Provides fun provideExerciseEquipmentDao(db: AppDatabase): ExerciseEquipmentDao = db.exerciseEquipmentDao()
    @Provides fun provideExerciseMuscleDao(db: AppDatabase): ExerciseMuscleDao = db.exerciseMuscleDao()
    @Provides fun provideMuscleDao(db: AppDatabase): MuscleDao = db.muscleDao()
    @Provides fun provideNotificationDao(db: AppDatabase): NotificationDao = db.notificationDao()
    @Provides fun providePlannedWorkoutDao(db: AppDatabase): PlannedWorkoutDao = db.plannedWorkoutDao()
    @Provides fun providePlannedWorkoutExerciseDao(db: AppDatabase): PlannedWorkoutExerciseDao = db.plannedWorkoutExerciseDao()
    @Provides fun provideQuoteDao(db: AppDatabase): QuoteDao = db.quoteDao()
    @Provides fun provideRoutinePeriodDao(db: AppDatabase): RoutinePeriodDao = db.routinePeriodDao()
    // Phase 0 (2026-05-12) : Task remplace RoutineTask + RoutineTaskCheck
    @Provides fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun provideTaskCheckDao(db: AppDatabase): TaskCheckDao = db.taskCheckDao()
    @Provides fun provideSupersetGroupDao(db: AppDatabase): SupersetGroupDao = db.supersetGroupDao()
    @Provides fun provideSupersetExerciseDao(db: AppDatabase): SupersetExerciseDao = db.supersetExerciseDao()
    @Provides fun provideTrainingCycleDao(db: AppDatabase): TrainingCycleDao = db.trainingCycleDao()
    @Provides fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
    @Provides fun provideMuscleGoalDao(db: AppDatabase): MuscleGoalDao = db.muscleGoalDao()

    // Nutrition A1 (2026-06-17)
    @Provides fun provideFoodDao(db: AppDatabase): FoodDao = db.foodDao()
    @Provides fun provideFoodPortionDao(db: AppDatabase): FoodPortionDao = db.foodPortionDao()
    @Provides fun provideMealPresetDao(db: AppDatabase): MealPresetDao = db.mealPresetDao()
    @Provides fun provideMealDao(db: AppDatabase): MealDao = db.mealDao()
    @Provides fun provideMealEntryDao(db: AppDatabase): MealEntryDao = db.mealEntryDao()
    @Provides fun provideNutritionGoalDao(db: AppDatabase): NutritionGoalDao = db.nutritionGoalDao()
    @Provides fun provideRecipeDao(db: AppDatabase): RecipeDao = db.recipeDao()
    @Provides fun provideRecipeIngredientDao(db: AppDatabase): RecipeIngredientDao = db.recipeIngredientDao()

    // Santé / Health Connect V1 (2026-06-17)
    @Provides fun provideHealthStepCountDao(db: AppDatabase): HealthStepCountDao = db.healthStepCountDao()
    @Provides fun provideHealthMetricDao(db: AppDatabase): HealthMetricDao = db.healthMetricDao()
    @Provides fun provideHealthGoalDao(db: AppDatabase): HealthGoalDao = db.healthGoalDao()

    // Hydratation (2026-07-05)
    @Provides fun provideWaterIntakeDao(db: AppDatabase): WaterIntakeDao = db.waterIntakeDao()

}
