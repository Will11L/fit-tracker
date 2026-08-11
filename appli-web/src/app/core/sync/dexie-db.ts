import { Injectable } from '@angular/core';
import Dexie, { Table } from 'dexie';
import { LocalExercise } from '@core/models/exercise.model';
import { LocalMuscle } from '@core/models/muscle.model';
import { LocalQuote } from '@core/models/quote.model';
import { LocalEquipment } from '@core/models/equipment.model';
import { LocalAvailableEquipment } from '@core/models/available-equipment.model';
import { LocalNotification } from '@core/models/notification.model';
import { LocalMuscleGoal } from '@core/models/muscle-goal.model';
import { LocalActualWorkout } from '@core/models/actual-workout.model';
import { LocalActualWorkoutExercise } from '@core/models/actual-workout-exercise.model';
import { LocalActualWorkoutSet } from '@core/models/actual-workout-set.model';
import { LocalExerciseMuscle } from '@core/models/exercise-muscle.model';
import { LocalExerciseEquipment } from '@core/models/exercise-equipment.model';
import { LocalPlannedWorkout } from '@core/models/planned-workout.model';
import { LocalPlannedWorkoutExercise } from '@core/models/planned-workout-exercise.model';
import { LocalSupersetGroup } from '@core/models/superset-group.model';
import { LocalSupersetExercise } from '@core/models/superset-exercise.model';
import { LocalTrainingCycle } from '@core/models/training-cycle.model';
import { LocalCycleWorkout } from '@core/models/cycle-workout.model';
import { LocalRoutinePeriod } from '@core/models/routine-period.model';
import { LocalTask } from '@core/models/task.model';
import { LocalTaskCheck } from '@core/models/task-check.model';
import { LocalFood } from '@core/models/food.model';
import { LocalFoodPortion } from '@core/models/food-portion.model';
import { LocalRecipe } from '@core/models/recipe.model';
import { LocalRecipeIngredient } from '@core/models/recipe-ingredient.model';
import { LocalMeal } from '@core/models/meal.model';
import { LocalMealPreset } from '@core/models/meal-preset.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import { LocalNutritionGoal } from '@core/models/nutrition-goal.model';
import { LocalWaterIntake } from '@core/models/water-intake.model';
import { LocalHealthGoal } from '@core/models/health-goal.model';
import { LocalHealthStepCount } from '@core/models/health-step-count.model';
import { LocalHealthMetric } from '@core/models/health-metric.model';

/**
 * Base IndexedDB locale (miroir de AppDatabase Room). Persistance offline-first.
 * NB : les booléens (synced/pendingDeletion) ne sont PAS indexés (IndexedDB ne sait pas) -> filtrés en mémoire.
 *
 * Versions : v1 exercises ; v2 + muscles ; v3 + quotes/equipments/available_equipments/notifications/muscle_goals ;
 * v4 + actual_workouts/actual_workout_exercises/actual_workout_sets/exercise_muscles (séances réalisées) ;
 * v5 + planned_workouts/planned_workout_exercises/superset_groups/superset_exercises/training_cycles/cycle_workouts (programmation) ;
 * v6 + routine_periods/tasks/task_checks (routines) ; v7 + exercise_equipment (jonction exercice↔matériel) ;
 * v8 + foods/food_portions/recipes/recipe_ingredients/meals/meal_presets/meal_entries/nutrition_goals (nutrition V3) ;
 * v9 meals + index presetUuid (lien stable repas↔periode, fix renommage casse le journal) ;
 * v10 foods + index foodGroup (catégories d'aliments : filtre catalogue par groupe/règne) ;
 * v11 + water_intakes/health_goals (hydratation : prises d'eau + objectif WATER_ML). Le
 *     champ foods.isWater n'est pas indexé → pas de changement de schéma foods.
 * v12 + health_step_counts/health_metrics (section Santé web, lecture seule : pas intraday
 *     + métriques FC/sommeil/SpO2/distance/calories poussés par Android depuis Health Connect).
 * Les upgrades sont automatiques et non destructifs.
 */
