import { fiberDensity, macroKcalBreakdown, macroPerKg } from './goal-analysis';
import { deriveGoalFromMacros } from './goal-macros';

describe('goal-analysis — dérivés de la cible (purs)', () => {
  // Cible de référence P180 / G250 / L80 → kcal dérivée (macro-first, D12).
  const kcal = deriveGoalFromMacros({ proteinG: 180, carbsG: 250, fatG: 80 }).kcal;
  const goal = { kcal, proteinG: 180, carbsG: 250, fatG: 80 };

  it('macroKcalBreakdown : 4 parts, somme des % = 100', () => {
    const b = macroKcalBreakdown(goal);
    expect(b.map((p) => p.key)).toEqual(['carbs', 'fat', 'protein', 'fiber']);
    expect(b.reduce((s, p) => s + p.percent, 0)).toBeCloseTo(100, 5);
  });

  it('macroKcalBreakdown : la somme des kcal des parts = kcal de la cible', () => {
    const b = macroKcalBreakdown(goal);
    expect(b.reduce((s, p) => s + p.kcal, 0)).toBeCloseTo(kcal, 5);
  });

  it('macroKcalBreakdown : les fibres pèsent ≈ 3 % du total (poste dérivé 15 g/1000 kcal)', () => {
    const fiber = macroKcalBreakdown(goal).find((p) => p.key === 'fiber')!;
    expect(fiber.percent).toBeCloseTo(3, 1);
  });

  it('macroKcalBreakdown : cible vide → parts à 0 (pas de division par zéro)', () => {
    const b = macroKcalBreakdown({ kcal: 0, proteinG: 0, carbsG: 0, fatG: 0 });
    expect(b.every((p) => p.percent === 0 && p.kcal === 0)).toBe(true);
  });

  it('macroPerKg : g/kg si poids fourni, null sinon — générique P/G/L', () => {
    expect(macroPerKg(180, 80)).toBeCloseTo(2.25, 5); // protéines
    expect(macroPerKg(250, 80)).toBeCloseTo(3.125, 5); // glucides
    expect(macroPerKg(80, 80)).toBeCloseTo(1, 5); // lipides
    expect(macroPerKg(180, null)).toBeNull();
    expect(macroPerKg(180, 0)).toBeNull();
    expect(macroPerKg(180, undefined)).toBeNull();
  });

  it('fiberDensity : ≈ 15 g/1000 kcal par construction, 0 si pas de kcal', () => {
    expect(fiberDensity(kcal)).toBeCloseTo(15, 5);
    expect(fiberDensity(0)).toBe(0);
  });
});
