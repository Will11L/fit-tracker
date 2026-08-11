/** ActualWorkoutSet — forme wire (camelCase). Enfant de ActualWorkoutExercise. */
export interface ActualWorkoutSet {
  uuid: string;
  actualWorkoutExerciseUUID: string;
  setOrder: number;
  reps: number;
  weight: number;
  isDropset: boolean;
  notes?: string | null;
  recommendation?: string | null;
  status: string; // NOT_STARTED | IN_PROGRESS | DONE | SKIPPED
  updatedAt: string | null;
}

/** Ligne locale (IndexedDB) = wire + métadonnées de sync (miroir des flags Room). */
export interface LocalActualWorkoutSet extends ActualWorkoutSet {
  synced: boolean;
  pendingDeletion: boolean;
}
