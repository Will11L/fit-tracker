import { LocalMeal } from '@core/models/meal.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import { LocalNutritionGoal } from '@core/models/nutrition-goal.model';
import { addDays, entryTotals } from './journal-utils';
import { MacroKey, MacroTargetKey, MACRO_COLOR, MACRO_KEYS, MACRO_LABEL } from './macro-colors';
import { FIBER_G_PER_1000_KCAL } from './goal-macros';

/**
 * Une ligne du « Top aliments » de la page Stats : un aliment (regroupé par ref stable food/recipe,
 * repli sur le displayName) avec sa somme du macro sélectionné sur la période + sa part du total.
 */
export interface TopFood {
  /** Clé de regroupement (foodUUID ?? recipeUUID ?? displayName). */
  key: string;
  displayName: string;
  /** Somme du macro sur la période (kcal ou g). */
  value: number;
  /** Part du total de la période, 0..1 (0 si total nul). */
  share: number;
}

/**
 * Classe les aliments par apport décroissant pour un macro sur [startIso, endIso] inclus. Agrège par
 * aliment (ref stable food/recipe, repli sur le nom affiché), somme le macro dérivé du snapshot
 * per-100g, ignore les apports ≤ 0 et les entries dont le repas parent est absent/hors période.
 * Pure (testable hors DI) — alimente la liste « Top aliments » de la page Stats.
 */
export function topFoodsByMacro(
  entries: LocalMealEntry[],
  meals: LocalMeal[],
  startIso: string,
  endIso: string,
  macroKey: MacroKey,
): TopFood[] {
  const mealById = new Map(meals.map((m) => [m.uuid, m]));
  const acc = new Map<string, { displayName: string; value: number }>();
  let total = 0;
  for (const e of entries) {
    const m = mealById.get(e.mealUUID);
    if (!m || m.date < startIso || m.date > endIso) continue;
    const v = entryTotals(e)[macroKey];
    if (v <= 0) continue;
    const key = e.foodUUID ?? e.recipeUUID ?? e.displayName;
    const cur = acc.get(key) ?? { displayName: e.displayName, value: 0 };
    cur.value += v;
    acc.set(key, cur);
    total += v;
  }
  const out: TopFood[] = [];
  for (const [key, { displayName, value }] of acc) {
    out.push({ key, displayName, value, share: total > 0 ? value / total : 0 });
  }
  out.sort((a, b) => b.value - a.value || a.displayName.localeCompare(b.displayName));
  return out;
}

export type StatGran = 'DAILY' | 'WEEKLY';

/**
 * Agrégat de la page Stats Nutrition : pour chaque bucket (jour ou semaine selon la granularité),
 * la consommation par macro + la cible cumulée. La cible d'un bucket = Σ sur chaque jour du bucket
 * de la cible **active ce jour-là** (effective_from, §3.7) → la vue semaine compare `Σ jours` à
 * `cible×nb_jours` (D8 : pas de goal hebdo dédié). Les fibres n'ont pas de cible (D11).
 */
export interface NutritionAggregate {
  buckets: string[];
  consumed: Record<MacroKey, number[]>;
  target: Record<MacroTargetKey, number[]>;
}

/**
 * Cible active à une date donnée parmi une liste de goals = celle au plus grand effectiveFrom ≤ date
 * (§3.7). Pure (miroir de NutritionGoalRepository.activeGoalFor pour usage hors DI).
 */
export function activeGoalAt(
  goals: LocalNutritionGoal[],
  date: string,
): LocalNutritionGoal | null {
  let active: LocalNutritionGoal | null = null;
  for (const g of goals) {
    if (g.effectiveFrom <= date && (!active || g.effectiveFrom > active.effectiveFrom)) active = g;
  }
  return active;
}

/** Bucket semaine équivalent SQLite strftime('%Y-%W') : 00-53, lundi premier jour (miroir Stats sport). */
export function weekBucket(day: string): string {
  const [y, m, d] = day.split('-').map(Number);
  const date = new Date(y, m - 1, d);
  const jan1 = new Date(date.getFullYear(), 0, 1);
  const dayOfYear = Math.round((date.getTime() - jan1.getTime()) / 86400000) + 1;
  const mondayBased = (date.getDay() + 6) % 7;
  const week = Math.floor((dayOfYear - 1 - mondayBased + 7) / 7);
  return `${date.getFullYear()}-${week.toString().padStart(2, '0')}`;
}

