/** Type de récurrence (code wire UPPER_CASE, cf. politique 11). */
export type RecurrenceKind = 'NONE' | 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';

/**
 * Task — forme wire (TaskOut côté serveur, camelCase). Tâche unifiée (remplace RoutineTask, Phase 0).
 * Champs conditionnels selon recurrenceKind (validés côté serveur) :
 * NONE→dueDate ; DAILY→periodUUID+recurrenceStartDate ; WEEKLY→recurrenceWeekdays+recurrenceStartDate ;
 * MONTHLY/YEARLY→recurrenceStartDate.
 */
export interface Task {
  uuid: string;
  userId: number;
  title: string;
  notes?: string | null;
  isActive: boolean;
  order: number;
  recurrenceKind: RecurrenceKind;
  dueDate?: string | null; // YYYY-MM-DD
  dueTime?: string | null; // HH:MM
  periodUUID?: string | null;
  recurrenceWeekdays?: number[] | null; // 0..6 (Lun=0..Dim=6)
  recurrenceStartDate?: string | null; // YYYY-MM-DD
  recurrenceEndDate?: string | null; // YYYY-MM-DD
  excludedDates: string[]; // dates ISO exclues des occurrences
  reminderMinutesBefore?: number | null;
  updatedAt: string | null;
}

/** Ligne locale (IndexedDB) = wire + métadonnées de sync (miroir des flags Room). */
export interface LocalTask extends Task {
  synced: boolean;
  pendingDeletion: boolean;
}