@Injectable({ providedIn: 'root' })
export class AppDb extends Dexie {
  exercises!: Table<LocalExercise, string>;
  muscles!: Table<LocalMuscle, string>;
  quotes!: Table<LocalQuote, string>;
  equipments!: Table<LocalEquipment, string>;
  available_equipments!: Table<LocalAvailableEquipment, string>;
  notifications!: Table<LocalNotification, string>;
  muscle_goals!: Table<LocalMuscleGoal, string>;
  actual_workouts!: Table<LocalActualWorkout, string>;
  actual_workout_exercises!: Table<LocalActualWorkoutExercise, string>;
  actual_workout_sets!: Table<LocalActualWorkoutSet, string>;
  exercise_muscles!: Table<LocalExerciseMuscle, string>;
  exercise_equipment!: Table<LocalExerciseEquipment, string>;
  planned_workouts!: Table<LocalPlannedWorkout, string>;
  planned_workout_exercises!: Table<LocalPlannedWorkoutExercise, string>;
  superset_groups!: Table<LocalSupersetGroup, string>;
  superset_exercises!: Table<LocalSupersetExercise, string>;
  training_cycles!: Table<LocalTrainingCycle, string>;
  cycle_workouts!: Table<LocalCycleWorkout, string>;
  routine_periods!: Table<LocalRoutinePeriod, string>;
  tasks!: Table<LocalTask, string>;
  task_checks!: Table<LocalTaskCheck, string>;
  foods!: Table<LocalFood, string>;
  food_portions!: Table<LocalFoodPortion, string>;
  recipes!: Table<LocalRecipe, string>;
  recipe_ingredients!: Table<LocalRecipeIngredient, string>;
  meals!: Table<LocalMeal, string>;
  meal_presets!: Table<LocalMealPreset, string>;
  meal_entries!: Table<LocalMealEntry, string>;
  nutrition_goals!: Table<LocalNutritionGoal, string>;
  water_intakes!: Table<LocalWaterIntake, string>;
  health_goals!: Table<LocalHealthGoal, string>;
  health_step_counts!: Table<LocalHealthStepCount, string>;
  health_metrics!: Table<LocalHealthMetric, string>;

  constructor() {
    super('sportapp');

    // Multi-onglets : un vieil onglet qui garde sa connexion ouverte bloque l'upgrade de
    // version IndexedDB des onglets récents (app figée tant qu'il n'est pas fermé).
    // `versionchange` = un autre onglet demande l'upgrade -> on ferme notre connexion et on
    // recharge pour rouvrir sur la nouvelle version.
    this.on('versionchange', () => {
      this.close();
      window.location.reload();
    });
    // Côté onglet qui upgrade : trace si un onglet tiers (sans handler, ex. vieille build)
    // bloque encore — l'upgrade reprendra automatiquement dès qu'il sera fermé.
    this.on('blocked', () => {
      console.warn(
        '[AppDb] Upgrade IndexedDB bloqué par un autre onglet sportapp — ferme les anciens onglets.',
      );
    });

    this.version(1).stores({
      exercises: 'uuid, userId, name',
    });
    this.version(2).stores({
      muscles: 'uuid, userId, name',
    });
    this.version(3).stores({
      quotes: 'uuid, userId',
      equipments: 'uuid',
      available_equipments: 'uuid, userId',
      notifications: 'uuid, userId',
      muscle_goals: 'uuid, userId, muscleUUID',
    });
    this.version(4).stores({
      actual_workouts: 'uuid, userId, date',
      actual_workout_exercises: 'uuid, actualWorkoutUUID, exerciseUUID',
      actual_workout_sets: 'uuid, actualWorkoutExerciseUUID',
      exercise_muscles: 'uuid, exerciseUUID, muscleUUID',
    });
    this.version(5).stores({
      planned_workouts: 'uuid, userId, dayOfWeek',
      planned_workout_exercises: 'uuid, plannedWorkoutUUID, exerciseUUID',
      superset_groups: 'uuid, userId',
      superset_exercises: 'uuid, supersetGroupUUID, exerciseUUID',
      training_cycles: 'uuid, userId',
      cycle_workouts: 'uuid, trainingCycleUUID, plannedWorkoutUUID',
    });
    this.version(6).stores({
      routine_periods: 'uuid, userId',
      tasks: 'uuid, userId, periodUUID',
      task_checks: 'uuid, userId, taskUUID, occurrenceDate',
    });
    this.version(7).stores({
      exercise_equipment: 'uuid, exerciseUUID, equipmentUUID',
    });
    this.version(8).stores({
      foods: 'uuid, userId, name, sourceRef',
      food_portions: 'uuid, foodUUID',
      recipes: 'uuid, userId, name',
      recipe_ingredients: 'uuid, recipeUUID, foodUUID',
      meals: 'uuid, userId, date',
      meal_presets: 'uuid, userId',
      meal_entries: 'uuid, mealUUID, foodUUID',
      nutrition_goals: 'uuid, userId, effectiveFrom',
    });
    this.version(9).stores({
      meals: 'uuid, userId, date, presetUuid',
    });
    this.version(10).stores({
      foods: 'uuid, userId, name, sourceRef, foodGroup',
    });
    this.version(11).stores({
      water_intakes: 'uuid, userId, date',
      health_goals: 'uuid, userId, effectiveFrom, type',
    });
    this.version(12).stores({
      health_step_counts: 'uuid, userId, date',
      health_metrics: 'uuid, userId, date, type',
    });
  }
}