export function bucketOf(day: string, gran: StatGran): string {
  return gran === 'DAILY' ? day : weekBucket(day);
}

/** Borne basse réelle des données = date du repas le plus ancien (pour clamper la période « Tout »). */
export function earliestMealDate(meals: LocalMeal[]): string | null {
  let min: string | null = null;
  for (const m of meals) if (min === null || m.date < min) min = m.date;
  return min;
}

/**
 * Agrège consommation + cibles par bucket sur [startIso, endIso] inclus. Énumère chaque jour de la
 * période (buckets continus, jours à 0 inclus) pour que la courbe de cible reste continue même les
 * jours sans aliment saisi.
 */
/**
 * Profil macro moyen par jour sur la période (radar Stats Nutrition) : moyenne /jour de la
 * consommation (kcal + 4 macros) + moyenne /jour de la cible (kcal + 3 macros) si une cible était
 * active sur la période (sinon `target` null → radar « profil consommé » brut). `dayCount` = nombre
 * de jours calendaires de la période ; les jours sans saisie comptent comme 0 (même convention que
 * la moyenne /jour de la page Objectifs). Le ratio consommé/cible est invariant par la division
 * (les jours s'annulent), donc l'option « consommé vs cible » du radar reste juste. Pure & testable.
 */
export interface PeriodMacroProfile {
  consumed: { kcal: number; protein: number; carbs: number; fat: number; fiber: number };
  target: { kcal: number; protein: number; carbs: number; fat: number } | null;
}

export function periodMacroProfile(agg: NutritionAggregate, dayCount: number): PeriodMacroProfile {
  const d = Math.max(1, dayCount);
  const sum = (arr: number[]): number => arr.reduce((s, v) => s + v, 0);
  const tKcal = sum(agg.target.kcal);
  return {
    consumed: {
      kcal: sum(agg.consumed.kcal) / d,
      protein: sum(agg.consumed.protein) / d,
      carbs: sum(agg.consumed.carbs) / d,
      fat: sum(agg.consumed.fat) / d,
      fiber: sum(agg.consumed.fiber) / d,
    },
    target:
      tKcal > 0
        ? {
            kcal: tKcal / d,
            protein: sum(agg.target.protein) / d,
            carbs: sum(agg.target.carbs) / d,
            fat: sum(agg.target.fat) / d,
          }
        : null,
  };
}

export function aggregateNutrition(
  entries: LocalMealEntry[],
  meals: LocalMeal[],
  goals: LocalNutritionGoal[],
  startIso: string,
  endIso: string,
  gran: StatGran,
): NutritionAggregate {
  const mealById = new Map(meals.map((m) => [m.uuid, m]));

  // Consommation par jour (totaux dérivés du snapshot per-100g).
  const consumedByDay = new Map<string, Record<MacroKey, number>>();
  for (const e of entries) {
    const m = mealById.get(e.mealUUID);
    if (!m || m.date < startIso || m.date > endIso) continue;
    const acc = consumedByDay.get(m.date) ?? { kcal: 0, protein: 0, carbs: 0, fat: 0, fiber: 0 };
    const t = entryTotals(e);
    acc.kcal += t.kcal;
    acc.protein += t.protein;
    acc.carbs += t.carbs;
    acc.fat += t.fat;
    acc.fiber += t.fiber;
    consumedByDay.set(m.date, acc);
  }

  const buckets: string[] = [];
  const index = new Map<string, number>();
  const consumed: Record<MacroKey, number[]> = { kcal: [], protein: [], carbs: [], fat: [], fiber: [] };
  const target: Record<MacroTargetKey, number[]> = { kcal: [], protein: [], carbs: [], fat: [] };

  for (let d = startIso; d <= endIso; d = addDays(d, 1)) {
    const b = bucketOf(d, gran);
    let idx = index.get(b);
    if (idx === undefined) {
      idx = buckets.length;
      index.set(b, idx);
      buckets.push(b);
      consumed.kcal[idx] = 0;
      consumed.protein[idx] = 0;
      consumed.carbs[idx] = 0;
      consumed.fat[idx] = 0;
      consumed.fiber[idx] = 0;
      target.kcal[idx] = 0;
      target.protein[idx] = 0;
      target.carbs[idx] = 0;
      target.fat[idx] = 0;
    }
    const c = consumedByDay.get(d);
    if (c) {
      consumed.kcal[idx] += c.kcal;
      consumed.protein[idx] += c.protein;
      consumed.carbs[idx] += c.carbs;
      consumed.fat[idx] += c.fat;
      consumed.fiber[idx] += c.fiber;
    }
    const g = activeGoalAt(goals, d);
    if (g) {
      target.kcal[idx] += g.kcal;
      target.protein[idx] += g.proteinG;
      target.carbs[idx] += g.carbsG;
      target.fat[idx] += g.fatG;
    }
  }

  return { buckets, consumed, target };
}

