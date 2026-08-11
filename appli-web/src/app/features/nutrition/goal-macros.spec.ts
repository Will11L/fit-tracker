import { deriveGoalFromMacros } from './goal-macros';

describe('Nutrition D12 — objectifs macro-first (kcal dérivée + fibres)', () => {
  it('exemple de référence P180/G250/L80 → base 2440 → 2515 kcal, 38 g fibres', () => {
    const d = deriveGoalFromMacros({ proteinG: 180, carbsG: 250, fatG: 80 });
    expect(d.kcal).toBeCloseTo(2515.46, 1); // 2440 / 0,97
    expect(Math.round(d.kcal)).toBe(2515);
    expect(Math.round(d.fiberG)).toBe(38); // 15 × 2515,46 / 1000 = 37,73
  });

  it('total = base / 0,97 (base = 4P + 4G + 9L)', () => {
    const d = deriveGoalFromMacros({ proteinG: 100, carbsG: 100, fatG: 50 });
    const base = 4 * 100 + 4 * 100 + 9 * 50; // 1250
    expect(d.kcal).toBeCloseTo(base / 0.97);
  });

  it('fibres = 15 g pour 1000 kcal du total dérivé', () => {
    const d = deriveGoalFromMacros({ proteinG: 150, carbsG: 200, fatG: 70 });
    expect(d.fiberG).toBeCloseTo((15 * d.kcal) / 1000);
  });

  it('macros à zéro → total et fibres à zéro', () => {
    const d = deriveGoalFromMacros({ proteinG: 0, carbsG: 0, fatG: 0 });
    expect(d.kcal).toBe(0);
    expect(d.fiberG).toBe(0);
  });

  it('cohérence avec la barre fibres du journal (15 g/1000 kcal)', () => {
    // Le total dérivé, repassé dans la cible fibres 15 g/1000 kcal, redonne fiberG.
    const d = deriveGoalFromMacros({ proteinG: 200, carbsG: 300, fatG: 90 });
    expect((d.kcal / 1000) * 15).toBeCloseTo(d.fiberG);
  });
});
