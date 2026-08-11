import { MICRO_COLOR, MICRO_FAMILY_COLOR, microLineItems } from './micro-colors';
import { MICRO_KEYS, MICRO_TARGETS, type MicroNutrients } from './micros';
import { MACRO_COLOR } from './macro-colors';

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

describe('Nutrition — code couleur des micros (micro-colors)', () => {
  it('expose 2 teintes par famille (minéraux vs vitamines), distinctes', () => {
    expect(MICRO_FAMILY_COLOR.mineral).toBe('var(--micro-mineral)');
    expect(MICRO_FAMILY_COLOR.vitamin).toBe('var(--micro-vitamin)');
    expect(MICRO_FAMILY_COLOR.mineral).not.toBe(MICRO_FAMILY_COLOR.vitamin);
  });

  it('les 10 micros ont chacun une couleur = la teinte de leur famille', () => {
    expect(Object.keys(MICRO_COLOR).length).toBe(MICRO_KEYS.length);
    for (const t of MICRO_TARGETS) {
      expect(MICRO_COLOR[t.key]).toBe(MICRO_FAMILY_COLOR[t.family]);
    }
  });

  it('seulement 2 couleurs distinctes au total (pas une palette de 10)', () => {
    const colors = MICRO_KEYS.map((k) => MICRO_COLOR[k]);
    expect(new Set(colors).size).toBe(2);
  });

  it('minéraux (Fe/Ca/Mg/Zn/K/Na) et vitamines (Vit C/D/B12/A) sont bien sur 2 teintes opposées', () => {
    expect(MICRO_COLOR.ironPer100g).toBe(MICRO_FAMILY_COLOR.mineral);
    expect(MICRO_COLOR.sodiumPer100g).toBe(MICRO_FAMILY_COLOR.mineral);
    expect(MICRO_COLOR.vitaminCPer100g).toBe(MICRO_FAMILY_COLOR.vitamin);
    expect(MICRO_COLOR.vitaminAPer100g).toBe(MICRO_FAMILY_COLOR.vitamin);
    expect(MICRO_COLOR.ironPer100g).not.toBe(MICRO_COLOR.vitaminCPer100g);
  });

  it('utilise ses propres tokens --micro-* et n’emprunte aucune des 5 hues macros (--macro-*)', () => {
    const macroTokens = new Set(Object.values(MACRO_COLOR));
    const microColors = new Set([...MICRO_KEYS.map((k) => MICRO_COLOR[k]), ...Object.values(MICRO_FAMILY_COLOR)]);
    for (const c of microColors) {
      expect(c).toMatch(/^var\(--micro-/); // jamais de M3 brut ni d'alias macro
      expect(macroTokens.has(c)).toBe(false); // hue distincte des 5 macros (kcal/protein/carbs/fat/fiber)
    }
  });
});

describe('microLineItems — affichage compact coloré par famille (source unique)', () => {
  it('ne renvoie que les micros présents (valeur non nulle), colorés par famille', () => {
    const items = microLineItems({ ...ZERO_MICROS, calciumPer100g: 120, vitaminDPer100g: 2 });
    expect(items.length).toBe(2);
    expect(items.map((i) => i.short)).toEqual(['Ca', 'Vit D']);
    expect(items[0].color).toBe(MICRO_COLOR.calciumPer100g); // minéral
    expect(items[1].color).toBe(MICRO_COLOR.vitaminDPer100g); // vitamine
  });

  it('chaque couleur vient de MICRO_COLOR (→ tokens) : changer un token propage ici', () => {
    const items = microLineItems({ ...ZERO_MICROS, ironPer100g: 7, vitaminCPer100g: 40 });
    expect(items.find((i) => i.short === 'Fe')!.color).toBe(MICRO_FAMILY_COLOR.mineral);
    expect(items.find((i) => i.short === 'Vit C')!.color).toBe(MICRO_FAMILY_COLOR.vitamin);
  });

  it('met les valeurs à l’échelle de la quantité consommée via factor', () => {
    // 80 g d’un aliment à 10 mg Ca /100 g → 8 mg consommés.
    const items = microLineItems({ ...ZERO_MICROS, calciumPer100g: 10 }, 80 / 100);
    expect(items[0].value).toBe(8);
    expect(items[0].color).toBe(MICRO_COLOR.calciumPer100g);
  });

  it('aucun micro renseigné → liste vide', () => {
    expect(microLineItems(ZERO_MICROS).length).toBe(0);
  });
});
