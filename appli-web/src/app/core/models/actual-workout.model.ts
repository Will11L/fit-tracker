/** ActualWorkout — forme wire (ActualWorkoutOut côté serveur, camelCase). */
export interface ActualWorkout {
  uuid: string;
  userId: number;
  name: string;
  date: string; // YYYY-MM-DD (format strict côté serveur)
  notes?: string | null;
  location?: string | null;
  isDone: boolean;
  updatedAt: string | null;
}

/** Ligne locale (IndexedDB) = wire + métadonnées de sync (miroir des flags Room). */
export interface LocalActualWorkout extends ActualWorkout {
  synced: boolean;
  pendingDeletion: boolean;
}
