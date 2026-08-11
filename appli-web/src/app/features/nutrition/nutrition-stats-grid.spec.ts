import { LocalMeal } from '@core/models/meal.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import { LocalNutritionGoal } from '@core/models/nutrition-goal.model';
import { MACRO_KEYS, MACRO_TARGET_KEYS, MacroKey, MacroTargetKey } from './macro-colors';
import { aggregateNutrition, topFoodsByMacro, type TopFood } from './nutrition-stats-utils';

/**
 * Couverture de la GRILLE de cartes Stats Nutrition (V6, §5.6) — une carte par nutriment
 * (kcal / Glucides / Lipides / Protéines / Fibres), chacune = chart (consommé + cible) + top aliments.
 * On reproduit ici le `cards` computed de NutritionStatsPage à partir des fonctions pures, pour
 * verrouiller les comportements par carte sans monter un TestBed : top aliments de chaque macro
 * (dont les deux cas non couverts par v6 : kcal et fibres-null) + l'invariant « fibres sans cible ».
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

describe('Nutrition V6 — grille de cartes par nutriment', () => {
  const meals = [meal({ uuid: 'm1', date: '2026-06-12' })];
  // Avoine = riche en fibres ; Poulet = pas de fibres (fiberPer100g null) ; Huile = beaucoup de kcal/lipides.
  const entries = [
    entry({ uuid: 'e1', mealUUID: 'm1', foodUUID: 'f-avoine', displayName: 'Avoine', kcalPer100g: 380, carbsPer100g: 60, proteinPer100g: 13, fatPer100g: 7, fiberPer100g: 10, quantityG: 100 }),
    entry({ uuid: 'e2', mealUUID: 'm1', foodUUID: 'f-poulet', displayName: 'Poulet', kcalPer100g: 165, carbsPer100g: 0, proteinPer100g: 31, fatPer100g: 3.6, fiberPer100g: null, quantityG: 200 }),
    entry({ uuid: 'e3', mealUUID: 'm1', foodUUID: 'f-huile', displayName: 'Huile', kcalPer100g: 900, carbsPer100g: 0, proteinPer100g: 0, fatPer100g: 100, fiberPer100g: null, quantityG: 20 }),
  ];

  it('carte Fibres : seuls les aliments à fibres > 0 apparaissent (fiberPer100g null = 0, exclu)', () => {
    const top = topFoodsByMacro(entries, meals, '2026-06-01', '2026-06-30', 'fiber');
    // Poulet (null) et Huile (null) écartés ; reste l'Avoine seule.
    expect(top.map((f) => f.displayName)).toEqual(['Avoine']);
    expect(top[0].value).toBeCloseTo(10); // 10 g/100g × 100 g
    expect(top[0].share).toBeCloseTo(1);
  });

  it('carte Calories : top aliments triés par kcal décroissant', () => {
    const top = topFoodsByMacro(entries, meals, '2026-06-01', '2026-06-30', 'kcal');
    // Avoine 380, Poulet 330 (165×2), Huile 180 (900×0.2).
    expect(top.map((f) => f.displayName)).toEqual(['Avoine', 'Poulet', 'Huile']);
    expect(top[0].value).toBeCloseTo(380);
    expect(top[1].value).toBeCloseTo(330);
    expect(top[2].value).toBeCloseTo(180);
    const sumShare = top.reduce((s, f) => s + f.share, 0);
    expect(sumShare).toBeCloseTo(1);
  });

  it('mimique le `cards` computed : 5 cartes (1 par nutriment), Fibres sans cible, autres avec cible', () => {
    const start = '2026-06-12';
    const end = '2026-06-12';
    const goals = [goal({})];
    const a = aggregateNutrition(entries, meals, goals, start, end, 'DAILY');

    interface Card { key: MacroKey; consumed: number[]; target: number[]; topFoods: TopFood[] }
    const cards: Card[] = MACRO_KEYS.map((key) => {
      const hasTarget = (MACRO_TARGET_KEYS as readonly string[]).includes(key);
      return {
        key,
        consumed: a.consumed[key],
        target: hasTarget ? a.target[key as MacroTargetKey] : [],
        topFoods: topFoodsByMacro(entries, meals, start, end, key),
      };
    });

    // 5 cartes dans l'ordre canonique kcal / Glucides / Lipides / Protéines / Fibres.
    expect(cards.map((c) => c.key)).toEqual(['kcal', 'carbs', 'fat', 'protein', 'fiber']);

    const fiberCard = cards.find((c) => c.key === 'fiber')!;
    expect(fiberCard.target).toEqual([]); // D11 : pas de cible fibres
    expect(fiberCard.consumed).toEqual([10]); // Avoine seule porte des fibres

    // Toutes les autres cartes ont une cible non vide.
    for (const c of cards.filter((c) => c.key !== 'fiber')) {
      expect(c.target.length).toBeGreaterThan(0);
    }

    // Carte kcal : consommé = somme des 3 aliments = 380 + 330 + 180.
    const kcalCard = cards.find((c) => c.key === 'kcal')!;
    expect(kcalCard.consumed[0]).toBeCloseTo(890);
  });
});
