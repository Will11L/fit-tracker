import { FoodMacros, effectiveFoodKcal, kcalFromMacros } from './food-kcal';

function food(over: Partial<FoodMacros>): FoodMacros {
  return {
    source: 'CIQUAL',
    kcalPer100g: 999, // valeur source volontairement fausse pour distinguer dérivée vs source
    proteinPer100g: 13,
    carbsPer100g: 60,
    fatPer100g: 7,
    fiberPer100g: null,
    ...over,
  };
}

describe('Nutrition D12 — kcal dérivée des macros', () => {
  it('kcalFromMacros : 4·P + 4·G + 9·L + 2·fibres', () => {
    // 4·13 + 4·60 + 9·7 = 52 + 240 + 63 = 355
    expect(kcalFromMacros(13, 60, 7)).toBeCloseTo(355);
  });

  it('kcalFromMacros : terme fibres (2 kcal/g), null traité comme 0', () => {
    expect(kcalFromMacros(0, 0, 0, 10)).toBeCloseTo(20);
    expect(kcalFromMacros(13, 60, 7, null)).toBeCloseTo(355);
    expect(kcalFromMacros(13, 60, 7, 5)).toBeCloseTo(365);
  });

  it('CIQUAL (brut) : kcal dérivée des macros, ignore la valeur source', () => {
    expect(effectiveFoodKcal(food({ source: 'CIQUAL', kcalPer100g: 999 }))).toBeCloseTo(355);
  });

  it('CIQUAL : inclut les fibres quand présentes', () => {
    expect(
      effectiveFoodKcal(food({ source: 'CIQUAL', kcalPer100g: 999, fiberPer100g: 10 })),
    ).toBeCloseTo(375);
  });

  it('OFF (étiqueté) : kcal de la source, l’étiquette fait foi', () => {
    expect(effectiveFoodKcal(food({ source: 'OFF', kcalPer100g: 372 }))).toBe(372);
  });

  it('CUSTOM : kcal saisie si fournie (> 0)', () => {
    expect(effectiveFoodKcal(food({ source: 'CUSTOM', kcalPer100g: 380 }))).toBe(380);
  });

  it('CUSTOM : dérive des macros si aucune kcal saisie (0)', () => {
    expect(effectiveFoodKcal(food({ source: 'CUSTOM', kcalPer100g: 0 }))).toBeCloseTo(355);
  });

  it('source inconnue : repli sur la valeur stockée', () => {
    expect(effectiveFoodKcal(food({ source: 'WHATEVER', kcalPer100g: 123 }))).toBe(123);
  });

  it('reproductibilité D5 : pour un brut, kcal effective == kcalFromMacros(mêmes macros)', () => {
    const f = food({ source: 'CIQUAL', proteinPer100g: 20, carbsPer100g: 5, fatPer100g: 3, fiberPer100g: 2 });
    expect(effectiveFoodKcal(f)).toBeCloseTo(kcalFromMacros(20, 5, 3, 2));
  });
});
