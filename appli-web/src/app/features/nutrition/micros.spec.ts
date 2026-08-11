import {
  microRows,
  MICRO_TARGETS,
  presentMicros,
  MicroNutrients,
  MicroTotals,
  ZERO_MICRO_TOTALS,
} from './micros';

const NONE: MicroNutrients = {
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

describe('Nutrition micros — affichage info (D11 étendu)', () => {
  it('presentMicros ne garde que les micros non nuls, arrondis à 1 décimale', () => {
    const items = presentMicros({ ...NONE, ironPer100g: 1.94, vitaminDPer100g: 1.5 });
    expect(items).toEqual([
      { label: 'Fer', short: 'Fe', value: 1.9, unit: 'mg' },
      { label: 'Vit. D', short: 'Vit D', value: 1.5, unit: 'µg' },
    ]);
  });

  it('respecte l’ordre minéraux puis vitamines et les unités mg/µg', () => {
    const items = presentMicros({
      ...NONE,
      sodiumPer100g: 40,
      calciumPer100g: 120,
      vitaminCPer100g: 30,
      vitaminB12Per100g: 0.8,
    });
    expect(items.map((i) => i.label)).toEqual(['Calcium', 'Sodium', 'Vit. C', 'Vit. B12']);
    expect(items.find((i) => i.label === 'Vit. B12')!.unit).toBe('µg');
  });

  it('presentMicros(factor) met les micros à l’échelle de la quantité consommée', () => {
    // 80 g d’un aliment à 10 mg Ca /100 g → 8 mg consommés.
    expect(presentMicros({ ...NONE, calciumPer100g: 10, vitaminCPer100g: 5 }, 80 / 100)).toEqual([
      { label: 'Calcium', short: 'Ca', value: 8, unit: 'mg' },
      { label: 'Vit. C', short: 'Vit C', value: 4, unit: 'mg' },
    ]);
    expect(presentMicros(NONE, 2)).toEqual([]);
  });

  it('traite 0 comme une valeur présente (≠ null)', () => {
    expect(presentMicros({ ...NONE, ironPer100g: 0 })).toEqual([
      { label: 'Fer', short: 'Fe', value: 0, unit: 'mg' },
    ]);
  });
});

describe('Nutrition micros — cibles VNR UE + lignes de progression', () => {
  it('expose les 9 VNR officielles + le plafond Sodium dans l’ordre minéraux puis vitamines', () => {
    expect(MICRO_TARGETS.map((t) => [t.label, t.target, t.unit, t.isLimit])).toEqual([
      ['Fer', 14, 'mg', false],
      ['Calcium', 800, 'mg', false],
      ['Magnésium', 375, 'mg', false],
      ['Zinc', 10, 'mg', false],
      ['Potassium', 2000, 'mg', false],
      ['Sodium', 2000, 'mg', true], // plafond, pas un objectif
      ['Vit. C', 80, 'mg', false],
      ['Vit. D', 5, 'µg', false],
      ['Vit. B12', 2.5, 'µg', false],
      ['Vit. A', 800, 'µg', false],
    ]);
  });

  it('microRows : progression bornée 0..1 vs la VNR, jamais en dépassement pour un objectif', () => {
    const totals: MicroTotals = { ...ZERO_MICRO_TOTALS, ironPer100g: 7, calciumPer100g: 1600 };
    const rows = microRows(totals);
    const iron = rows.find((r) => r.label === 'Fer')!;
    const calcium = rows.find((r) => r.label === 'Calcium')!;
    expect(iron.progress).toBeCloseTo(0.5); // 7 / 14
    expect(calcium.progress).toBe(1); // 1600 / 800 borné à 1
    expect(calcium.exceeded).toBe(false); // pas un plafond
  });

  it('microRows : Sodium est un plafond — exceeded seulement au-dessus de 2000 mg', () => {
    const under = microRows({ ...ZERO_MICRO_TOTALS, sodiumPer100g: 1500 }).find(
      (r) => r.label === 'Sodium',
    )!;
    const over = microRows({ ...ZERO_MICRO_TOTALS, sodiumPer100g: 2500 }).find(
      (r) => r.label === 'Sodium',
    )!;
    expect(under.isLimit).toBe(true);
    expect(under.exceeded).toBe(false);
    expect(over.exceeded).toBe(true);
    expect(over.progress).toBe(1);
  });
});
