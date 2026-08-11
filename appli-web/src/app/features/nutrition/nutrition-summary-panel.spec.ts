import {
  macroBarRows,
  macroEnergyShares,
  macroRadarData,
  microRadarData,
  microSummaryRows,
  sugarBarRow,
  type MacroAmounts,
} from './nutrition-summary-panel';
import { MACRO_COLOR, SUGAR_COLOR } from './macro-colors';
import { MICRO_COLOR, MICRO_FAMILY_COLOR } from './micro-colors';
import { MICRO_TARGETS, type MicroNutrients } from './micros';

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

describe('NutritionSummaryPanel — helpers purs (résumé macros + micros)', () => {
  describe('macroBarRows', () => {
    const macros: MacroAmounts = { protein: 30, carbs: 60, fat: 20, fiber: 10 };

    it('produit les 4 macros grammes (carbs/fat/protein/fiber), pas kcal, dans l’ordre canonique', () => {
      const rows = macroBarRows(macros, null);
      expect(rows.map((r) => r.key)).toEqual(['carbs', 'fat', 'protein', 'fiber']);
    });

    it('chaque macro porte sa couleur dédiée (macro-colors), jamais fonction de l’avancement', () => {
      const rows = macroBarRows(macros, null);
      for (const r of rows) {
        expect(r.color).toBe(MACRO_COLOR[r.key]);
      }
    });

    it('sans cible → barre relative au plus grand macro (le max = barre pleine)', () => {
      const rows = macroBarRows(macros, null);
      // carbs (60) est le plus grand → progress 1 ; fat (20) → 20/60 = 0.333…
      const carbs = rows.find((r) => r.key === 'carbs')!;
      const fat = rows.find((r) => r.key === 'fat')!;
      expect(carbs.progress).toBe(1);
      expect(fat.progress).toBeCloseTo(20 / 60, 5);
      expect(carbs.target).toBeNull();
      expect(carbs.targetText).toBe('g'); // pas de « / cible » sans cible
    });

    it('avec cibles → avancement vs cible, borné à 1, avec texte « / cible unité »', () => {
      const rows = macroBarRows(macros, { carbs: 120, fat: 10, protein: 60, fiber: 40 });
      const carbs = rows.find((r) => r.key === 'carbs')!;
      const fat = rows.find((r) => r.key === 'fat')!;
      expect(carbs.progress).toBeCloseTo(60 / 120, 5); // 0.5
      expect(fat.progress).toBe(1); // 20/10 = 2 → borné à 1
      expect(carbs.target).toBe(120);
      expect(carbs.targetText).toBe('/ 120 g');
    });

    it('fibres absentes → valeur 0 (pas de crash)', () => {
      const rows = macroBarRows({ protein: 30, carbs: 60, fat: 20 }, null);
      const fiber = rows.find((r) => r.key === 'fiber')!;
      expect(fiber.value).toBe(0);
    });

    it('cibles fournies mais une macro sans cible (fibres, usage réel) → barre pleine (fallback = sa valeur)', () => {
      // Usage catalogue/recette : on passe des cibles macros mais pas de cible fibres (v1, D11).
      const rows = macroBarRows(macros, { carbs: 120, fat: 40, protein: 60 });
      const fiber = rows.find((r) => r.key === 'fiber')!;
      expect(fiber.target).toBeNull();
      expect(fiber.progress).toBe(1); // denom = value (10) → 10/10
      expect(fiber.targetText).toBe('g'); // pas de « / cible » faute de cible
    });
  });

  describe('sugarBarRow (ligne « Sucres » du détail aliment, mode barres seulement)', () => {
    const macros: MacroAmounts = { protein: 30, carbs: 60, fat: 20, fiber: 10 };

    it('teinte --macro-sugar + échelle relative au plus grand macro (comme les barres sans cible)', () => {
      const row = sugarBarRow(15, macros)!;
      expect(row.color).toBe(SUGAR_COLOR);
      expect(row.value).toBe(15);
      expect(row.progress).toBeCloseTo(15 / 60, 5); // max macro = carbs 60
      expect(row.valueText).toBe('15');
    });

    it('sucres non renseignés → null (pas de ligne)', () => {
      expect(sugarBarRow(null, macros)).toBeNull();
      expect(sugarBarRow(undefined, macros)).toBeNull();
    });

    it('borné 0..1 même si les sucres dépassent le plus grand macro (aliment quasi 100 % sucre)', () => {
      const row = sugarBarRow(99, { protein: 0, carbs: 50, fat: 0, fiber: 0 })!;
      expect(row.progress).toBe(1);
    });

    it('les sucres restent ABSENTS du radar macros (plafond ≠ cible, cohérent bandeau Journal)', () => {
      // macroBarRows / macroRadarData ne connaissent que les 4 macros grammes — aucune fuite « Sucres ».
      expect(macroBarRows(macros, null).map((r) => r.key)).toEqual(['carbs', 'fat', 'protein', 'fiber']);
      const { axes } = macroRadarData(macros, null);
      expect(axes.map((a) => a.label)).toEqual(['Glucides', 'Lipides', 'Protéines', 'Fibres']);
    });
  });

  describe('microSummaryRows', () => {
    it('produit 10 lignes vs VNR / plafond, colorées par famille (T3)', () => {
      const rows = microSummaryRows({ ...ZERO_MICROS, ironPer100g: 7, vitaminCPer100g: 40 });
      expect(rows.length).toBe(MICRO_TARGETS.length);
      const iron = rows.find((r) => r.key === 'ironPer100g')!;
      const vitC = rows.find((r) => r.key === 'vitaminCPer100g')!;
      expect(iron.color).toBe(MICRO_COLOR.ironPer100g); // teinte minéral
      expect(vitC.color).toBe(MICRO_COLOR.vitaminCPer100g); // teinte vitamine
      expect(iron.progress).toBeCloseTo(7 / 14, 5); // VNR fer = 14
    });

    it('Sodium en dessous du plafond → teinte minéral + texte « ≤ plafond »', () => {
      const rows = microSummaryRows({ ...ZERO_MICROS, sodiumPer100g: 1000 });
      const na = rows.find((r) => r.key === 'sodiumPer100g')!;
      expect(na.isLimit).toBe(true);
      expect(na.exceeded).toBe(false);
      expect(na.color).toBe(MICRO_FAMILY_COLOR.mineral);
      expect(na.targetText).toBe('≤ 2000 mg');
    });

    it('Sodium au-dessus du plafond → orange d’avertissement, distinct du rouge minéral', () => {
      const rows = microSummaryRows({ ...ZERO_MICROS, sodiumPer100g: 2500 });
      const na = rows.find((r) => r.key === 'sodiumPer100g')!;
      expect(na.exceeded).toBe(true);
      // Orange warning ≠ rouge minéral (--micro-mineral == --app-snackbar-error) qui rendait l'alerte
      // invisible : le dépassement du plafond doit être perceptible.
      expect(na.color).toBe('var(--app-snackbar-warning)');
      expect(na.color).not.toBe(MICRO_COLOR.sodiumPer100g);
    });
  });

  describe('macroEnergyShares (donut — répartition macro en %)', () => {
    const macros: MacroAmounts = { protein: 30, carbs: 60, fat: 20, fiber: 10 };

    it('kcal par macro via Atwater (4·G, 9·L, 4·P, 2·F), ordre canonique G/L/P/F', () => {
      const rows = macroEnergyShares(macros);
      expect(rows.map((r) => r.key)).toEqual(['carbs', 'fat', 'protein', 'fiber']);
      expect(rows.map((r) => r.kcal)).toEqual([240, 180, 120, 20]); // 4·60, 9·20, 4·30, 2·10
    });

    it('parts (%) = kcal de la macro / énergie macro totale, somme = 1', () => {
      const rows = macroEnergyShares(macros);
      const total = 240 + 180 + 120 + 20; // 560
      expect(rows.find((r) => r.key === 'carbs')!.share).toBeCloseTo(240 / total, 5);
      expect(rows.find((r) => r.key === 'fat')!.share).toBeCloseTo(180 / total, 5);
      expect(rows.reduce((s, r) => s + r.share, 0)).toBeCloseTo(1, 5);
    });

    it('chaque part porte la couleur dédiée de sa macro (macro-colors)', () => {
      for (const r of macroEnergyShares(macros)) {
        expect(r.color).toBe(MACRO_COLOR[r.key]);
      }
    });

    it('fibres absentes → part fibres à 0 (pas de crash)', () => {
      const fiber = macroEnergyShares({ protein: 30, carbs: 60, fat: 20 }).find((r) => r.key === 'fiber')!;
      expect(fiber.kcal).toBe(0);
      expect(fiber.share).toBe(0);
    });

    it('profil sans kcal → toutes les parts à 0 (pas de division par zéro / NaN)', () => {
      const rows = macroEnergyShares({ protein: 0, carbs: 0, fat: 0, fiber: 0 });
      expect(rows.every((r) => r.kcal === 0 && r.share === 0)).toBe(true);
    });
  });

  describe('macroRadarData', () => {
    const macros: MacroAmounts = { protein: 30, carbs: 60, fat: 20, fiber: 10 };

    it('sans cible → 1 série « Profil » de valeurs brutes (g), 4 axes sans max imposé', () => {
      const { axes, series } = macroRadarData(macros, null);
      expect(axes.map((a) => a.label)).toEqual(['Glucides', 'Lipides', 'Protéines', 'Fibres']);
      expect(axes.every((a) => a.max === undefined)).toBe(true);
      expect(series.length).toBe(1);
      expect(series[0].name).toBe('Profil');
      expect(series[0].values).toEqual([60, 20, 30, 10]);
      expect(series[0].color).toBe('var(--macro-kcal)');
    });

    it('chaque axe porte la couleur de sa macro (cohérent avec les barres), sans cible', () => {
      const { axes } = macroRadarData(macros, null);
      expect(axes.map((a) => a.color)).toEqual([
        MACRO_COLOR.carbs,
        MACRO_COLOR.fat,
        MACRO_COLOR.protein,
        MACRO_COLOR.fiber,
      ]);
    });

    it('avec cibles → série % objectif (100 = atteint) + série repère « Objectif » à 100, axes max 120', () => {
      const { axes, series } = macroRadarData(macros, { carbs: 120, fat: 40, protein: 60, fiber: 20 });
      expect(axes.every((a) => a.max === 120)).toBe(true);
      expect(series.length).toBe(2);
      expect(series[0].values).toEqual([50, 50, 50, 50]); // 60/120, 20/40, 30/60, 10/20
      expect(series[1].name).toBe('Objectif');
      expect(series[1].values).toEqual([100, 100, 100, 100]);
    });

    it('chaque axe porte la couleur de sa macro, avec cibles', () => {
      const { axes } = macroRadarData(macros, { carbs: 120, fat: 40, protein: 60, fiber: 20 });
      expect(axes.map((a) => a.color)).toEqual([
        MACRO_COLOR.carbs,
        MACRO_COLOR.fat,
        MACRO_COLOR.protein,
        MACRO_COLOR.fiber,
      ]);
    });

    it('libellés de légende personnalisables (défaut « Profil » / « Objectif »)', () => {
      const targets = { carbs: 120, fat: 40, protein: 60, fiber: 20 };
      // Défaut rétro-compatible (callers à 2 arguments).
      expect(macroRadarData(macros, targets).series.map((s) => s.name)).toEqual(['Profil', 'Objectif']);
      // Personnalisé pour distinguer clairement les 2 tracés selon la page.
      expect(
        macroRadarData(macros, targets, { value: 'Consommé', target: 'Cible' }).series.map((s) => s.name),
      ).toEqual(['Consommé', 'Cible']);
      expect(
        macroRadarData(macros, targets, { value: 'Réel (7 j)', target: 'Cible' }).series.map((s) => s.name),
      ).toEqual(['Réel (7 j)', 'Cible']);
      // Sans cible : le libellé « valeur » s'applique à l'unique tracé.
      expect(macroRadarData(macros, null, { value: 'Réel (7 j)' }).series.map((s) => s.name)).toEqual([
        'Réel (7 j)',
      ]);
    });
  });

  describe('microRadarData', () => {
    it('aucun micro renseigné → les 10 axes (minéraux puis vitamines) avec une série à 0', () => {
      const { axes, series } = microRadarData(ZERO_MICROS);
      // État vide « parlant » : tous les micronutriments possibles restent affichés (axes), couverture
      // nulle — au lieu d'un radar sans axes (qui retomberait sur le placeholder « Aucune donnée »).
      expect(axes.map((a) => a.label)).toEqual(MICRO_TARGETS.map((t) => t.label));
      expect(axes.every((a) => a.max === 120)).toBe(true);
      expect(series.length).toBe(1);
      expect(series[0].name).toBe('Couverture VNR');
      expect(series[0].values).toEqual(MICRO_TARGETS.map(() => 0));
    });

    it('au moins un micro renseigné → un axe par micro PRÉSENT seulement (cas normal inchangé)', () => {
      const { axes, series } = microRadarData({ ...ZERO_MICROS, ironPer100g: 7, vitaminCPer100g: 40 });
      // Fer (VNR 14) et Vit. C (VNR 80), tous deux à 50 % → seuls ces 2 axes, dans l'ordre canonique.
      expect(axes.map((a) => a.label)).toEqual(['Fer', 'Vit. C']);
      expect(series[0].values).toEqual([50, 50]);
    });
  });
});
