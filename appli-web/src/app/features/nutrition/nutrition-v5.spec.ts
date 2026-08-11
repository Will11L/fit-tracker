import { LocalFood } from '@core/models/food.model';
import { recipeMacros } from './recipe-utils';

function food(over: Partial<LocalFood>): LocalFood {
  return {
    uuid: 'f1',
    userId: 1,
    name: 'Avoine',
    brand: null,
    source: 'CUSTOM',
    sourceRef: null,
    foodGroup: null,
    kcalPer100g: 380,
    proteinPer100g: 13,
    carbsPer100g: 60,
    fatPer100g: 7,
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
    isFavorite: false,
    archived: false,
    isWater: false,
    updatedAt: null,
    synced: true,
    pendingDeletion: false,
    ...over,
  };
}

describe('Nutrition V5 — recipe utils', () => {
  const foods = new Map([
    ['f1', food({})],
    [
      'f2',
      food({
        uuid: 'f2',
        name: 'Lait',
        kcalPer100g: 50,
        proteinPer100g: 3.3,
        carbsPer100g: 5,
        fatPer100g: 1.5,
      }),
    ],
  ]);
  const ingredients = [
    { foodUUID: 'f1', quantityG: 100 },
    { foodUUID: 'f2', quantityG: 300 },
  ];

  it('totaux = somme des per-100g × quantité des ingrédients', () => {
    const m = recipeMacros({ kind: 'SAVED_MEAL', totalWeightG: null }, ingredients, foods);
    expect(m.totals.kcal).toBeCloseTo(380 + 150);
    expect(m.totals.protein).toBeCloseTo(13 + 9.9);
    expect(m.ingredientsWeightG).toBe(400);
  });

  it('kind=RECIPE avec totalWeightG : per-100g sur le poids cuit (ratio cru/cuit)', () => {
    const m = recipeMacros({ kind: 'RECIPE', totalWeightG: 265 }, ingredients, foods);
    expect(m.weightBaseG).toBe(265);
    expect(m.per100g.kcal).toBeCloseTo((530 / 265) * 100); // 200 kcal / 100 g cuit
  });

  it('sans totalWeightG : per-100g sur la somme des quantités', () => {
    const m = recipeMacros({ kind: 'RECIPE', totalWeightG: null }, ingredients, foods);
    expect(m.weightBaseG).toBe(400);
    expect(m.per100g.kcal).toBeCloseTo(132.5);
  });

  it('ingrédient dont le Food a disparu : ignoré (référence vivante, pas de snapshot)', () => {
    const m = recipeMacros(
      { kind: 'SAVED_MEAL', totalWeightG: null },
      [...ingredients, { foodUUID: 'gone', quantityG: 50 }],
      foods,
    );
    expect(m.totals.kcal).toBeCloseTo(530);
    expect(m.ingredientsWeightG).toBe(400);
  });

  it('recette vide : per-100g à zéro (pas de division par zéro)', () => {
    const m = recipeMacros({ kind: 'RECIPE', totalWeightG: null }, [], foods);
    expect(m.per100g.kcal).toBe(0);
    expect(m.weightBaseG).toBe(0);
  });

  it('tous les ingrédients référencent des Foods supprimés → ingredientsWeightG 0 (garde anti-row fantôme T7)', () => {
    // Déclencheur exact de la garde `insertRecipeEntry` (ingredientsWeightG <= 0 → pas d'entry).
    // Même avec un totalWeightG renseigné (weightBaseG > 0), si AUCUN ingrédient ne se résout, le
    // poids d'ingrédients reste 0 : aucune entry 0 kcal / Meal fantôme ne doit être créée. per-100g
    // et micros restent nuls malgré weightBaseG = totalWeightG (totaux à 0 / poids).
    const m = recipeMacros(
      { kind: 'RECIPE', totalWeightG: 200 },
      [
        { foodUUID: 'ghost-1', quantityG: 100 },
        { foodUUID: 'ghost-2', quantityG: 50 },
      ],
      foods,
    );
    expect(m.ingredientsWeightG).toBe(0); // la garde se déclenche → aucune insertion
    expect(m.totals.kcal).toBe(0);
    expect(m.per100g.kcal).toBe(0);
    expect(m.microPer100g.ironPer100g).toBe(0);
  });

  describe('micros agrégés (T7)', () => {
    const microFoods = new Map([
      ['fe', food({ uuid: 'fe', name: 'Fer-riche', ironPer100g: 10, calciumPer100g: 100 })],
      ['vc', food({ uuid: 'vc', name: 'Vit C', vitaminCPer100g: 50, calciumPer100g: 20 })],
    ]);

    it('somme au prorata des ingrédients (per-100g × quantité), null traité 0', () => {
      const m = recipeMacros(
        { kind: 'SAVED_MEAL', totalWeightG: null },
        [
          { foodUUID: 'fe', quantityG: 200 }, // ×2 : Fe 20, Ca 200
          { foodUUID: 'vc', quantityG: 100 }, // ×1 : Vit C 50, Ca 20
        ],
        microFoods,
      );
      expect(m.microTotals.ironPer100g).toBeCloseTo(20);
      expect(m.microTotals.calciumPer100g).toBeCloseTo(220);
      expect(m.microTotals.vitaminCPer100g).toBeCloseTo(50);
      // Micro non renseigné sur aucun ingrédient : 0 (jamais null).
      expect(m.microTotals.magnesiumPer100g).toBe(0);
    });

    it('aucun micro renseigné : tous les totaux micros à 0', () => {
      const m = recipeMacros({ kind: 'SAVED_MEAL', totalWeightG: null }, ingredients, foods);
      expect(m.microTotals.ironPer100g).toBe(0);
      expect(m.microTotals.vitaminCPer100g).toBe(0);
    });

    it('per-100g des micros = microTotals rapportés au poids de base (snapshot entry RECIPE, T7)', () => {
      const m = recipeMacros(
        { kind: 'RECIPE', totalWeightG: 200 },
        [
          { foodUUID: 'fe', quantityG: 200 }, // Fe 20, Ca 200
          { foodUUID: 'vc', quantityG: 100 }, // Vit C 50, Ca 20
        ],
        microFoods,
      );
      // weightBaseG = 200 g cuit ; per-100g = total / 200 × 100 (même règle que les macros).
      expect(m.weightBaseG).toBe(200);
      expect(m.microPer100g.ironPer100g).toBeCloseTo(10); // 20 / 200 × 100
      expect(m.microPer100g.calciumPer100g).toBeCloseTo(110); // 220 / 200 × 100
      expect(m.microPer100g.vitaminCPer100g).toBeCloseTo(25); // 50 / 200 × 100
      expect(m.microPer100g.magnesiumPer100g).toBe(0);
    });

    it('recette vide : micros per-100g à 0 (pas de division par zéro)', () => {
      const m = recipeMacros({ kind: 'RECIPE', totalWeightG: null }, [], microFoods);
      expect(m.microPer100g.ironPer100g).toBe(0);
      expect(m.microPer100g.calciumPer100g).toBe(0);
    });
  });
});
