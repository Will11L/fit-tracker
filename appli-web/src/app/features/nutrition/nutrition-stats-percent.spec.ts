import { LocalMeal } from '@core/models/meal.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import { LocalNutritionGoal } from '@core/models/nutrition-goal.model';
import { MACRO_COLOR, MACRO_LABEL } from './macro-colors';
import {
  aggregateNutrition,
  formatBucketLabel,
  macroPercentSeries,
} from './nutrition-stats-utils';
import { FIBER_G_PER_1000_KCAL } from './goal-macros';

/**
 * 6e graphe Stats Nutrition (synthèse multi-lignes « % de l'objectif ») — builder pur
 * `macroPercentSeries`. On verrouille : les 5 séries (ordre / libellé / couleur macro), le calcul
 * `consommé / cible × 100` (fibres = cible dérivée du kcal), les buckets sans objectif → `null`
 * (pas de division par zéro ni de 100 % fantôme), et l'état vide quand aucun objectif n'est actif.
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

describe('macroPercentSeries — 5 macros en % de l’objectif (6e graphe multi-lignes)', () => {
  it('5 séries dans l’ordre canonique, libellés + couleurs macro (source MACRO_*)', () => {
    // 1 jour, consommation = cible exacte (fibres incluses : 30 g = 2000/1000 × 15) → 100 % partout.
    const meals = [meal({ uuid: 'm1', date: '2026-06-12' })];
    const entries = [
      entry({
        mealUUID: 'm1',
        kcalPer100g: 2000,
        carbsPer100g: 200,
        fatPer100g: 70,
        proteinPer100g: 150,
        fiberPer100g: 30,
        quantityG: 100,
      }),
    ];
    const agg = aggregateNutrition(entries, meals, [goal({})], '2026-06-12', '2026-06-12', 'DAILY');
    const series = macroPercentSeries(agg);

    expect(series.map((s) => s.key)).toEqual(['kcal', 'carbs', 'fat', 'protein', 'fiber']);
    expect(series.map((s) => s.name)).toEqual([
      MACRO_LABEL.kcal,
      MACRO_LABEL.carbs,
      MACRO_LABEL.fat,
      MACRO_LABEL.protein,
      MACRO_LABEL.fiber,
    ]);
    expect(series.map((s) => s.color)).toEqual([
      MACRO_COLOR.kcal,
      MACRO_COLOR.carbs,
      MACRO_COLOR.fat,
      MACRO_COLOR.protein,
      MACRO_COLOR.fiber,
    ]);
    // 1 bucket, tout à 100 % (y compris la cible fibres dérivée du kcal cible).
    for (const s of series) expect(s.data).toEqual([100]);
  });

  it('calcule consommé/cible × 100 (1 décimale), 0 % si consommé nul mais cible active', () => {
    // Glucides à moitié de la cible (100 g vs 200), protéines pile, lipides à zéro (cible active).
    const meals = [meal({ uuid: 'm1', date: '2026-06-12' })];
    const entries = [
      entry({ mealUUID: 'm1', carbsPer100g: 100, proteinPer100g: 150, fatPer100g: 0, quantityG: 100 }),
    ];
    const agg = aggregateNutrition(entries, meals, [goal({})], '2026-06-12', '2026-06-12', 'DAILY');
    const byKey = Object.fromEntries(macroPercentSeries(agg).map((s) => [s.key, s.data]));

    expect(byKey['carbs']).toEqual([50]); // 100 / 200
    expect(byKey['protein']).toEqual([100]); // 150 / 150
    expect(byKey['fat']).toEqual([0]); // 0 / 70 → 0 %, PAS null (la cible est active)
  });

  it('fibres : cible dérivée du kcal cible (FIBER_G_PER_1000_KCAL), pas du champ goal', () => {
    // Cible kcal 2000 → cible fibres 30 g ; consommé 15 g → 50 %.
    const meals = [meal({ uuid: 'm1', date: '2026-06-12' })];
    const entries = [entry({ mealUUID: 'm1', fiberPer100g: 15, quantityG: 100 })];
    const agg = aggregateNutrition(entries, meals, [goal({ kcal: 2000 })], '2026-06-12', '2026-06-12', 'DAILY');
    const fiber = macroPercentSeries(agg).find((s) => s.key === 'fiber')!;
    const fiberTarget = (2000 / 1000) * FIBER_G_PER_1000_KCAL; // 30
    expect(fiber.data).toEqual([Math.round((15 / fiberTarget) * 1000) / 10]); // 50
  });

  it('bucket sans objectif actif → null (point sauté, pas de division par zéro ni de 100 % fantôme)', () => {
    // Période 11 → 12 juin ; objectif actif seulement à partir du 12 (effectiveFrom).
    // Le 11 porte de la consommation MAIS aucune cible → doit rester null (pas 100 %).
    const meals = [
      meal({ uuid: 'm0', date: '2026-06-11' }),
      meal({ uuid: 'm1', date: '2026-06-12' }),
    ];
    const entries = [
      entry({ uuid: 'e0', mealUUID: 'm0', carbsPer100g: 100, quantityG: 100 }),
      entry({ uuid: 'e1', mealUUID: 'm1', carbsPer100g: 200, quantityG: 100 }),
    ];
    const agg = aggregateNutrition(
      entries,
      meals,
      [goal({ effectiveFrom: '2026-06-12' })],
      '2026-06-11',
      '2026-06-12',
      'DAILY',
    );
    const carbs = macroPercentSeries(agg).find((s) => s.key === 'carbs')!;
    expect(agg.buckets).toEqual(['2026-06-11', '2026-06-12']);
    expect(carbs.data).toEqual([null, 100]); // 11 juin : pas de cible → null ; 12 juin : 200/200
  });

  it('WEEKLY : bucket multi-jours → consommé Σ / (cible × nb_jours) × 100 (D8)', () => {
    // 10 → 12 juin 2026 = même semaine SQLite (%Y-%W) → 1 seul bucket de 3 jours.
    // Cible carbs 200 g/jour → cible bucket = 600 g ; consommé 100 g × 3 jours = 300 g → 50 %.
    const meals = [
      meal({ uuid: 'm0', date: '2026-06-10' }),
      meal({ uuid: 'm1', date: '2026-06-11' }),
      meal({ uuid: 'm2', date: '2026-06-12' }),
    ];
    const entries = [
      entry({ uuid: 'e0', mealUUID: 'm0', carbsPer100g: 100, quantityG: 100 }),
      entry({ uuid: 'e1', mealUUID: 'm1', carbsPer100g: 100, quantityG: 100 }),
      entry({ uuid: 'e2', mealUUID: 'm2', carbsPer100g: 100, quantityG: 100 }),
    ];
    const agg = aggregateNutrition(entries, meals, [goal({})], '2026-06-10', '2026-06-12', 'WEEKLY');
    expect(agg.buckets.length).toBe(1); // les 3 jours tombent dans la même semaine
    const carbs = macroPercentSeries(agg).find((s) => s.key === 'carbs')!;
    expect(carbs.data).toEqual([50]); // 300 / 600 — la division survit à l'agrégation multi-jours
  });

  it('aucun objectif actif sur toute la période → [] (état vide du composant, pas de courbe fantôme)', () => {
    const meals = [meal({ uuid: 'm1', date: '2026-06-12' })];
    const entries = [entry({ mealUUID: 'm1', carbsPer100g: 100, quantityG: 100 })];
    const agg = aggregateNutrition(entries, meals, [], '2026-06-12', '2026-06-12', 'DAILY');
    expect(macroPercentSeries(agg)).toEqual([]);
  });

  it('période vide (aucun bucket) → []', () => {
    const agg = aggregateNutrition([], [], [], '2026-06-13', '2026-06-12', 'DAILY'); // start > end
    expect(agg.buckets).toEqual([]);
    expect(macroPercentSeries(agg)).toEqual([]);
  });
});

describe('formatBucketLabel — étiquette X des buckets', () => {
  it('DAILY : YYYY-MM-DD → J/M (sans zéro de tête)', () => {
    expect(formatBucketLabel('2026-06-09', 'DAILY')).toBe('9/6');
    expect(formatBucketLabel('2026-12-25', 'DAILY')).toBe('25/12');
  });

  it('WEEKLY : YYYY-WW → W##', () => {
    expect(formatBucketLabel('2026-07', 'WEEKLY')).toBe('W7');
    expect(formatBucketLabel('2026-23', 'WEEKLY')).toBe('W23');
  });
});
