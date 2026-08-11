import { LocalMeal } from '@core/models/meal.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import { LocalNutritionGoal } from '@core/models/nutrition-goal.model';
import { aggregateNutrition, periodMacroProfile } from './nutrition-stats-utils';
import { macroRadarData, type MacroAmounts, type MacroTargets } from './nutrition-summary-panel';
import { FIBER_G_PER_1000_KCAL } from './goal-macros';

/**
 * Radar du profil macro de la période (Stats Nutrition) : helper pur `periodMacroProfile` (moyenne
 * /jour consommée + cible) puis composition avec `macroRadarData` (déjà testé). On verrouille les 2
 * cas : sans cible active (1 série « profil consommé » brut) et avec cible active (2 séries
 * consommé vs objectif).
 */

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

describe('periodMacroProfile — profil macro moyen /jour de la période', () => {
  // 1 jour, 1 aliment 100 g : P40 / G50 / L20 / fibres 8, kcal arbitraire 600.
  const meals = [meal({ uuid: 'm1', date: '2026-06-12' })];
  const entries = [
    entry({
      mealUUID: 'm1',
      kcalPer100g: 600,
      proteinPer100g: 40,
      carbsPer100g: 50,
      fatPer100g: 20,
      fiberPer100g: 8,
      quantityG: 100,
    }),
  ];

  it('sans cible active → target null, consommé = totaux / nb de jours', () => {
    const agg = aggregateNutrition(entries, meals, [], '2026-06-12', '2026-06-13', 'DAILY');
    // 2 jours dans la période, consommation sur 1 seul → moyenne = total / 2.
    const p = periodMacroProfile(agg, 2);
    expect(p.target).toBeNull();
    expect(p.consumed.protein).toBeCloseTo(40 / 2, 5);
    expect(p.consumed.carbs).toBeCloseTo(50 / 2, 5);
    expect(p.consumed.fat).toBeCloseTo(20 / 2, 5);
    expect(p.consumed.fiber).toBeCloseTo(8 / 2, 5);
    expect(p.consumed.kcal).toBeCloseTo(600 / 2, 5);
  });

  it('avec cible active → target = cible moyenne /jour (kcal + 3 macros)', () => {
    const agg = aggregateNutrition(entries, meals, [goal({})], '2026-06-12', '2026-06-12', 'DAILY');
    const p = periodMacroProfile(agg, 1);
    expect(p.target).not.toBeNull();
    expect(p.target!.kcal).toBeCloseTo(2000, 5);
    expect(p.target!.protein).toBeCloseTo(150, 5);
    expect(p.target!.carbs).toBeCloseTo(200, 5);
    expect(p.target!.fat).toBeCloseTo(70, 5);
  });

  it('dayCount ≤ 0 traité comme 1 (jamais de division par zéro)', () => {
    const agg = aggregateNutrition(entries, meals, [], '2026-06-12', '2026-06-12', 'DAILY');
    const p = periodMacroProfile(agg, 0);
    expect(p.consumed.protein).toBeCloseTo(40, 5); // total / 1
  });
});

describe('Radar profil période — composition periodMacroProfile + macroRadarData', () => {
  const meals = [meal({ uuid: 'm1', date: '2026-06-12' })];
  const entries = [
    entry({
      mealUUID: 'm1',
      kcalPer100g: 2000,
      proteinPer100g: 150,
      carbsPer100g: 200,
      fatPer100g: 70,
      fiberPer100g: 30,
      quantityG: 100,
    }),
  ];

  it('cible active → 2 séries (consommé % objectif + repère « Objectif » à 100)', () => {
    const agg = aggregateNutrition(entries, meals, [goal({})], '2026-06-12', '2026-06-12', 'DAILY');
    const p = periodMacroProfile(agg, 1);
    const macros: MacroAmounts = p.consumed;
    const targets: MacroTargets = {
      ...p.target!,
      fiber: (p.target!.kcal / 1000) * FIBER_G_PER_1000_KCAL,
    };
    const { axes, series } = macroRadarData(macros, targets);
    expect(axes.map((a) => a.label)).toEqual(['Glucides', 'Lipides', 'Protéines', 'Fibres']);
    expect(series.length).toBe(2);
    // Consommé = cible exactement (fibres incluses : 30 g = 2000/1000 × 15) → 100 % partout.
    expect(series[0].values).toEqual([100, 100, 100, 100]); // carbs/fat/protein/fiber
    expect(series[1].name).toBe('Objectif');
    expect(series[1].values).toEqual([100, 100, 100, 100]);
  });

  it('sans cible → 1 série « Profil » de valeurs brutes (g)', () => {
    const agg = aggregateNutrition(entries, meals, [], '2026-06-12', '2026-06-12', 'DAILY');
    const p = periodMacroProfile(agg, 1);
    const { series } = macroRadarData(p.consumed, null);
    expect(series.length).toBe(1);
    expect(series[0].name).toBe('Profil');
    expect(series[0].values).toEqual([200, 70, 150, 30]); // carbs/fat/protein/fiber
  });
});
