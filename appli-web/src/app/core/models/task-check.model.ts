/** TaskCheck — forme wire (TaskCheckOut côté serveur, camelCase). Une coche par occurrence_date d'une Task. */
export interface TaskCheck {
  uuid: string;
  userId: number;
  taskUUID: string;
  occurrenceDate: string; // YYYY-MM-DD
  isChecked: boolean;
  checkedAt?: string | null;
  updatedAt: string | null;
}

/** Ligne locale (IndexedDB) = wire + métadonnées de sync (miroir des flags Room). */
export interface LocalTaskCheck extends TaskCheck {
  synced: boolean;
  pendingDeletion: boolean;
}
