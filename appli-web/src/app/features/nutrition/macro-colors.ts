/**
 * 🎨 SOURCE UNIQUE de la couleur des macros (NUTRITION_DESIGN, idée user 2026-06-13) — une couleur
 * dédiée par macro (calories / protéines / glucides / lipides / fibres), tirée des tokens SCSS
 * `--macro-*` (eux-mêmes adossés aux primitives de la palette, jamais de M3 brut). TOUT affichage
 * d'une macro (bandeau du Journal, rows du catalogue, picker, détail, barres, graphes echarts —
 * vue à plat + stats) DOIT tirer sa couleur de `MACRO_COLOR`, jamais d'une couleur codée en dur.
 * Conséquence : changer un token `--macro-*` propage la couleur PARTOUT, automatiquement. Façon
 * palette par zone des Stats sport. Pendant macro de micro-colors.ts (micros colorés par famille).
 */
export type MacroKey = 'kcal' | 'protein' | 'carbs' | 'fat' | 'fiber';

/** Ordre canonique d'affichage des macros. */
export const MACRO_KEYS: MacroKey[] = ['kcal', 'carbs', 'fat', 'protein', 'fiber'];

/** Macros comparables à une cible (les fibres n'ont pas de cible en v1, D11). */
export const MACRO_TARGET_KEYS = ['kcal', 'carbs', 'fat', 'protein'] as const;
export type MacroTargetKey = (typeof MACRO_TARGET_KEYS)[number];

/**
 * Sucres (plafond OMS du bandeau Journal) : teinte dédiée VOLONTAIREMENT hors MacroKey/MACRO_KEYS —
 * les sucres sont une limite (≤), pas une cible « à remplir », et ne doivent pas entrer dans les
 * boucles macros (anneaux du calendrier, stats, radar). Token `--macro-sugar` (dark + light).
 */
export const SUGAR_COLOR = 'var(--macro-sugar)';

/** Token CSS de la couleur de chaque macro. */
export const MACRO_COLOR: Record<MacroKey, string> = {
  kcal: 'var(--macro-kcal)',
  protein: 'var(--macro-protein)',
  carbs: 'var(--macro-carbs)',
  fat: 'var(--macro-fat)',
  fiber: 'var(--macro-fiber)',
};

/** Libellé long (FR). */
export const MACRO_LABEL: Record<MacroKey, string> = {
  kcal: 'Calories',
  protein: 'Protéines',
  carbs: 'Glucides',
  fat: 'Lipides',
  fiber: 'Fibres',
};

/** Abréviation compacte pour les lignes denses (P / G / L…). */
export const MACRO_SHORT: Record<MacroKey, string> = {
  kcal: 'kcal',
  protein: 'P',
  carbs: 'G',
  fat: 'L',
  fiber: 'F',
};

/** Abréviation moyenne (Gluc. / Lip. / Prot. / Fib.) — parts de donut où le nom complet est trop long
 *  mais où les sigles d'une lettre (MACRO_SHORT) seraient trop cryptiques. */
export const MACRO_ABBR: Record<MacroKey, string> = {
  kcal: 'kcal',
  protein: 'Prot.',
  carbs: 'Gluc.',
  fat: 'Lip.',
  fiber: 'Fib.',
};

/** Unité d'affichage de chaque macro. */
export const MACRO_UNIT: Record<MacroKey, string> = {
  kcal: 'kcal',
  protein: 'g',
  carbs: 'g',
  fat: 'g',
  fiber: 'g',
};

/** Icône Material par macro (en-têtes de sections / toggles). */
export const MACRO_ICON: Record<MacroKey, string> = {
  kcal: 'local_fire_department',
  protein: 'egg',
  carbs: 'bakery_dining',
  fat: 'water_drop',
  fiber: 'grass',
};
