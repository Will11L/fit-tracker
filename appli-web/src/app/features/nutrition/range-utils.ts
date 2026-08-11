import { StatGran } from './nutrition-stats-utils';

/** Période sélectionnable (miroir des chips de la page Stats sport). */
export type RangeKey = 'W1' | 'D30' | 'M3' | 'M6' | 'Y1' | 'ALL' | 'CUSTOM';

export const RANGE_CHIPS: { key: RangeKey; label: string }[] = [
  { key: 'W1', label: '1 semaine' },
  { key: 'D30', label: '30 jours' },
  { key: 'M3', label: '3 mois' },
  { key: 'M6', label: '6 mois' },
  { key: 'Y1', label: '1 an' },
  { key: 'ALL', label: 'Tout' },
  { key: 'CUSTOM', label: 'Personnalisé' },
];

/** Bornes + granularité d'affichage (jour si ≤ 14 j, sinon semaine). */
export interface RangeBounds {
  startIso: string;
  endIso: string;
  days: number;
  gran: StatGran;
}

function pad(n: number): string {
  return n.toString().padStart(2, '0');
}

function toIso(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function parseIso(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

/** "YYYY-MM-DD" d'une date ± delta jours en arithmétique locale (Date acceptée pour aujourd'hui). */
export function addDaysRange(base: Date | string, delta: number): string {
  const d = typeof base === 'string' ? parseIso(base) : base;
  return toIso(new Date(d.getFullYear(), d.getMonth(), d.getDate() + delta));
}

function minusMonths(d: Date, n: number): Date {
  const y = d.getFullYear();
  const m = d.getMonth() - n;
  const lastDay = new Date(y, m + 1, 0).getDate();
  return new Date(y, m, Math.min(d.getDate(), lastDay));
}

/**
 * Calcule les bornes d'une période. 'ALL' part de `earliestIso` si fourni (clamp aux données réelles
 * pour éviter des mois vides), sinon d'un repli ancien. 'CUSTOM' utilise les bornes saisies.
 */
export function computeBounds(
  kind: RangeKey,
  customStart: string,
  customEnd: string,
  earliestIso: string | null,
): RangeBounds {
  const today = new Date();
  let start: Date;
  let end = today;
  switch (kind) {
    case 'W1':
      start = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 6);
      break;
    case 'D30':
      start = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 29);
      break;
    case 'M3':
      start = minusMonths(today, 3);
      break;
    case 'M6':
      start = minusMonths(today, 6);
      break;
    case 'Y1':
      start = minusMonths(today, 12);
      break;
    case 'ALL':
      start = earliestIso ? parseIso(earliestIso) : new Date(2000, 0, 1);
      break;
    case 'CUSTOM':
      start = parseIso(customStart);
      end = parseIso(customEnd);
      break;
  }
  const startIso = toIso(start);
  const endIso = toIso(end);
  const days = Math.max(1, Math.round((end.getTime() - start.getTime()) / 86400000) + 1);
  return { startIso, endIso, days, gran: days <= 14 ? 'DAILY' : 'WEEKLY' };
}
