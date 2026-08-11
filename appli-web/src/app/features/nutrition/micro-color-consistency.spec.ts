import { MICRO_COLOR, MICRO_FAMILY_COLOR, microLineItems } from './micro-colors';
import { microSummaryRows } from './nutrition-summary-panel';
import { MICRO_TARGETS, type MicroNutrients } from './micros';

/**
 * Garantie « source unique » de la tâche : un micro a la MÊME couleur partout. Les deux surfaces
 * d'affichage publiques — ligne compacte (rows du catalogue / picker / entries du journal via
 * `microLineItems`) et barres détail/résumé (via `microSummaryRows`) — doivent toujours s'accorder,
 * faute de quoi le « cohérentes partout » de la tâche est rompu. Les specs existantes vérifient
 * chaque surface CONTRE `MICRO_COLOR` séparément ; ici on verrouille l'accord croisé des 2 surfaces
 * sur les 10 micros, pour que les helpers ne puissent pas diverger sans casser un test.
 */

const ZERO_MICROS: MicroNutrients = {
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
};

describe('Micros — cohérence cross-surface (source unique)', () => {
  it('chaque micro présent a une couleur identique sur la ligne compacte et sur les barres résumé/détail', () => {
    for (const t of MICRO_TARGETS) {
      // Valeur sous le plafond Sodium (1000 < 2000) pour rester sur la teinte famille (pas l'alerte).
      const input: MicroNutrients = { ...ZERO_MICROS, [t.key]: 1000 };

      const lineColor = microLineItems(input).find((i) => i.short)!.color;
      const summaryColor = microSummaryRows(input).find((r) => r.key === t.key)!.color;

      expect(lineColor).toBe(MICRO_COLOR[t.key]); // ligne compacte = token famille
      expect(summaryColor).toBe(MICRO_COLOR[t.key]); // barres résumé = même token
      expect(lineColor).toBe(summaryColor); // → les 2 surfaces ne peuvent pas diverger
    }
  });

  it('minéraux rouge / vitamines doré : les 2 surfaces respectent les 2 teintes famille (jamais une 3e)', () => {
    for (const t of MICRO_TARGETS) {
      const input: MicroNutrients = { ...ZERO_MICROS, [t.key]: 50 };
      const expected = MICRO_FAMILY_COLOR[t.family]; // mineral (rouge) ou vitamin (doré)

      expect(microLineItems(input)[0].color).toBe(expected);
      expect(microSummaryRows(input).find((r) => r.key === t.key)!.color).toBe(expected);
    }
  });
});
