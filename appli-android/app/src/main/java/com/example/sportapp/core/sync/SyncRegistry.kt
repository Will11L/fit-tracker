package com.example.sportapp.core.sync

import com.example.sportapp.core.data.local.ActualWorkoutDao
import com.example.sportapp.core.data.local.ActualWorkoutExerciseDao
import com.example.sportapp.core.data.local.ActualWorkoutSetDao
import com.example.sportapp.core.data.local.AvailableEquipmentDao
import com.example.sportapp.core.data.local.CycleWorkoutDao
import com.example.sportapp.core.data.local.EquipmentDao
import com.example.sportapp.core.data.local.ExerciseDao
import com.example.sportapp.core.data.local.ExerciseEquipmentDao
import com.example.sportapp.core.data.local.ExerciseMuscleDao
import com.example.sportapp.core.data.local.FoodDao
import com.example.sportapp.core.data.local.FoodPortionDao
import com.example.sportapp.core.data.local.HealthGoalDao
import com.example.sportapp.core.data.local.HealthMetricDao
import com.example.sportapp.core.data.local.HealthStepCountDao
import com.example.sportapp.core.data.local.MealDao
import com.example.sportapp.core.data.local.MealEntryDao
import com.example.sportapp.core.data.local.MealPresetDao
import com.example.sportapp.core.data.local.MuscleDao
import com.example.sportapp.core.data.local.MuscleGoalDao
import com.example.sportapp.core.data.local.NotificationDao
import com.example.sportapp.core.data.local.NutritionGoalDao
import com.example.sportapp.core.data.local.PlannedWorkoutDao
import com.example.sportapp.core.data.local.PlannedWorkoutExerciseDao
import com.example.sportapp.core.data.local.QuoteDao
import com.example.sportapp.core.data.local.RecipeDao
import com.example.sportapp.core.data.local.RecipeIngredientDao
import com.example.sportapp.core.data.local.RoutinePeriodDao
import com.example.sportapp.core.data.local.SupersetExerciseDao
import com.example.sportapp.core.data.local.SupersetGroupDao
import com.example.sportapp.core.data.local.TaskCheckDao
import com.example.sportapp.core.data.local.TaskDao
import com.example.sportapp.core.data.local.TrainingCycleDao
import com.example.sportapp.core.data.local.WaterIntakeDao
import com.example.sportapp.core.data.model.ActualWorkout
import com.example.sportapp.core.data.model.ActualWorkoutExercise
import com.example.sportapp.core.data.model.ActualWorkoutSet
import com.example.sportapp.core.data.model.AvailableEquipment
import com.example.sportapp.core.data.model.CycleWorkout
import com.example.sportapp.core.data.model.Equipment
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.data.model.ExerciseEquipment
import com.example.sportapp.core.data.model.ExerciseMuscle
import com.example.sportapp.core.data.model.Food
import com.example.sportapp.core.data.model.FoodPortion
import com.example.sportapp.core.data.model.HealthGoal
import com.example.sportapp.core.data.model.HealthMetric
import com.example.sportapp.core.data.model.HealthStepCount
import com.example.sportapp.core.data.model.Meal
import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.core.data.model.MealPreset
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.core.data.model.MuscleGoal
import com.example.sportapp.core.data.model.Notification
import com.example.sportapp.core.data.model.NutritionGoal
import com.example.sportapp.core.data.model.PlannedWorkout
import com.example.sportapp.core.data.model.PlannedWorkoutExercise
import com.example.sportapp.core.data.model.Quote
import com.example.sportapp.core.data.model.Recipe
import com.example.sportapp.core.data.model.RecipeIngredient
import com.example.sportapp.core.data.model.RoutinePeriod
import com.example.sportapp.core.data.model.SupersetExercise
import com.example.sportapp.core.data.model.SupersetGroup
import com.example.sportapp.core.data.model.Task
import com.example.sportapp.core.data.model.TaskCheck
import com.example.sportapp.core.data.model.TrainingCycle
import com.example.sportapp.core.data.model.WaterIntake
import com.example.sportapp.core.sync.base.SyncableEntity
import com.example.sportapp.core.sync.syncables.ActualWorkoutExerciseSyncable
import com.example.sportapp.core.sync.syncables.ActualWorkoutSetSyncable
import com.example.sportapp.core.sync.syncables.ActualWorkoutSyncable
import com.example.sportapp.core.sync.syncables.AvailableEquipmentSyncable
import com.example.sportapp.core.sync.syncables.CycleWorkoutSyncable
import com.example.sportapp.core.sync.syncables.EquipmentSyncable
import com.example.sportapp.core.sync.syncables.ExerciseEquipmentSyncable
import com.example.sportapp.core.sync.syncables.ExerciseMuscleSyncable
import com.example.sportapp.core.sync.syncables.ExerciseSyncable
import com.example.sportapp.core.sync.syncables.FoodPortionSyncable
import com.example.sportapp.core.sync.syncables.FoodSyncable
import com.example.sportapp.core.sync.syncables.HealthGoalSyncable
import com.example.sportapp.core.sync.syncables.HealthMetricSyncable
import com.example.sportapp.core.sync.syncables.HealthStepCountSyncable
import com.example.sportapp.core.sync.syncables.MealEntrySyncable
import com.example.sportapp.core.sync.syncables.MealPresetSyncable
import com.example.sportapp.core.sync.syncables.MealSyncable
import com.example.sportapp.core.sync.syncables.MuscleGoalSyncable
import com.example.sportapp.core.sync.syncables.MuscleSyncable
import com.example.sportapp.core.sync.syncables.NotificationSyncable
import com.example.sportapp.core.sync.syncables.NutritionGoalSyncable
import com.example.sportapp.core.sync.syncables.PlannedWorkoutExerciseSyncable
import com.example.sportapp.core.sync.syncables.PlannedWorkoutSyncable
import com.example.sportapp.core.sync.syncables.QuoteSyncable
import com.example.sportapp.core.sync.syncables.RecipeIngredientSyncable
import com.example.sportapp.core.sync.syncables.RecipeSyncable
import com.example.sportapp.core.sync.syncables.RoutinePeriodSyncable
import com.example.sportapp.core.sync.syncables.SupersetExerciseSyncable
import com.example.sportapp.core.sync.syncables.SupersetGroupSyncable
import com.example.sportapp.core.sync.syncables.TaskCheckSyncable
import com.example.sportapp.core.sync.syncables.TaskSyncable
import com.example.sportapp.core.sync.syncables.TrainingCycleSyncable
import com.example.sportapp.core.sync.syncables.WaterIntakeSyncable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Source unique de vérité pour l'ensemble des entités synchronisables de l'app.
 *
 * Ajouter une 21e entité au système de sync = créer son `XSyncable` + ajouter
 * 1 ligne ici dans `all`. Aucun autre code dispatch n'a besoin d'être touché
 * (`SyncEngine`, `SyncManager`, `SyncSettingsViewModel`, NetworkMonitor itèrent
 * tous sur `all` ou `reversed`).
 *
 * Cf. T4.2 Phase 1.2 (2026-05-07).
 */