/** Étiquette X d'un bucket pour les graphes Stats : 'YYYY-MM-DD' → 'J/M' (jour) ou 'YYYY-WW' → 'W##'
 *  (semaine). Pure (miroir du formatage interne de NutritionStatsChart, pour les graphes sans
 *  formatage X intégré comme MultiLineChart). */
export function formatBucketLabel(bucket: string, gran: StatGran): string {
  if (gran === 'DAILY') {
    const [, m, d] = bucket.split('-');
    return m && d ? `${Number(d)}/${Number(m)}` : bucket;
  }
  const week = Number(bucket.slice(bucket.lastIndexOf('-') + 1));
  return Number.isNaN(week) ? bucket : `W${week}`;
}

/**
 * Une série « % de l'objectif » pour le graphe multi-lignes de synthèse (1 par macro) : structurée
 * comme `LineSeries` du composant DS (name/color/data) + la clé macro pour le suivi. `data` peut
 * porter des `null` (bucket sans objectif actif → point sauté, pas de 100 % fantôme).
 */
export interface MacroPercentSeries {
  key: MacroKey;
  /** Libellé de légende (= MACRO_LABEL). */
  name: string;
  /** Token couleur macro (`var(--macro-*)`), résolu par le composant graphe. */
  color: string;
  /** % consommé/cible par bucket (1 décimale) ; `null` = pas d'objectif actif ce bucket (sauté). */
  data: (number | null)[];
}

/**
 * Construit les 5 séries « % de l'objectif » (1 par macro, ordre MACRO_KEYS) pour le 6e graphe de
 * synthèse multi-lignes : pour chaque bucket, `pct = consommé / cible × 100` (1 décimale). La cible
 * par bucket vient de l'agrégat (`activeGoalAt` par jour, §3.7) ; les fibres dérivent leur cible du
 * kcal cible (FIBER_G_PER_1000_KCAL, comme les cartes). Si la cible d'un bucket est nulle/absente
 * (pas d'objectif actif), le point est `null` (sauté) → aucune division par zéro, pas de 100 %
 * fantôme. Aucun objectif actif sur toute la période → `[]` (le composant affiche son état vide).
 * Pure & testable.
 */
export function macroPercentSeries(agg: NutritionAggregate): MacroPercentSeries[] {
  const n = agg.buckets.length;
  const series: MacroPercentSeries[] = MACRO_KEYS.map((key) => {
    const consumed = agg.consumed[key];
    const data: (number | null)[] = [];
    for (let i = 0; i < n; i++) {
      const target =
        key === 'fiber'
          ? (agg.target.kcal[i] / 1000) * FIBER_G_PER_1000_KCAL
          : agg.target[key as MacroTargetKey][i];
      data.push(target > 0 ? Math.round((consumed[i] / target) * 1000) / 10 : null);
    }
    return { key, name: MACRO_LABEL[key], color: MACRO_COLOR[key], data };
  });
  // Aucun objectif actif sur toute la période → rien à comparer : état vide (pas de courbe fantôme).
  const anyValue = series.some((s) => s.data.some((v) => v !== null));
  return anyValue ? series : [];
}
