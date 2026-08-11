/** PlannedWorkout — forme wire (PlannedWorkoutOut côté serveur, camelCase). */
export interface PlannedWorkout {
  uuid: string;
  userId: number;
  name: string;
  dayOfWeek: string; // Monday … Sunday (code wire EN, cf. politique 11)
  updatedAt: string | null;
}

/** Ligne locale (IndexedDB) = wire + métadonnées de sync (miroir des flags Room). */
export interface LocalPlannedWorkout extends PlannedWorkout {
  synced: boolean;
  pendingDeletion: boolean;
}
