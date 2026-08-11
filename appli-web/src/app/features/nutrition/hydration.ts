/**
 * Logique pure de l'Hydratation (2026-07-05) — miroir web de HydrationDomain.kt (Android).
 *
 * Total du jour = prises manuelles (water_intakes) + boissons eau journalisées
 * (meal_entries dont l'aliment est `isWater`, converties 1 g = 1 ml). Ce total est
 * CALCULÉ (aucune row matérialisée pour les entrées repas) : retirer une entrée
 * retire mécaniquement son volume. L'objectif journalier est un HealthGoal
 * `type = WATER_ML` versionné par effectiveFrom.
 */
import { LocalHealthGoal } from '@core/models/health-goal.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import { LocalWaterIntake } from '@core/models/water-intake.model';

export const WATER_GOAL_TYPE = 'WATER_ML';

/**
 * Un aliment OFF est de l'eau si l'une de ses `categoriesTags` appartient à la
 * famille OFF « waters » (`en:waters` et sous-catégories : mineral-waters,
 * spring-waters, sparkling-waters…). Heuristique posée à l'import (le flag
 * `Food.isWater` est ensuite la source de vérité, éditable manuellement).
 */
export function detectWaterFromOffCategories(categoriesTags: readonly string[]): boolean {
  return categoriesTags.some((tag) => {
    const slug = tag.includes(':') ? tag.slice(tag.indexOf(':') + 1) : tag;
    return slug === 'waters' || slug.endsWith('-waters') || slug.endsWith('-water');
  });
}

/** Objectif d'hydratation actif un jour J (ml) = HealthGoal WATER_ML au plus grand
 *  effectiveFrom ≤ J. null si aucun objectif défini. */
export function activeWaterGoalMl(goals: readonly LocalHealthGoal[], day: string): number | null {
  let active: LocalHealthGoal | null = null;
  for (const g of goals) {
    if (g.type === WATER_GOAL_TYPE && !g.pendingDeletion && g.effectiveFrom <= day) {
      if (!active || g.effectiveFrom > active.effectiveFrom) active = g;
    }
  }
  return active ? Math.round(active.target) : null;
}

/** Volume issu des prises manuelles (water_intakes) du jour (ml). */
export function manualWaterMl(intakes: readonly LocalWaterIntake[], day: string): number {
  let ml = 0;
  for (const w of intakes) {
    if (!w.pendingDeletion && w.date === day) ml += w.amountMl;
  }
  return ml;
}

/** Volume issu des entrées repas « eau » du jour (1 g = 1 ml). `waterFoodUuids` =
 *  uuids des aliments marqués isWater ; `dayMealUuids` = repas du jour affiché. */
export function mealWaterMl(
  dayMealUuids: ReadonlySet<string>,
  entries: readonly LocalMealEntry[],
  waterFoodUuids: ReadonlySet<string>,
): number {
  let grams = 0;
  for (const e of entries) {
    if (
      !e.pendingDeletion &&
      dayMealUuids.has(e.mealUUID) &&
      e.foodUUID != null &&
      waterFoodUuids.has(e.foodUUID)
    ) {
      grams += e.quantityG;
    }
  }
  return Math.round(grams);
}

/** Total d'hydratation du jour (ml) = prises manuelles + boissons eau journalisées. */
export function dayHydrationMl(
  day: string,
  intakes: readonly LocalWaterIntake[],
  dayMealUuids: ReadonlySet<string>,
  entries: readonly LocalMealEntry[],
  waterFoodUuids: ReadonlySet<string>,
): number {
  return manualWaterMl(intakes, day) + mealWaterMl(dayMealUuids, entries, waterFoodUuids);
}

/** ml → litres compacts (« 1,25 » sans zéros superflus, « 2 » si entier). */
export function formatLiters(ml: number): string {
  const liters = ml / 1000;
  if (Number.isInteger(liters)) return String(liters);
  return String(Math.round(liters * 100) / 100);
}
