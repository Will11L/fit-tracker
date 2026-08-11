/** SupersetExercise — forme wire (camelCase). Enfant de SupersetGroup (pas de userId). */
export interface SupersetExercise {
  uuid: string;
  supersetGroupUUID: string;
  exerciseUUID: string;
  orderInGroup: number;
  updatedAt: string | null;
}

/** Ligne locale (IndexedDB) = wire + métadonnées de sync (miroir des flags Room). */
export interface LocalSupersetExercise extends SupersetExercise {
  synced: boolean;
  pendingDeletion: boolean;
}
