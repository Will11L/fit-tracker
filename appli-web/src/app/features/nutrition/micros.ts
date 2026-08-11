/**
 * Vitamines & minéraux (pack essentiel ~10, D11 étendu 2026-06-13) — helpers d'affichage info.
 *
 * v1 : affichage seulement (pas de cibles). Minéraux + vitamine C en mg, vitamines D/B12/A en µg.
 * Les valeurs sont per-100 g (snapshot/catalogue). Pures, testables sans Angular.
 */

/** Sous-ensemble structurel d'un Food/MealEntry/OffProduct portant les 10 micros. */
export interface MicroNutrients {
  ironPer100g: number | null;
  calciumPer100g: number | null;
  magnesiumPer100g: number | null;
  zincPer100g: number | null;
  potassiumPer100g: number | null;
  sodiumPer100g: number | null;
  vitaminCPer100g: number | null;
  vitaminDPer100g: number | null;
  vitaminB12Per100g: number | null;
  vitaminAPer100g: number | null;
}

interface MicroSpec {
  key: keyof MicroNutrients;
  label: string;
  /** Abréviation compacte pour les lignes denses (symbole chimique pour les minéraux). */
  short: string;
  unit: 'mg' | 'µg';
}

/** Ordre d'affichage : minéraux puis vitamines (cohérent avec le cadrage de la tâche). */
const MICRO_SPECS: readonly MicroSpec[] = [
  { key: 'ironPer100g', label: 'Fer', short: 'Fe', unit: 'mg' },
  { key: 'calciumPer100g', label: 'Calcium', short: 'Ca', unit: 'mg' },
  { key: 'magnesiumPer100g', label: 'Magnésium', short: 'Mg', unit: 'mg' },
  { key: 'zincPer100g', label: 'Zinc', short: 'Zn', unit: 'mg' },
  { key: 'potassiumPer100g', label: 'Potassium', short: 'K', unit: 'mg' },
  { key: 'sodiumPer100g', label: 'Sodium', short: 'Na', unit: 'mg' },
  { key: 'vitaminCPer100g', label: 'Vit. C', short: 'Vit C', unit: 'mg' },
  { key: 'vitaminDPer100g', label: 'Vit. D', short: 'Vit D', unit: 'µg' },
  { key: 'vitaminB12Per100g', label: 'Vit. B12', short: 'Vit B12', unit: 'µg' },
  { key: 'vitaminAPer100g', label: 'Vit. A', short: 'Vit A', unit: 'µg' },
];

export interface MicroDisplayItem {
  label: string;
  /** Abréviation compacte (Fe, Ca, Mg… ; « Vit C » pour les vitamines). */
  short: string;
  value: number;
  unit: string;
}

/** Clés des 10 micros, ordre minéraux puis vitamines (= ordre d'affichage des barres/anneaux). */
export const MICRO_KEYS: readonly (keyof MicroNutrients)[] = MICRO_SPECS.map((s) => s.key);

/** Abréviation compacte par clé : symbole chimique pour les minéraux (Fe, Ca, Mg, Zn, K, Na), « Vit C/D/B12/A »
 *  pour les vitamines. Sert aux libellés denses (parts de donut, lignes compactes). */
export const MICRO_SHORT: Record<keyof MicroNutrients, string> = MICRO_SPECS.reduce(
  (acc, s) => ({ ...acc, [s.key]: s.short }),
  {} as Record<keyof MicroNutrients, string>,
);

/** Cumul jour d'un micro pour chacune des 10 clés (valeurs absolues, jamais null — 0 par défaut). */
export type MicroTotals = Record<keyof MicroNutrients, number>;

export const ZERO_MICRO_TOTALS: MicroTotals = MICRO_SPECS.reduce(
  (acc, s) => ({ ...acc, [s.key]: 0 }),
  {} as MicroTotals,
);

/** Famille d'un micro (teinte d'affichage par famille, pas de couleur cible imposée). */
export type MicroFamily = 'mineral' | 'vitamin';