@Singleton
class SyncRegistry @Inject constructor(
    actualWorkoutDao: ActualWorkoutDao,
    actualWorkoutExerciseDao: ActualWorkoutExerciseDao,
    actualWorkoutSetDao: ActualWorkoutSetDao,
    availableEquipmentDao: AvailableEquipmentDao,
    cycleWorkoutDao: CycleWorkoutDao,
    equipmentDao: EquipmentDao,
    exerciseDao: ExerciseDao,
    exerciseEquipmentDao: ExerciseEquipmentDao,
    exerciseMuscleDao: ExerciseMuscleDao,
    muscleDao: MuscleDao,
    muscleGoalDao: MuscleGoalDao,
    notificationDao: NotificationDao,
    plannedWorkoutDao: PlannedWorkoutDao,
    plannedWorkoutExerciseDao: PlannedWorkoutExerciseDao,
    quoteDao: QuoteDao,
    routinePeriodDao: RoutinePeriodDao,
    taskDao: TaskDao,
    taskCheckDao: TaskCheckDao,
    supersetExerciseDao: SupersetExerciseDao,
    supersetGroupDao: SupersetGroupDao,
    trainingCycleDao: TrainingCycleDao,
    // Nutrition A1 (2026-06-17)
    foodDao: FoodDao,
    foodPortionDao: FoodPortionDao,
    mealPresetDao: MealPresetDao,
    mealDao: MealDao,
    mealEntryDao: MealEntryDao,
    nutritionGoalDao: NutritionGoalDao,
    recipeDao: RecipeDao,
    recipeIngredientDao: RecipeIngredientDao,
    // Santé / Health Connect V1 (2026-06-17)
    healthStepCountDao: HealthStepCountDao,
    healthMetricDao: HealthMetricDao,
    healthGoalDao: HealthGoalDao,
    // Hydratation (2026-07-05)
    waterIntakeDao: WaterIntakeDao,
) {
    /**
     * Liste ordonnée FK-aware : parents avant enfants. Reproduit l'ordre
     * historiquement validé en prod dans `SyncManager.syncAllToServer()`
     * (V4.4). Utiliser tel quel pour push (insert/update). Pour delete,
     * utiliser [reversed] (enfants avant parents → évite 404 cascade).
     */
    val all: List<SyncableEntity<*>> = listOf(
        ActualWorkoutSyncable(actualWorkoutDao),
        AvailableEquipmentSyncable(availableEquipmentDao),
        EquipmentSyncable(equipmentDao),
        ExerciseSyncable(exerciseDao),
        ExerciseEquipmentSyncable(exerciseEquipmentDao),
        MuscleSyncable(muscleDao),
        PlannedWorkoutSyncable(plannedWorkoutDao),
        PlannedWorkoutExerciseSyncable(plannedWorkoutExerciseDao),
        SupersetGroupSyncable(supersetGroupDao),
        TrainingCycleSyncable(trainingCycleDao),
        ActualWorkoutExerciseSyncable(actualWorkoutExerciseDao),
        ActualWorkoutSetSyncable(actualWorkoutSetDao),
        ExerciseMuscleSyncable(exerciseMuscleDao),
        MuscleGoalSyncable(muscleGoalDao),
        SupersetExerciseSyncable(supersetExerciseDao),
        CycleWorkoutSyncable(cycleWorkoutDao),
        RoutinePeriodSyncable(routinePeriodDao),
        TaskSyncable(taskDao),
        TaskCheckSyncable(taskCheckDao),
        QuoteSyncable(quoteDao),
        NotificationSyncable(notificationDao),

        // Nutrition A1 (2026-06-17) — ordre FK-aware : parents avant enfants.
        // Food / MealPreset / NutritionGoal / Recipe = parents (FK vers User seul,
        // pas de FK Room inter-nutrition). Puis FoodPortion (-> Food), Meal (-> MealPreset),
        // RecipeIngredient (-> Recipe + Food), enfin MealEntry (-> Meal + Food + Recipe).
        FoodSyncable(foodDao),
        MealPresetSyncable(mealPresetDao),
        NutritionGoalSyncable(nutritionGoalDao),
        RecipeSyncable(recipeDao),
        FoodPortionSyncable(foodPortionDao),
        MealSyncable(mealDao),
        RecipeIngredientSyncable(recipeIngredientDao),
        MealEntrySyncable(mealEntryDao),

        // Santé / Health Connect V1 (2026-06-17) — 3 entités user-scoped, FK vers
        // User seul (aucune FK inter-santé). Position sans contrainte d'ordre relatif :
        // placées en fin de liste, après les parents workout/nutrition.
        HealthStepCountSyncable(healthStepCountDao),
        HealthMetricSyncable(healthMetricDao),
        HealthGoalSyncable(healthGoalDao),

        // Hydratation (2026-07-05) — entité user-scoped, FK vers User seul (aucune
        // FK inter-entité). Placée en fin de liste, sans contrainte d'ordre relatif.
        WaterIntakeSyncable(waterIntakeDao),
    )

    /** Ordre inverse : enfants avant parents (utilisé pour les deletes). */
    val reversed: List<SyncableEntity<*>> get() = all.reversed()

    /**
     * Lookup par classe Kotlin de l'entité. Utilisé par `SyncSettingsViewModel`
     * pour dispatcher `syncEntity(entity: Any)` / `deleteEntity(entity: Any)`.
     */
    private val byClass: Map<KClass<*>, SyncableEntity<*>> = mapOf(
        ActualWorkout::class to all.first { it.entityName == "ActualWorkouts" },
        ActualWorkoutExercise::class to all.first { it.entityName == "ActualWorkoutExercises" },
        ActualWorkoutSet::class to all.first { it.entityName == "ActualWorkoutSets" },
        AvailableEquipment::class to all.first { it.entityName == "AvailableEquipments" },
        CycleWorkout::class to all.first { it.entityName == "CycleWorkouts" },
        Equipment::class to all.first { it.entityName == "Equipments" },
        Exercise::class to all.first { it.entityName == "Exercises" },
        ExerciseEquipment::class to all.first { it.entityName == "ExerciseEquipment" },
        ExerciseMuscle::class to all.first { it.entityName == "ExerciseMuscles" },
        Muscle::class to all.first { it.entityName == "Muscles" },
        MuscleGoal::class to all.first { it.entityName == "MuscleGoals" },
        Notification::class to all.first { it.entityName == "Notifications" },
        PlannedWorkout::class to all.first { it.entityName == "PlannedWorkouts" },
        PlannedWorkoutExercise::class to all.first { it.entityName == "PlannedWorkoutExercises" },
        Quote::class to all.first { it.entityName == "Quotes" },
        RoutinePeriod::class to all.first { it.entityName == "RoutinePeriods" },
        Task::class to all.first { it.entityName == "Tasks" },
        TaskCheck::class to all.first { it.entityName == "TaskChecks" },
        SupersetExercise::class to all.first { it.entityName == "SupersetExercises" },
        SupersetGroup::class to all.first { it.entityName == "SupersetGroups" },
        TrainingCycle::class to all.first { it.entityName == "TrainingCycles" },
        // Nutrition A1 (2026-06-17)
        Food::class to all.first { it.entityName == "Foods" },
        FoodPortion::class to all.first { it.entityName == "FoodPortions" },
        MealPreset::class to all.first { it.entityName == "MealPresets" },
        Meal::class to all.first { it.entityName == "Meals" },
        MealEntry::class to all.first { it.entityName == "MealEntries" },
        NutritionGoal::class to all.first { it.entityName == "NutritionGoals" },
        Recipe::class to all.first { it.entityName == "Recipes" },
        RecipeIngredient::class to all.first { it.entityName == "RecipeIngredients" },
        // Santé / Health Connect V1 (2026-06-17)
        HealthStepCount::class to all.first { it.entityName == "HealthStepCounts" },
        HealthMetric::class to all.first { it.entityName == "HealthMetrics" },
        HealthGoal::class to all.first { it.entityName == "HealthGoals" },
        // Hydratation (2026-07-05)
        WaterIntake::class to all.first { it.entityName == "WaterIntakes" },
    )

    /** Trouve la `SyncableEntity` correspondant à un item donné (classe Kotlin). */
    fun findByItem(item: Any): SyncableEntity<*>? = byClass[item::class]

    /** Trouve la `SyncableEntity` par classe Kotlin (lookup direct). */
    fun findByClass(klass: KClass<*>): SyncableEntity<*>? = byClass[klass]

    /** Trouve la `SyncableEntity` par nom (`entityName`, ex: "Muscles"). */
    fun findByEntityName(name: String): SyncableEntity<*>? =
        all.firstOrNull { it.entityName == name }
}
