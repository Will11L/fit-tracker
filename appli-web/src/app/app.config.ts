import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { authInterceptor } from '@core/auth/auth.interceptor';
import { clientIdInterceptor } from '@core/auth/client-id.interceptor';
import { ExerciseStore } from '@core/sync/stores/exercise.store';
import { MuscleStore } from '@core/sync/stores/muscle.store';
import { QuoteStore } from '@core/sync/stores/quote.store';
import { EquipmentStore } from '@core/sync/stores/equipment.store';
import { AvailableEquipmentStore } from '@core/sync/stores/available-equipment.store';
import { NotificationStore } from '@core/sync/stores/notification.store';
import { MuscleGoalStore } from '@core/sync/stores/muscle-goal.store';
import { ExerciseMuscleStore } from '@core/sync/stores/exercise-muscle.store';
import { ExerciseEquipmentStore } from '@core/sync/stores/exercise-equipment.store';
import { ActualWorkoutStore } from '@core/sync/stores/actual-workout.store';
import { ActualWorkoutExerciseStore } from '@core/sync/stores/actual-workout-exercise.store';
import { ActualWorkoutSetStore } from '@core/sync/stores/actual-workout-set.store';
import { PlannedWorkoutStore } from '@core/sync/stores/planned-workout.store';
import { PlannedWorkoutExerciseStore } from '@core/sync/stores/planned-workout-exercise.store';
import { SupersetGroupStore } from '@core/sync/stores/superset-group.store';
import { SupersetExerciseStore } from '@core/sync/stores/superset-exercise.store';
import { TrainingCycleStore } from '@core/sync/stores/training-cycle.store';
import { CycleWorkoutStore } from '@core/sync/stores/cycle-workout.store';
import { RoutinePeriodStore } from '@core/sync/stores/routine-period.store';
import { TaskStore } from '@core/sync/stores/task.store';
import { TaskCheckStore } from '@core/sync/stores/task-check.store';
import { FoodStore } from '@core/sync/stores/food.store';
import { FoodPortionStore } from '@core/sync/stores/food-portion.store';
import { RecipeStore } from '@core/sync/stores/recipe.store';
import { RecipeIngredientStore } from '@core/sync/stores/recipe-ingredient.store';
import { MealStore } from '@core/sync/stores/meal.store';
import { MealPresetStore } from '@core/sync/stores/meal-preset.store';
import { MealEntryStore } from '@core/sync/stores/meal-entry.store';
import { NutritionGoalStore } from '@core/sync/stores/nutrition-goal.store';
import { WaterIntakeStore } from '@core/sync/stores/water-intake.store';
import { HealthGoalStore } from '@core/sync/stores/health-goal.store';
import { HealthStepCountStore } from '@core/sync/stores/health-step-count.store';
import { HealthMetricStore } from '@core/sync/stores/health-metric.store';
import { SYNCABLE_STORES } from '@core/sync/syncable-store';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // withComponentInputBinding : lie les params de route aux inputs (ex. session/:uuid → SessionPage.uuid).
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([clientIdInterceptor, authInterceptor])),
    // Registre des entités synchronisables (miroir SyncRegistry). +1 ligne par entité future.
    { provide: SYNCABLE_STORES, useExisting: ExerciseStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: MuscleStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: QuoteStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: EquipmentStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: AvailableEquipmentStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: NotificationStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: MuscleGoalStore, multi: true },
    // Données d'entraînement (Batch A) — ordre FK : jonction puis séance > exercice > set.
    { provide: SYNCABLE_STORES, useExisting: ExerciseMuscleStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: ExerciseEquipmentStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: ActualWorkoutStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: ActualWorkoutExerciseStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: ActualWorkoutSetStore, multi: true },
    // Programmation (Batch B) — ordre FK : parents (planned/superset/cycle) avant enfants.
    { provide: SYNCABLE_STORES, useExisting: PlannedWorkoutStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: PlannedWorkoutExerciseStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: SupersetGroupStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: SupersetExerciseStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: TrainingCycleStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: CycleWorkoutStore, multi: true },
    // Routines (Batch C) — ordre FK : période > task > task_check.
    { provide: SYNCABLE_STORES, useExisting: RoutinePeriodStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: TaskStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: TaskCheckStore, multi: true },
    // Nutrition (V3) — ordre FK : parents avant enfants (food > portion, recipe > ingredient, meal > entry).
    { provide: SYNCABLE_STORES, useExisting: FoodStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: FoodPortionStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: RecipeStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: RecipeIngredientStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: MealPresetStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: MealStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: MealEntryStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: NutritionGoalStore, multi: true },
    // Hydratation (2026-07-05) — user-scoped, aucune FK inter-entité.
    { provide: SYNCABLE_STORES, useExisting: WaterIntakeStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: HealthGoalStore, multi: true },
    // Santé (2026-07-06) — lecture seule côté web (source Health Connect Android), user-scoped.
    { provide: SYNCABLE_STORES, useExisting: HealthStepCountStore, multi: true },
    { provide: SYNCABLE_STORES, useExisting: HealthMetricStore, multi: true },
  ],
};
