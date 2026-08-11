import {
  MACRO_KEYS,
  MACRO_TARGET_KEYS,
  MACRO_COLOR,
  MACRO_SHORT,
  MACRO_LABEL,
  MACRO_UNIT,
  MacroKey,
} from './macro-colors';

describe('Nutrition V6 — code couleur des macros (macro-colors)', () => {
  it('les 5 macros (kcal/P/G/L/fibres) ont chacune une couleur, un label, une abrév et une unité', () => {
    expect(MACRO_KEYS).toEqual(['kcal', 'carbs', 'fat', 'protein', 'fiber']);
    for (const k of MACRO_KEYS) {
      // chaque macro a un token CSS dédié (jamais de M3 brut), distinct par macro
      expect(MACRO_COLOR[k]).toBe(`var(--macro-${k})`);
      expect(MACRO_LABEL[k]).toBeTruthy();
      expect(MACRO_SHORT[k]).toBeTruthy();
      expect(MACRO_UNIT[k]).toBeTruthy();
    }
  });

  it('les couleurs des 5 macros sont toutes distinctes', () => {
    const colors = MACRO_KEYS.map((k) => MACRO_COLOR[k]);
    expect(new Set(colors).size).toBe(MACRO_KEYS.length);
  });

  it('abréviations compactes : protéines=P, glucides=G, lipides=L, fibres=F', () => {
    expect(MACRO_SHORT.protein).toBe('P');
    expect(MACRO_SHORT.carbs).toBe('G');
    expect(MACRO_SHORT.fat).toBe('L');
    expect(MACRO_SHORT.fiber).toBe('F');
  });

  it('les fibres ont une couleur mais PAS de cible (D11 : MACRO_TARGET_KEYS exclut fiber)', () => {
    expect(MACRO_COLOR.fiber).toBeTruthy();
    expect([...MACRO_TARGET_KEYS]).toEqual(['kcal', 'carbs', 'fat', 'protein']);
    expect((MACRO_TARGET_KEYS as readonly string[]).includes('fiber')).toBe(false);
  });

  it('toute clé de cible est aussi une clé macro valide', () => {
    for (const t of MACRO_TARGET_KEYS) {
      expect(MACRO_KEYS).toContain(t as MacroKey);
    }
  });
});
