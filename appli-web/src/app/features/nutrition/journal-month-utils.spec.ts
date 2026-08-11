import { LocalMeal } from '@core/models/meal.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import { LocalNutritionGoal } from '@core/models/nutrition-goal.model';
import { RING_MACRO_KEYS, dailyTotalsForMonth } from './journal-month-utils';

function entry(over: Partial<LocalMealEntry>): LocalMealEntry {
  return {
    uuid: 'e1',
    mealUUID: 'm1',
    foodUUID: 'f1',
    recipeUUID: null,
    displayName: 'Aliment',
    quantityG: 100,
    portionLabel: null,
    kcalPer100g: 0,
    proteinPer100g: 0,
    carbsPer100g: 0,
    fatPer100g: 0,
    fiberPer100g: null,
    sugarPer100g: null,
    satFatPer100g: null,
    saltPer100g: null,
    ironPer100g: null,
    calciumPer100g: null,
    magnesiumPer100g: null,
    zincPer100g: null,
    potassiumPer100g: null,
    sodiumPer100g: null,
    vitaminCPer100g: null,
    vitaminDPer100g: null,
    vitaminB12Per100g: null,
    vitaminAPer100g: null,
    updatedAt: null,
    synced: true,
    pendingDeletion: false,
    ...over,
  };
}

function meal(over: Partial<LocalMeal>): LocalMeal {
  return {
    uuid: 'm1',
    userId: 1,
    date: '2026-06-12',
    name: 'Déjeuner',
    orderIndex: 1,
    presetUuid: null,
    updatedAt: null,
    synced: true,
    pendingDeletion: false,
    ...over,
  };
}

function goal(over: Partial<LocalNutritionGoal>): LocalNutritionGoal {
  return {
    uuid: 'g1',
    userId: 1,
    effectiveFrom: '2026-01-01',
    dayKind: 'ALL',
    kcal: 2000,
    proteinG: 150,
    carbsG: 200,
    fatG: 70,
    updatedAt: null,
    synced: true,
    pendingDeletion: false,
    ...over,
  };
}

describe('dailyTotalsForMonth — agrégation mensuelle pour les anneaux du calendrier', () => {
  it('expose les 4 macros d’anneau dans l’ordre kcal → glucides → lipides → protéines', () => {
    expect(RING_MACRO_KEYS).toEqual(['kcal', 'carbs', 'fat', 'protein']);
  });

  it('un jour sans entry → hasData=false et progression nulle', () => {
    const map = dailyTotalsForMonth(['2026-06-10', '2026-06-12'], [], [], [goal({})]);
    const d = map.get('2026-06-10')!;
    expect(d.hasData).toBe(false);
    expect(d.totals.kcal).toBe(0);
    expect(d.progress.kcal).toBe(0);
  });

  it('cumule les snapshots per-100g du jour (total = per100g × q / 100)', () => {
    const meals = [meal({ uuid: 'm1', date: '2026-06-12' })];
    const entries = [
      // 500 kcal, 50 G, 20 L, 40 P
      entry({ uuid: 'e1', mealUUID: 'm1', kcalPer100g: 250, carbsPer100g: 25, fatPer100g: 10, proteinPer100g: 20, quantityG: 200 }),
    ];
    const map = dailyTotalsForMonth(['2026-06-12'], entries, meals, []);
    const d = map.get('2026-06-12')!;
    expect(d.hasData).toBe(true);
    expect(d.totals.kcal).toBeCloseTo(500);
    expect(d.totals.carbs).toBeCloseTo(50);
    expect(d.totals.fat).toBeCloseTo(20);
    expect(d.totals.protein).toBeCloseTo(40);
  });

  it('progression = consommé / cible active du jour, bornée à 1', () => {
    const meals = [meal({ uuid: 'm1', date: '2026-06-12' })];
    const entries = [
      // kcal = 1000 (sous cible 2000 → 0.5), carbs = 400 (au-dessus de 200 → borné 1)
      entry({ uuid: 'e1', mealUUID: 'm1', kcalPer100g: 1000, carbsPer100g: 400, quantityG: 100 }),
    ];
    const map = dailyTotalsForMonth(['2026-06-12'], entries, meals, [goal({})]);
    const d = map.get('2026-06-12')!;
    expect(d.progress.kcal).toBeCloseTo(0.5);
    expect(d.progress.carbs).toBe(1); // borné
  });

  it('jour sans cible → progression 0 même avec des aliments (anneau sans remplissage)', () => {
    const meals = [meal({ uuid: 'm1', date: '2026-06-12' })];
    const entries = [entry({ uuid: 'e1', mealUUID: 'm1', kcalPer100g: 500, quantityG: 100 })];
    const map = dailyTotalsForMonth(['2026-06-12'], entries, meals, []); // aucun goal
    const d = map.get('2026-06-12')!;
    expect(d.hasData).toBe(true);
    expect(d.targets.kcal).toBeNull();
    expect(d.progress.kcal).toBe(0);
  });

  it('utilise la cible ACTIVE du jour (effectiveFrom ≤ jour, le plus récent)', () => {
    const meals = [meal({ uuid: 'm1', date: '2026-06-12' })];
    const entries = [entry({ uuid: 'e1', mealUUID: 'm1', kcalPer100g: 1500, quantityG: 100 })];
    const goals = [
      goal({ uuid: 'g-old', effectiveFrom: '2026-01-01', kcal: 3000 }),
      goal({ uuid: 'g-new', effectiveFrom: '2026-06-01', kcal: 1500 }),
    ];
    const map = dailyTotalsForMonth(['2026-06-12'], entries, meals, goals);
    const d = map.get('2026-06-12')!;
    expect(d.targets.kcal).toBe(1500); // la plus récente active
    expect(d.progress.kcal).toBe(1); // 1500 / 1500
  });

  it('ignore les entries dont le repas parent est absent', () => {
    const entries = [entry({ uuid: 'e1', mealUUID: 'orphan', kcalPer100g: 500, quantityG: 100 })];
    const map = dailyTotalsForMonth(['2026-06-12'], entries, [], []);
    expect(map.get('2026-06-12')!.hasData).toBe(false);
  });
});
