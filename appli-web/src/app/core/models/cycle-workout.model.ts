/** CycleWorkout — forme wire (camelCase). Lie un PlannedWorkout à un TrainingCycle (pas de userId). */
export interface CycleWorkout {
  uuid: string;
  trainingCycleUUID: string;
  plannedWorkoutUUID: string;
  updatedAt: string | null;
}

/** Ligne locale (IndexedDB) = wire + métadonnées de sync (miroir des flags Room). */
export interface LocalCycleWorkout extends CycleWorkout {
  synced: boolean;
  pendingDeletion: boolean;
}
