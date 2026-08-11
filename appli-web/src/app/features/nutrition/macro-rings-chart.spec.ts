import { macroRingViews } from './macro-rings-chart';
import { MACRO_COLOR } from './macro-colors';

describe('macroRingViews — anneaux concentriques macro (kcal + G/L/P/F)', () => {
  it('5 anneaux du plus extérieur (kcal) au plus intérieur (fibres), kcal plus épais', () => {
    const rings = macroRingViews(1800, { carbs: 200, fat: 60, protein: 120, fiber: 25 }, null);
    expect(rings.length).toBe(5);
    expect(rings[0].color).toBe(MACRO_COLOR.kcal);
    expect(rings[4].color).toBe(MACRO_COLOR.fiber);
    expect(rings[0].width).toBeGreaterThan(rings[1].width);
  });

  it('avec cible : avancement = valeur / cible, étiquette « Nom % »', () => {
    const rings = macroRingViews(
      1000,
      { carbs: 150, fat: 50, protein: 100, fiber: 20 },
      { kcal: 2000, carbs: 300, fat: 100, protein: 200, fiber: 40 },
    );
    expect(rings[0].progress).toBeCloseTo(0.5);
    expect(rings[0].label).toBe('Calories 50%');
    expect(rings[1].label).toBe('Glucides 50%');
  });

  it('dépassement : progress plafonné à 1 mais % NON plafonné dans l’étiquette', () => {
    const rings = macroRingViews(2400, { carbs: 0, fat: 0, protein: 0, fiber: 0 }, { kcal: 2000 });
    expect(rings[0].progress).toBe(1);
    expect(rings[0].label).toBe('Calories 120%');
  });

  it('sans cible : étiquette « Nom valeur unité » (repli sur la valeur)', () => {
    const rings = macroRingViews(1800, { carbs: 200, fat: 60, protein: 120, fiber: 25 }, null);
    expect(rings[0].label).toBe('Calories 1800 kcal');
    expect(rings[1].label).toBe('Glucides 200 g');
  });
});
