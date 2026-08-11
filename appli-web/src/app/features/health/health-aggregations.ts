import { HealthStepCount } from '@core/models/health-step-count.model';
import { HealthMetric } from '@core/models/health-metric.model';
import { HealthGoal } from '@core/models/health-goal.model';

/**
 * Agrégations pures pour la section Santé web (lecture depuis Dexie, aucune dépendance Angular →
 * testable). Miroir de `HealthUiAggregations.kt` (Android). Les entrées sont les listes locales
 * déjà filtrées de `pendingDeletion` par l'appelant (repository).
 */

/** Granularité intraday des charts « aujourd'hui » : tranches de 30 min → 48 slots. */
export const SLOT_MINUTES = 30;
export const SLOTS_PER_DAY = (24 * 60) / SLOT_MINUTES; // 48

export const GOAL_TYPE_STEPS = 'STEPS';

/** Total de pas du jour [day] = somme des buckets de cette date. */
export function stepsForDay(buckets: HealthStepCount[], day: string): number {
  return buckets.filter((b) => b.date === day).reduce((acc, b) => acc + b.steps, 0);
}

/** Tranche de 30 min (0..47) d'un `bucketStart` "HH:MM", ou null si non parsable. */
function slotOfBucketStart(hhmm: string): number | null {
  const [hRaw, mRaw] = hhmm.split(':');
  const h = Number(hRaw);
  if (Number.isNaN(h)) return null;
  const m = Number(mRaw ?? '0');
  return h * (60 / SLOT_MINUTES) + Math.floor((Number.isNaN(m) ? 0 : m) / SLOT_MINUTES);
}

/**
 * Pas du jour ventilés sur 48 tranches de 30 min (index 0 = 00:00 … 47 = 23:30), à partir des
 * buckets. Slots sans bucket = 0. Un bucket horaire résiduel ("HH:00") tombe dans sa tranche.
 */
export function stepsBySlot(buckets: HealthStepCount[], day: string): number[] {
  const bySlot = new Array<number>(SLOTS_PER_DAY).fill(0);
  for (const b of buckets) {
    if (b.date !== day) continue;
    const slot = slotOfBucketStart(b.bucketStart);
    if (slot !== null && slot >= 0 && slot < SLOTS_PER_DAY) bySlot[slot] += b.steps;
  }
  return bySlot;
}

/**
 * Met à zéro les tranches postérieures à [currentSlot] d'une série intraday : la vue « aujourd'hui »
 * n'affiche aucune barre future (garde-fou contre l'artefact de proration Samsung). Affichage-only.
 */
export function clipFutureSlots(series: number[], currentSlot: number): number[] {
  return series.map((v, i) => (i > currentSlot ? 0 : v));
}

/** Index de tranche (0..47) d'une date locale (pour clipper la série intraday à « maintenant »). */
export function currentSlot(now: Date): number {
  return now.getHours() * (60 / SLOT_MINUTES) + Math.floor(now.getMinutes() / SLOT_MINUTES);
}

/** Type des tranches FC intraday synchronisées (≠ 'HEART_RATE' = moyenne quotidienne, start_time null). */
export const HR_INTRADAY_TYPE = 'HEART_RATE_INTRADAY';

/** Type des tranches sommeil intraday (minutes dormies par tranche de 30 min, miroir FC intraday). */
export const SLEEP_INTRADAY_TYPE = 'SLEEP_INTRADAY';

/** Types des phases de sommeil par jour (minutes par famille) — ordre d'empilement du chart 7 j
 *  (parité Android STAGE_BUCKET_*) : profond / léger / paradoxal (REM) / éveillé. */
export const SLEEP_STAGE_TYPES = [
  'SLEEP_STAGE_DEEP',
  'SLEEP_STAGE_LIGHT',
  'SLEEP_STAGE_REM',
  'SLEEP_STAGE_AWAKE',
] as const;

/** Type des sessions de sommeil : `startTime` = mise au lit "HH:MM", `value` = endormissement
 *  en minutes depuis minuit (unit "min-of-day"), date = jour du réveil. */
export const SLEEP_SESSION_TYPE = 'SLEEP_SESSION';

/** Types des slices de phases (hypnogramme) : le TYPE porte la phase (ordre = familles
 *  STAGE_BUCKET_* Android), `startTime` = début du stade "HH:MM", `value` = durée (min),
 *  date = jour du réveil. */
export const SLEEP_SLICE_TYPES = [
  'SLEEP_SLICE_DEEP',
  'SLEEP_SLICE_LIGHT',
  'SLEEP_SLICE_REM',
  'SLEEP_SLICE_AWAKE',
] as const;

