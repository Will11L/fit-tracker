import { LocalMeal } from '@core/models/meal.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import { LocalNutritionGoal } from '@core/models/nutrition-goal.model';
import { MacroTotals, ZERO_TOTALS, entryTotals } from './journal-utils';
import { activeGoalAt } from './nutrition-stats-utils';

/**
 * Les 4 macros affichées par les anneaux concentriques des cases du calendrier (kcal en anneau
 * extérieur + glucides / lipides / protéines en anneaux intérieurs). Les fibres sont exclues ici
 * (4 anneaux seulement, cf. tâche) — fibres restent dans le bandeau résumé.
 */
export type RingMacroKey = 'kcal' | 'carbs' | 'fat' | 'protein';

/** Ordre des anneaux du plus extérieur (kcal) au plus intérieur (protéines). */
export const RING_MACRO_KEYS: RingMacroKey[] = ['kcal', 'carbs', 'fat', 'protein'];

/** Cumuls + cibles + progression d'un jour pour les 4 anneaux d'une case du calendrier. */
export interface DayRingTotals {
  /** "YYYY-MM-DD". */
  date: string;
  /** Cumuls macros du jour (snapshots per-100g des entries du jour). */
  totals: MacroTotals;
  /** Vrai si au moins une entry existe ce jour-là (sinon anneaux gris/vides). */
  hasData: boolean;
  /** Cibles du jour (cible active à cette date) — null par macro si aucune cible. */
  targets: Record<RingMacroKey, number | null>;
  /** Progression 0..1 par macro (bornée) ; 0 si pas de cible (anneau sans remplissage). */
  progress: Record<RingMacroKey, number>;
}

/** Valeur consommée d'un macro d'anneau depuis des totaux jour. */
function consumedOf(totals: MacroTotals, key: RingMacroKey): number {
  return key === 'kcal'
    ? totals.kcal
    : key === 'carbs'
      ? totals.carbs
      : key === 'fat'
        ? totals.fat
        : totals.protein;
}

/** Cible d'un macro d'anneau depuis un goal (null si pas de goal). */
function targetOf(goal: LocalNutritionGoal | null, key: RingMacroKey): number | null {
  if (!goal) return null;
  return key === 'kcal' ? goal.kcal : key === 'carbs' ? goal.carbsG : key === 'fat' ? goal.fatG : goal.proteinG;
}

/**
 * Agrège, pour chaque jour de la liste fournie, les cumuls macros + la progression vs la cible
 * active de ce jour-là (activeGoalAt, §3.7). Jour sans entry → hasData=false (anneaux vides) ;
 * macro sans cible → progress 0 (anneau sans remplissage). Pure et testable hors DI — alimente
 * les cases du calendrier mensuel du Journal.
 *
 * @param dayIsos Jours à calculer (typiquement tous les jours du mois affiché).
 */
export function dailyTotalsForMonth(
  dayIsos: string[],
  entries: LocalMealEntry[],
  meals: LocalMeal[],
  goals: LocalNutritionGoal[],
): Map<string, DayRingTotals> {
  const mealById = new Map(meals.map((m) => [m.uuid, m]));

  // Cumuls + nombre d'entries par jour.
  const totalsByDay = new Map<string, MacroTotals>();
  const countByDay = new Map<string, number>();
  for (const e of entries) {
    const m = mealById.get(e.mealUUID);
    if (!m) continue;
    const acc = totalsByDay.get(m.date) ?? { ...ZERO_TOTALS };
    const t = entryTotals(e);
    acc.kcal += t.kcal;
    acc.protein += t.protein;
    acc.carbs += t.carbs;
    acc.fat += t.fat;
    acc.fiber += t.fiber;
    totalsByDay.set(m.date, acc);
    countByDay.set(m.date, (countByDay.get(m.date) ?? 0) + 1);
  }

  const out = new Map<string, DayRingTotals>();
  for (const date of dayIsos) {
    const totals = totalsByDay.get(date) ?? { ...ZERO_TOTALS };
    const hasData = (countByDay.get(date) ?? 0) > 0;
    const goal = activeGoalAt(goals, date);
    const targets = {} as Record<RingMacroKey, number | null>;
    const progress = {} as Record<RingMacroKey, number>;
    for (const key of RING_MACRO_KEYS) {
      const target = targetOf(goal, key);
      targets[key] = target;
      progress[key] = target && target > 0 ? Math.min(1, consumedOf(totals, key) / target) : 0;
    }
    out.set(date, { date, totals, hasData, targets, progress });
  }
  return out;
}
