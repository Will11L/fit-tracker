/** TrainingCycle — forme wire (TrainingCycleOut côté serveur, camelCase). */
export interface TrainingCycle {
  uuid: string;
  userId: number;
  name: string;
  startDate: string; // YYYY-MM-DD strict (serveur rejette les datetimes complets)
  endDate: string; // YYYY-MM-DD strict
  updatedAt: string | null;
}

/** Ligne locale (IndexedDB) = wire + métadonnées de sync (miroir des flags Room). */
export interface LocalTrainingCycle extends TrainingCycle {
  synced: boolean;
  pendingDeletion: boolean;
}