/** Point d'hypnogramme : début en minutes RELATIVES à minuit du jour de réveil
 *  (négatif = la veille au soir), durée (min), famille 0..3 (0=profond … 3=éveillé). */
export interface SleepPhasePoint {
  startMin: number;
  minutes: number;
  bucket: number;
}

/**
 * Chronologie d'hypnogramme du jour [day] depuis les rows SLEEP_SLICE_* : une heure
 * ≥ 15:00 est interprétée comme LA VEILLE AU SOIR (la nuit appartient au matin du
 * réveil ; une sieste d'après-midi garde son propre jour). Triée par début — miroir
 * de `HealthUiAggregations.sleepPhaseTimeline` Android.
 */
export function sleepPhaseTimeline(metrics: HealthMetric[], day: string): SleepPhasePoint[] {
  return metrics
    .filter(
      (m) =>
        (SLEEP_SLICE_TYPES as readonly string[]).includes(m.type) && m.date === day && m.startTime,
    )
    .map((m) => {
      const [h, mm] = m.startTime!.split(':').map(Number);
      let startMin = (h || 0) * 60 + (Number.isNaN(mm) ? 0 : mm || 0);
      if (startMin >= 15 * 60) startMin -= 24 * 60; // la veille au soir
      return {
        startMin,
        minutes: Math.round(m.value),
        bucket: (SLEEP_SLICE_TYPES as readonly string[]).indexOf(m.type),
      };
    })
    .sort((a, b) => a.startMin - b.startMin);
}

