/** ExerciseEquipment — forme wire (camelCase). Jonction Exercise↔Equipment (pas de userId). */
export interface ExerciseEquipment {
  uuid: string;
  exerciseUUID: string;
  equipmentUUID: string;
  updatedAt: string | null;
}

/** Ligne locale (IndexedDB) = wire + métadonnées de sync (miroir des flags Room). */
export interface LocalExerciseEquipment extends ExerciseEquipment {
  synced: boolean;
  pendingDeletion: boolean;
}
