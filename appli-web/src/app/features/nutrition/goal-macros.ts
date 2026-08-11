/**
 * Objectifs nutrition *macro-first* (NUTRITION_DESIGN D12, 2026-06-13).
 *
 * L'utilisateur saisit UNIQUEMENT les 3 macros en g (protéines, glucides hors-fibres,
 * lipides) ; le total kcal objectif se dérive en intégrant l'espace fibres :
 *
 *     base  = 4·P + 4·G + 9·L
 *     total = base / 0,97          (l'espace fibres = 15 g/1000 kcal × 2 kcal/g = 3 % du total)
 *     fibres_g = 15·total / 1000
 *
 * Le `/ 0,97` résout la circularité fibres↔kcal (les fibres dépendent du total, qui dépend
 * des fibres). Cohérent avec `fiberTargetG` (journal-utils) = 15 g/1000 kcal de la cible.
 * Ex. P180/G250/L80 → base 2440 → total 2515 kcal, 38 g fibres. Pure.
 */

/** Cible fibres santé : 15 g pour 1000 kcal (reco EU). Partagée avec le journal. */
export const FIBER_G_PER_1000_KCAL = 15;

/** Part calorique réservée aux fibres : 15 g/1000 kcal × 2 kcal/g = 0,03 du total. */
const FIBER_KCAL_FRACTION = (FIBER_G_PER_1000_KCAL * 2) / 1000; // 0,03

export interface GoalMacros {
  proteinG: number;
  carbsG: number;
  fatG: number;
}

export interface DerivedGoal {
  /** Total kcal objectif, espace fibres inclus (= valeur stockée en base). */
  kcal: number;
  /** Fibres cibles dérivées du total (g). */
  fiberG: number;
}

/**
 * Total kcal + fibres dérivés des 3 macros (macro-first, D12). Pure — pas d'arrondi
 * (l'affichage et le stockage arrondissent au besoin).
 */
export function deriveGoalFromMacros(m: GoalMacros): DerivedGoal {
  const base = 4 * m.proteinG + 4 * m.carbsG + 9 * m.fatG;
  const kcal = base / (1 - FIBER_KCAL_FRACTION); // base / 0,97
  const fiberG = (FIBER_G_PER_1000_KCAL * kcal) / 1000;
  return { kcal, fiberG };
}
