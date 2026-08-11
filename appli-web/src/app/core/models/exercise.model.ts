/** Exercise — forme wire (ExerciseOut côté serveur, camelCase). */
export interface Exercise {
  uuid: string;
  userId: number;
  name: string;
  description?: string | null;
  instructions?: string[] | null;
  recommendedSets?: number | null;
  recommendedReps?: string | null;
  restTimeSeconds?: number | null;
  durationInSeconds?: number | null;
  gifUrl?: string | null;
  isFavorite: boolean;
  lastDone?: string | null;
  updatedAt: string | null;
}

/** Ligne locale (IndexedDB) = wire + métadonnées de sync (miroir des flags Room). */
export interface LocalExercise extends Exercise {
  synced: boolean;
  pendingDeletion: boolean;
}
