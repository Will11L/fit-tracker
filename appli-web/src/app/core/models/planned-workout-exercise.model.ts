/** PlannedWorkoutExercise — forme wire (camelCase). Enfant de PlannedWorkout (pas de userId). */
export interface PlannedWorkoutExercise {
  uuid: string;
  plannedWorkoutUUID: string;
  exerciseUUID: string;
  sets: number;
  reps: string;
  phase: string; // WARMUP | TRAINING | POST_TRAINING
  status: string; // NOT_STARTED | IN_PROGRESS | DONE | SKIPPED
  order: number;
  ignored: boolean;
  updatedAt: string | null;
}

/** Ligne locale (IndexedDB) = wire + métadonnées de sync (miroir des flags Room). */
export interface LocalPlannedWorkoutExercise extends PlannedWorkoutExercise {
  synced: boolean;
  pendingDeletion: boolean;
}
