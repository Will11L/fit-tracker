import { Task } from '@core/models/task.model';

/**
 * Occurrence d'une Task non-DAILY à une date donnée — port de ScheduledTaskExpander.kt
 * (occurrencesInRange réduit au test d'une seule date) :
 * - NONE    : dueDate === date ;
 * - WEEKLY  : jour de semaine ∈ recurrenceWeekdays (Lun=0..Dim=6) dans [start..end] ;
 * - MONTHLY : même jour-du-mois que recurrenceStartDate dans [start..end] ;
 * - YEARLY  : même (mois, jour) que recurrenceStartDate dans [start..end] ;
 * - DAILY   : false (géré par les périodes, pas par l'expansion).
 * excludedDates (mode "Seulement cette occurrence" Android) filtre toujours.
 * Comparaisons de dates en ISO "YYYY-MM-DD" (ordre lexicographique = ordre chronologique).
 */
export function occursOn(task: Task, dateIso: string): boolean {
  if (task.recurrenceKind === 'DAILY') return false;
  if ((task.excludedDates ?? []).includes(dateIso)) return false;

  if (task.recurrenceKind === 'NONE') return task.dueDate === dateIso;

  const start = task.recurrenceStartDate ?? null;
  const end = task.recurrenceEndDate ?? null;
  if (!start) return false;
  if (dateIso < start || (end !== null && dateIso > end)) return false;

  switch (task.recurrenceKind) {
    case 'WEEKLY': {
      const weekdays = task.recurrenceWeekdays ?? [];
      if (weekdays.length === 0) return false;
      const [y, m, d] = dateIso.split('-').map(Number);
      const jsDay = new Date(y, m - 1, d).getDay(); // 0=Dim..6=Sam
      const monBased = (jsDay + 6) % 7; // Lun=0..Dim=6 (encoding Android)
      return weekdays.includes(monBased);
    }
    case 'MONTHLY':
      return dateIso.slice(8, 10) === start.slice(8, 10);
    case 'YEARLY':
      return dateIso.slice(5) === start.slice(5);
    default:
      return false;
  }
}
