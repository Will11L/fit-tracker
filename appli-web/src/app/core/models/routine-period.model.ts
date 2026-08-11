/** RoutinePeriod — forme wire (RoutinePeriodOut côté serveur, camelCase). Plage horaire d'une journée. */
export interface RoutinePeriod {
  uuid: string;
  userId: number;
  name: string;
  startTime: string; // "HH:MM" (ex. "06:30")
  endTime: string; // "HH:MM" (ex. "09:00")
  order: number;
  updatedAt: string | null;
}

/** Ligne locale (IndexedDB) = wire + métadonnées de sync (miroir des flags Room). */
export interface LocalRoutinePeriod extends RoutinePeriod {
  synced: boolean;
  pendingDeletion: boolean;
}
