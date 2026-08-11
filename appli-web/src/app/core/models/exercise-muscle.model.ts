/** ExerciseMuscle — forme wire (camelCase). Jonction pondérée Exercise↔Muscle (pas de userId). */
export interface ExerciseMuscle {
  uuid: string;
  exerciseUUID: string;
  muscleUUID: string;
  coefficient: number;
  updatedAt: string | null;
}

/** Ligne locale (IndexedDB) = wire + métadonnées de sync (miroir des flags Room). */
export interface LocalExerciseMuscle extends ExerciseMuscle {
  synced: boolean;
  pendingDeletion: boolean;
}
