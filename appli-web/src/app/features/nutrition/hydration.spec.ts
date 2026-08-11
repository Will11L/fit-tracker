import { LocalHealthGoal } from '@core/models/health-goal.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import { LocalWaterIntake } from '@core/models/water-intake.model';
import {
  activeWaterGoalMl,
  dayHydrationMl,
  detectWaterFromOffCategories,
  formatLiters,
  manualWaterMl,
  mealWaterMl,
} from './hydration';

/** Logique pure de l'Hydratation (2026-07-05) — miroir de HydrationDomainTest.kt. */

function intake(uuid: string, date: string, ml: number): LocalWaterIntake {
  return { uuid, userId: 1, date, amountMl: ml, createdAt: `${date}T08:00:00Z`, updatedAt: null, synced: true, pendingDeletion: false };
}
function entry(uuid: string, mealUUID: string, foodUUID: string | null, grams: number): LocalMealEntry {
  return {
    uuid, mealUUID, foodUUID, recipeUUID: null, displayName: 'x', quantityG: grams, portionLabel: null,
    kcalPer100g: 0, proteinPer100g: 0, carbsPer100g: 0, fatPer100g: 0,
    fiberPer100g: null, sugarPer100g: null, satFatPer100g: null, saltPer100g: null,
    ironPer100g: null, calciumPer100g: null, magnesiumPer100g: null, zincPer100g: null,
    potassiumPer100g: null, sodiumPer100g: null, vitaminCPer100g: null, vitaminDPer100g: null,
    vitaminB12Per100g: null, vitaminAPer100g: null,
    updatedAt: null, synced: true, pendingDeletion: false,
  } as LocalMealEntry;
}
function goal(uuid: string, type: string, target: number, from: string): LocalHealthGoal {
  return { uuid, userId: 1, type, target, effectiveFrom: from, updatedAt: null, synced: true, pendingDeletion: false };
}

describe('hydration (logique pure)', () => {
  it('détecte une eau via la famille de catégories OFF', () => {
    expect(detectWaterFromOffCategories(['en:beverages', 'en:waters'])).toBe(true);
    expect(detectWaterFromOffCategories(['en:mineral-waters'])).toBe(true);
    expect(detectWaterFromOffCategories(['fr:eaux', 'en:sparkling-waters'])).toBe(true);
  });

  it('ne flague pas les catégories non-eau', () => {
    expect(detectWaterFromOffCategories(['en:sodas', 'en:beverages'])).toBe(false);
    expect(detectWaterFromOffCategories([])).toBe(false);
    expect(detectWaterFromOffCategories(['en:waterfowls'])).toBe(false);
  });

  it('somme les prises manuelles du jour sélectionné', () => {
    const intakes = [intake('a', '2026-07-05', 250), intake('b', '2026-07-05', 500), intake('c', '2026-07-04', 1000)];
    expect(manualWaterMl(intakes, '2026-07-05')).toBe(750);
  });

  it('compte les entrées eau des repas du jour (1 g = 1 ml)', () => {
    const dayMeals = new Set(['m1']);
    const entries = [
      entry('e1', 'm1', 'water', 500),
      entry('e2', 'm1', 'solid', 200),
      entry('e3', 'other', 'water', 300),
      entry('e4', 'm1', null, 100),
    ];
    expect(mealWaterMl(dayMeals, entries, new Set(['water']))).toBe(500);
  });

  it('total = prises manuelles + entrées eau', () => {
    const total = dayHydrationMl('2026-07-05', [intake('a', '2026-07-05', 250)], new Set(['m1']), [entry('e1', 'm1', 'w', 750)], new Set(['w']));
    expect(total).toBe(1000);
  });

  it('objectif WATER_ML actif = plus grand effectiveFrom ≤ jour, du bon type', () => {
    const goals = [goal('g1', 'WATER_ML', 2000, '2026-07-01'), goal('g2', 'WATER_ML', 2500, '2026-07-05'), goal('g3', 'STEPS', 10000, '2026-07-05')];
    expect(activeWaterGoalMl(goals, '2026-07-05')).toBe(2500);
    expect(activeWaterGoalMl(goals, '2026-07-03')).toBe(2000);
    expect(activeWaterGoalMl(goals, '2026-06-30')).toBeNull();
  });

  it('formate les litres compacts', () => {
    expect(formatLiters(2000)).toBe('2');
    expect(formatLiters(1250)).toBe('1.25');
    expect(formatLiters(500)).toBe('0.5');
  });
});
