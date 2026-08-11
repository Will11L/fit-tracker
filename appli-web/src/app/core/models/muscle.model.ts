/** Muscle — forme wire (MuscleOut côté serveur, camelCase). */
export interface Muscle {
  uuid: string;
  userId: number;
  name: string;
  muscleGroup?: string | null;
  zone?: string | null;
  isFavorite: boolean;
  updatedAt: string | null;
}

/** Ligne locale (IndexedDB) = wire + métadonnées de sync. */
export interface LocalMuscle extends Muscle {
  synced: boolean;
  pendingDeletion: boolean;
}
