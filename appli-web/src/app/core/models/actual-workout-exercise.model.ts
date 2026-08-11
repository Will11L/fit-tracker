/** ActualWorkoutExercise — forme wire (camelCase). Enfant de ActualWorkout (pas de userId : ownership via le parent). */
export interface ActualWorkoutExercise {
  uuid: string;
  actualWorkoutUUID: string;
  exerciseUUID: string;
  sets: number;
  reps: string;
  phase: string; // WARMUP | TRAINING | POST_TRAINING
  status: string; // NOT_STARTED | IN_PROGRESS | DONE | SKIPPED
  order: number;
  addedManually: boolean;
  updatedAt: string | null;
}

/** Ligne locale (IndexedDB) = wire + métadonnées de sync (miroir des flags Room). */
export interface LocalActualWorkoutExercise extends ActualWorkoutExercise {
  synced: boolean;
  pendingDeletion: boolean;
}