/** Minutes depuis minuit (0..1439) → "HH:MM" (décode la value des rows SLEEP_SESSION). */
export function minutesOfDayToHhmm(value: number): string {
  const total = ((Math.round(value) % 1440) + 1440) % 1440;
  const h = Math.floor(total / 60);
  const m = total % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

/**
 * Sessions de sommeil du jour [day] (nuit rattachée au matin du réveil), triées par mise au lit :
 * { bedTime, asleepTime } "HH:MM". Alimente les lignes « Au lit à X · Endormi à Y » (parité Android).
 */
export function sleepSessionsForDay(
  metrics: HealthMetric[],
  day: string,
): { bedTime: string; asleepTime: string }[] {
  return metrics
    .filter((m) => m.type === SLEEP_SESSION_TYPE && m.date === day && m.startTime)
    .sort((a, b) => ((a.startTime ?? '') < (b.startTime ?? '') ? -1 : 1))
    .map((m) => ({ bedTime: m.startTime!, asleepTime: minutesOfDayToHhmm(m.value) }));
}

/**
 * Minutes par famille de phase `[profond, léger, paradoxal, éveillé]` pour chaque jour de [days]
 * (0 si absent) : alimente les barres empilées 7 j. `hasData` = au moins une phase renseignée
 * (sinon le chart retombe sur les totaux simples, parité Android).
 */
export function sleepStagesByDayCalendar(
  metrics: HealthMetric[],
  days: string[],
): { stacked: number[][]; hasData: boolean } {
  const byTypeDate = new Map<string, number>();
  for (const m of metrics) {
    if ((SLEEP_STAGE_TYPES as readonly string[]).includes(m.type)) {
      byTypeDate.set(`${m.type}|${m.date}`, m.value);
    }
  }
  const stacked = days.map((d) => SLEEP_STAGE_TYPES.map((t) => byTypeDate.get(`${t}|${d}`) ?? 0));
  return { stacked, hasData: stacked.some((row) => row.some((v) => v > 0)) };
}

/**
 * Valeurs d'un type de métrique intraday ventilées sur 48 tranches de 30 min (index 0 = 00:00 …
 * 47 = 23:30), à partir des rows portant un `startTime` "HH:MM". Slots sans mesure = 0. Une seule row
 * par tranche (uuid déterministe côté import). Alimente la série FC « aujourd'hui » (bpm moyen/tranche).
 */
export function metricBySlot(metrics: HealthMetric[], type: string, day: string): number[] {
  const bySlot = new Array<number>(SLOTS_PER_DAY).fill(0);
  for (const m of metrics) {
    if (m.type !== type || m.date !== day || !m.startTime) continue;
    const slot = slotOfBucketStart(m.startTime);
    if (slot !== null && slot >= 0 && slot < SLOTS_PER_DAY) bySlot[slot] = m.value;
  }
  return bySlot;
}

/** "HH:MM" du début de la tranche d'index [slot] (0..47) : 20 → "10:00", 21 → "10:30". */
export function slotHhmm(slot: number): string {
  const perHour = 60 / SLOT_MINUTES;
  const h = Math.floor(slot / perHour);
  const m = (slot % perHour) * SLOT_MINUTES;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

/**
 * Dernière tranche renseignée (valeur > 0) jusqu'à [currentSlot] inclus d'une série intraday :
 * { slot, value }, ou null si aucune. Alimente la ligne « X bpm · heure » (dernière mesure FC du jour).
 */
export function latestSlot(series: number[], currentSlot: number): { slot: number; value: number } | null {
  for (let i = Math.min(currentSlot, series.length - 1); i >= 0; i--) {
    if (series[i] > 0) return { slot: i, value: series[i] };
  }
  return null;
}

/**
 * Totaux de pas pour un ensemble de jours calendaires [days] (dans l'ordre fourni), 0 pour les
 * jours sans bucket → slots réservés (le chart 7 j garde toutes les positions alignées).
 */
export function stepsByDayCalendar(
  buckets: HealthStepCount[],
  days: string[],
): { date: string; value: number }[] {
  const byDate = new Map<string, number>();
  for (const b of buckets) byDate.set(b.date, (byDate.get(b.date) ?? 0) + b.steps);
  return days.map((d) => ({ date: d, value: byDate.get(d) ?? 0 }));
}

/**
 * Valeur d'un type de métrique pour des jours calendaires [days] (ordre fourni), 0 si absent.
 * Une date à plusieurs rows → la plus tardive (startTime max).
 */
export function metricByDayCalendar(
  metrics: HealthMetric[],
  type: string,
  days: string[],
): { date: string; value: number }[] {
  const byDate = new Map<string, HealthMetric>();
  for (const m of metrics) {
    if (m.type !== type) continue;
    const cur = byDate.get(m.date);
    if (!cur || (m.startTime ?? '') > (cur.startTime ?? '')) byDate.set(m.date, m);
  }
  return days.map((d) => ({ date: d, value: byDate.get(d)?.value ?? 0 }));
}

/** Dernière valeur d'un type de métrique (date max, puis startTime max), ou null. */
export function latestMetric(metrics: HealthMetric[], type: string): HealthMetric | null {
  let best: HealthMetric | null = null;
  for (const m of metrics) {
    if (m.type !== type) continue;
    if (
      !best ||
      m.date > best.date ||
      (m.date === best.date && (m.startTime ?? '') > (best.startTime ?? ''))
    ) {
      best = m;
    }
  }
  return best;
}

/**
 * Moyenne des jours renseignés (valeurs > 0) d'une série ; null si aucun. Exclut les slots vides/0
 * pour qu'un court historique n'écrase pas la moyenne. Alimente la ligne de repère du chart 7 jours.
 */
export function averageOfFilledDays(values: number[]): number | null {
  const filled = values.filter((v) => v > 0);
  if (filled.length === 0) return null;
  return filled.reduce((a, b) => a + b, 0) / filled.length;
}

/**
 * Objectif actif d'un `type` un jour [day] = le HealthGoal au plus grand `effectiveFrom` ≤ [day]
 * (même sémantique que NutritionGoal / muscle goals). null si aucun objectif défini pour ce jour.
 */
export function activeHealthGoal(
  goals: HealthGoal[],
  day: string,
  type = GOAL_TYPE_STEPS,
): HealthGoal | null {
  let active: HealthGoal | null = null;
  for (const g of goals) {
    if (g.type !== type || g.effectiveFrom > day) continue;
    if (!active || g.effectiveFrom > active.effectiveFrom) active = g;
  }
  return active;
}

/** Progression [0, 1] du total de pas vers l'objectif (0 si objectif ≤ 0 ou absent). */
export function stepProgress(steps: number, goalTarget: number | null | undefined): number {
  if (!goalTarget || goalTarget <= 0) return 0;
  return Math.max(0, Math.min(1, steps / goalTarget));
}

/** Les [count] derniers jours calendaires finissant à [today] ("YYYY-MM-DD"), du plus ancien au plus récent. */
export function weekDaysEndingToday(today: string, count: number): string[] {
  const [y, m, d] = today.split('-').map(Number);
  const days: string[] = [];
  for (let i = count - 1; i >= 0; i--) {
    const date = new Date(y, m - 1, d - i);
    const mm = String(date.getMonth() + 1).padStart(2, '0');
    const dd = String(date.getDate()).padStart(2, '0');
    days.push(`${date.getFullYear()}-${mm}-${dd}`);
  }
  return days;
}

/** Minutes → "X h Y min" (ou "Y min" sous une heure) — même format que le hub Android
 *  (`health_dash_sleep_value`). Pour l'affichage du sommeil. */
export function formatHoursMinutes(minutes: number): string {
  const total = Math.max(0, Math.round(minutes));
  const h = Math.floor(total / 60);
  const m = total % 60;
  return h > 0 ? `${h} h ${m} min` : `${m} min`;
}
