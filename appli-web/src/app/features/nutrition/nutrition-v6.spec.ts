import { LocalMeal } from '@core/models/meal.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import { LocalNutritionGoal } from '@core/models/nutrition-goal.model';
import { aggregateNutrition, activeGoalAt, earliestMealDate, topFoodsByMacro, weekBucket } from './nutrition-stats-utils';
import { computeBounds } from './range-utils';

function entry(over: Partial<LocalMealEntry>): LocalMealEntry {
  return {
    uuid: 'e1',
    mealUUID: 'm1',
    foodUUID: 'f1',
    recipeUUID: null,
    displayName: 'Œuf',
    quantityG: 100,
    portionLabel: null,
    kcalPer100g: 150,
    proteinPer100g: 13,
    carbsPer100g: 1,
    fatPer100g: 10,
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

describe('Nutrition V6 — Top aliments par macro (topFoodsByMacro)', () => {
  const meals = [
    meal({ uuid: 'm1', date: '2026-06-12', name: 'Déjeuner' }),
    meal({ uuid: 'm2', date: '2026-06-10', name: 'Petit-déj' }),
    meal({ uuid: 'm3', date: '2026-05-01', name: 'Hors période' }),
  ];
  const entries = [
    // Poulet sur 2 jours -> doit s'agréger par foodUUID en une seule ligne.
    entry({ uuid: 'e1', mealUUID: 'm1', foodUUID: 'f-poulet', displayName: 'Poulet', kcalPer100g: 165, proteinPer100g: 31, fatPer100g: 3.6, carbsPer100g: 0, quantityG: 200 }),
    entry({ uuid: 'e2', mealUUID: 'm2', foodUUID: 'f-poulet', displayName: 'Poulet', kcalPer100g: 165, proteinPer100g: 31, fatPer100g: 3.6, carbsPer100g: 0, quantityG: 100 }),
    entry({ uuid: 'e3', mealUUID: 'm2', foodUUID: 'f-avoine', displayName: 'Avoine', kcalPer100g: 380, proteinPer100g: 13, carbsPer100g: 60, fatPer100g: 7, fiberPer100g: 10, quantityG: 50 }),
    // Hors période + orphelin : exclus.
    entry({ uuid: 'e4', mealUUID: 'm3', foodUUID: 'f-poulet', displayName: 'Poulet', kcalPer100g: 165, proteinPer100g: 31, quantityG: 500 }),
    entry({ uuid: 'e5', mealUUID: 'orphan', foodUUID: 'f-x', displayName: 'Fantôme', proteinPer100g: 99, quantityG: 100 }),
  ];

  it('agrège par aliment, somme le macro sur la période, trie décroissant + part %', () => {
    const top = topFoodsByMacro(entries, meals, '2026-06-01', '2026-06-30', 'protein');
    // Poulet (300 g -> 93 g prot) devant Avoine (50 g -> 6.5 g prot). Hors période + orphelin écartés.
    expect(top.map((f) => f.displayName)).toEqual(['Poulet', 'Avoine']);
    expect(top[0].value).toBeCloseTo(93); // 31 × 300/100
    expect(top[1].value).toBeCloseTo(6.5); // 13 × 50/100
    expect(top[0].share).toBeCloseTo(93 / 99.5);
    expect(top[0].share + top[1].share).toBeCloseTo(1);
  });

  it('ignore les apports nuls (macro à 0) pour le macro demandé', () => {
    // Glucides : Poulet = 0 g -> seule l'Avoine apparaît.
    const top = topFoodsByMacro(entries, meals, '2026-06-01', '2026-06-30', 'carbs');
    expect(top.map((f) => f.displayName)).toEqual(['Avoine']);
    expect(top[0].share).toBeCloseTo(1);
  });

  it('clé de regroupement : repli recipeUUID puis displayName quand foodUUID absent', () => {
    // 2 entries même recette (sans foodUUID) -> 1 ligne ; 1 entry sans food ni recipe -> clé = displayName.
    const m = [meal({ uuid: 'm1', date: '2026-06-12' })];
    const es = [
      entry({ uuid: 'r1', mealUUID: 'm1', foodUUID: null, recipeUUID: 'rec-1', displayName: 'Curry', proteinPer100g: 10, quantityG: 100 }),
      entry({ uuid: 'r2', mealUUID: 'm1', foodUUID: null, recipeUUID: 'rec-1', displayName: 'Curry', proteinPer100g: 10, quantityG: 100 }),
      entry({ uuid: 'd1', mealUUID: 'm1', foodUUID: null, recipeUUID: null, displayName: 'Snack libre', proteinPer100g: 5, quantityG: 100 }),
    ];
    const top = topFoodsByMacro(es, m, '2026-06-01', '2026-06-30', 'protein');
    expect(top.map((f) => f.key)).toEqual(['rec-1', 'Snack libre']);
    expect(top[0].value).toBeCloseTo(20); // 2 × (10 × 100/100), agrégé par recipeUUID
    expect(top[1].value).toBeCloseTo(5);
  });
});

describe('Nutrition V6 — stats (nutrition-stats-utils)', () => {
  it('activeGoalAt : cible active = plus grand effectiveFrom ≤ date', () => {
    const goals = [
      goal({ uuid: 'g1', effectiveFrom: '2026-01-01', kcal: 2000 }),
      goal({ uuid: 'g2', effectiveFrom: '2026-06-01', kcal: 2200 }),
    ];
    expect(activeGoalAt(goals, '2026-05-31')?.kcal).toBe(2000);
    expect(activeGoalAt(goals, '2026-06-01')?.kcal).toBe(2200);
    expect(activeGoalAt(goals, '2025-12-31')).toBeNull();
  });

  it('aggregateNutrition DAILY : consommé par jour + cible×nb_jours via la cible active du jour', () => {
    const meals = [meal({ uuid: 'm1', date: '2026-06-12', name: 'Déj' })];
    const entries = [
      entry({ uuid: 'e1', mealUUID: 'm1', kcalPer100g: 200, proteinPer100g: 20, carbsPer100g: 10, fatPer100g: 5, fiberPer100g: 4, quantityG: 100 }),
    ];
    const goals = [goal({ effectiveFrom: '2026-01-01', kcal: 2000, proteinG: 150 })];
    const agg = aggregateNutrition(entries, meals, goals, '2026-06-11', '2026-06-12', 'DAILY');

    expect(agg.buckets).toEqual(['2026-06-11', '2026-06-12']);
    expect(agg.consumed.kcal).toEqual([0, 200]); // rien le 11, 200 le 12
    expect(agg.consumed.fiber).toEqual([0, 4]);
    expect(agg.target.kcal).toEqual([2000, 2000]); // cible active chaque jour
    expect(agg.target.protein).toEqual([150, 150]);
  });

  it('aggregateNutrition WEEKLY : un bucket cumule consommé et cible des jours de la semaine', () => {
    const meals = [
      meal({ uuid: 'm1', date: '2026-06-08', name: 'L' }), // lundi semaine W##
      meal({ uuid: 'm2', date: '2026-06-09', name: 'M' }),
    ];
    const entries = [
      entry({ uuid: 'e1', mealUUID: 'm1', kcalPer100g: 100, quantityG: 100 }),
      entry({ uuid: 'e2', mealUUID: 'm2', kcalPer100g: 100, quantityG: 100 }),
    ];
    const goals = [goal({ effectiveFrom: '2026-01-01', kcal: 2000 })];
    const agg = aggregateNutrition(entries, meals, goals, '2026-06-08', '2026-06-09', 'WEEKLY');

    expect(agg.buckets).toEqual([weekBucket('2026-06-08')]);
    expect(agg.consumed.kcal).toEqual([200]); // 100 + 100 sur la semaine
    expect(agg.target.kcal).toEqual([4000]); // 2000 × 2 jours
  });

  it('earliestMealDate : 1re date de repas (clamp de la période « Tout »)', () => {
    expect(earliestMealDate([meal({ date: '2026-06-12' }), meal({ date: '2026-03-01' })])).toBe('2026-03-01');
    expect(earliestMealDate([])).toBeNull();
  });

  it('computeBounds : granularité jour ≤ 14 j sinon semaine ; CUSTOM respecte les bornes', () => {
    expect(computeBounds('W1', '', '', null).gran).toBe('DAILY'); // 7 j
    expect(computeBounds('D30', '', '', null).gran).toBe('WEEKLY'); // 30 j
    const custom = computeBounds('CUSTOM', '2026-06-01', '2026-06-05', null);
    expect(custom.startIso).toBe('2026-06-01');
    expect(custom.endIso).toBe('2026-06-05');
    expect(custom.days).toBe(5);
    expect(custom.gran).toBe('DAILY');
  });
});