/**
 * Cible d'un micro. Pour 9 des 10 micros = VNR officielle UE (règlement 1169/2011, annexe XIII),
 * objectif à atteindre. Pour le Sodium = repère LIMITE (plafond 2000 mg, repère OMS), pas un
 * objectif : à afficher comme plafond, en alerte si dépassé (`isLimit`).
 */
export interface MicroTarget {
  key: keyof MicroNutrients;
  label: string;
  unit: 'mg' | 'µg';
  family: MicroFamily;
  /** VNR (objectif) ou plafond (Sodium). */
  target: number;
  /** true => repère plafond (Sodium) : barre d'alerte si dépassé, pas un objectif à remplir. */
  isLimit: boolean;
}

/** Cibles VNR UE + repère plafond Sodium (constantes fixes, ordre minéraux puis vitamines). */
export const MICRO_TARGETS: readonly MicroTarget[] = [
  { key: 'ironPer100g', label: 'Fer', unit: 'mg', family: 'mineral', target: 14, isLimit: false },
  { key: 'calciumPer100g', label: 'Calcium', unit: 'mg', family: 'mineral', target: 800, isLimit: false },
  { key: 'magnesiumPer100g', label: 'Magnésium', unit: 'mg', family: 'mineral', target: 375, isLimit: false },
  { key: 'zincPer100g', label: 'Zinc', unit: 'mg', family: 'mineral', target: 10, isLimit: false },
  { key: 'potassiumPer100g', label: 'Potassium', unit: 'mg', family: 'mineral', target: 2000, isLimit: false },
  { key: 'sodiumPer100g', label: 'Sodium', unit: 'mg', family: 'mineral', target: 2000, isLimit: true },
  { key: 'vitaminCPer100g', label: 'Vit. C', unit: 'mg', family: 'vitamin', target: 80, isLimit: false },
  { key: 'vitaminDPer100g', label: 'Vit. D', unit: 'µg', family: 'vitamin', target: 5, isLimit: false },
  { key: 'vitaminB12Per100g', label: 'Vit. B12', unit: 'µg', family: 'vitamin', target: 2.5, isLimit: false },
  { key: 'vitaminAPer100g', label: 'Vit. A', unit: 'µg', family: 'vitamin', target: 800, isLimit: false },
];

/** Ligne d'affichage d'un micro vs sa cible (barre ou anneau), dérivée des cumuls du jour. */
export interface MicroRow extends MicroTarget {
  value: number;
  /** Avancement borné 0..1 (pour la barre / l'anneau). */
  progress: number;
  /** Sodium seulement : cumul strictement au-dessus du plafond (déclenche la couleur d'alerte). */
  exceeded: boolean;
}

/** Construit les 10 lignes micros (cumul vs VNR / plafond) — pure, testable sans Angular. */
export function microRows(totals: MicroTotals): MicroRow[] {
  return MICRO_TARGETS.map((s) => {
    const value = totals[s.key];
    const ratio = s.target > 0 ? value / s.target : 0;
    return {
      ...s,
      value,
      progress: Math.max(0, Math.min(1, ratio)),
      exceeded: s.isLimit && value > s.target,
    };
  });
}

/**
 * Micros présents (valeur non nulle), arrondis à 1 décimale. `factor` met les valeurs à l'échelle
 * d'une quantité consommée (ex. `quantityG / 100` pour une entry du journal) ; 1 par défaut =
 * per-100 g. Donnée structurée canonique : l'affichage COLORÉ par famille passe par `microLineItems`
 * (micro-colors.ts) — aucune couleur n'est décidée ici (séparation donnée / code couleur).
 */
export function presentMicros(m: MicroNutrients, factor = 1): MicroDisplayItem[] {
  return MICRO_SPECS.filter((s) => m[s.key] != null).map((s) => ({
    label: s.label,
    short: s.short,
    value: Math.round((m[s.key] as number) * factor * 10) / 10,
    unit: s.unit,
  }));
}
