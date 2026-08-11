/**
 * 🎨 SOURCE UNIQUE de la couleur des micronutriments (DÉCISION validée 2026-06-15) — pendant micro
 * de macro-colors.ts. TOUT affichage de micro (rows du catalogue, picker, détail, résumé, stats,
 * entries du journal) DOIT tirer sa couleur d'ici (`MICRO_COLOR` / `MICRO_FAMILY_COLOR`, ou le
 * helper `microLineItems`), jamais d'une couleur codée en dur. Conséquence : changer un token
 * `--micro-*` ou une entrée de map propage la couleur PARTOUT, automatiquement.
 *
 * Contrairement aux macros (1 couleur par macro), les micros sont colorés par FAMILLE : 2 teintes.
 *   - Famille minéraux (Fe / Ca / Mg / Zn / K / Na) → `--micro-mineral` (rouge).
 *   - Famille vitamines (Vit C / D / B12 / A)       → `--micro-vitamin` (doré).
 * PAS de palette distincte par micro (10 couleurs), PAS de dégradé d'intensité par %VNR. Les 2
 * teintes (tokens SCSS `--micro-*`, dark + light, dans _colors.scss) restent lisibles sur fond
 * thirdBlue (`--app-bg-recessed`) et hors des 5 hues macros (`--macro-kcal/protein/carbs/fat/fiber`).
 *
 * Les libellés / abréviations / unités / familles vivent DÉJÀ dans micros.ts (MICRO_TARGETS) ; ce
 * module n'ajoute QUE le mapping couleur (micro → famille → token) + l'assemblage prêt-à-afficher.
 */
import { MicroFamily, MicroNutrients, MICRO_TARGETS, presentMicros } from './micros';

/** Token CSS de la teinte de chaque famille de micros (jamais de M3 brut). */
export const MICRO_FAMILY_COLOR: Record<MicroFamily, string> = {
  mineral: 'var(--micro-mineral)',
  vitamin: 'var(--micro-vitamin)',
};

/**
 * Couleur (token CSS) de chacun des 10 micros, dérivée de sa famille — source unique = MICRO_TARGETS
 * (micros.ts). Façon MACRO_COLOR pour les macros : consommée par les lignes / barres / anneaux micros.
 */
export const MICRO_COLOR: Record<keyof MicroNutrients, string> = MICRO_TARGETS.reduce(
  (acc, t) => ({ ...acc, [t.key]: MICRO_FAMILY_COLOR[t.family] }),
  {} as Record<keyof MicroNutrients, string>,
);

/** Couleur (token CSS) par libellé de micro — dérivée de MICRO_COLOR, pour `microLineItems`. */
const MICRO_COLOR_BY_LABEL = new Map(MICRO_TARGETS.map((t) => [t.label, MICRO_COLOR[t.key]]));

/** Un micro présent (valeur non nulle) prêt à afficher en ligne compacte, avec sa teinte famille. */
export interface MicroLineItem {
  /** Abréviation compacte (Fe, Ca, Mg… ; « Vit C » pour les vitamines). */
  short: string;
  /** Valeur arrondie (per-100 g, ou mise à l'échelle de la quantité si `factor` fourni). */
  value: number;
  unit: string;
  /** Teinte de la famille du micro (token CSS `--micro-*` via MICRO_COLOR). */
  color: string;
}

/**
 * Micros présents (valeur non nulle) en abréviations compactes, COLORÉS par famille — LE point
 * d'entrée unique pour afficher des micros colorés (rows du catalogue, picker, entries du journal,
 * résumé). Chaque micro est un item autonome (rendu comme un span coloré, séparés par « · »), jamais
 * une chaîne unique monochrome. La couleur vient de MICRO_COLOR (→ tokens `--micro-*`) : changer un
 * token / une famille propage ici et donc partout. `factor` met à l'échelle d'une quantité consommée
 * (ex. `quantityG / 100`) ; 1 par défaut = per-100 g. Pure, testable.
 */
export function microLineItems(m: MicroNutrients, factor = 1): MicroLineItem[] {
  return presentMicros(m, factor).map((i) => ({
    short: i.short,
    value: i.value,
    unit: i.unit,
    color: MICRO_COLOR_BY_LABEL.get(i.label) ?? MICRO_FAMILY_COLOR.mineral,
  }));
}
