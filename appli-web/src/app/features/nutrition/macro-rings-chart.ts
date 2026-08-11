import { type ConcentricRing } from '@designsystem/common_components/concentric-rings';
import { MACRO_COLOR, MACRO_LABEL, type MacroKey } from './macro-colors';
import type { MacroAmounts, MacroTargets } from './nutrition-summary-panel';

/**
 * Adaptateur macro → anneaux concentriques : construit les données d'anneaux passées au composant DS
 * générique `ConcentricRingsChart`. Pas un composant — juste la logique métier nutrition (couleurs /
 * libellés macro, avancement vs cible), partagée par le Profil macros des pages Stats et Objectifs.
 */

/** Épaisseur (unités viewBox) de chaque anneau : kcal (extérieur) un peu plus épais que les macros. */
const RING_WIDTHS: Record<MacroKey, number> = { kcal: 10.5, carbs: 7.5, fat: 7.5, protein: 7.5, fiber: 7.5 };
/** Ordre des anneaux, du plus EXTÉRIEUR (kcal) au plus intérieur (fibres). */
const RING_ORDER: MacroKey[] = ['kcal', 'carbs', 'fat', 'protein', 'fiber'];
/** Macros en grammes (anneaux intérieurs) — kcal est un chiffre, traité à part. */
const GRAM_KEYS: MacroKey[] = ['carbs', 'fat', 'protein', 'fiber'];

const clamp01 = (v: number): number => Math.max(0, Math.min(1, v));
const round = (v: number): number => Math.round(v);

/**
 * Construit les 5 anneaux concentriques macro (kcal extérieur → fibres centre) — pur, testable.
 * Avancement vs cible si fournie (kcal & macros), sinon : macros relatives au plus grand macro
 * (comparaison intra-profil) et kcal relatif à lui-même. Étiquette « Nom + % de l'objectif » (non
 * plafonné), repli sur « Nom + valeur » sans cible. Même sémantique que les anneaux du calendrier.
 */
export function macroRingViews(
  kcal: number,
  macros: MacroAmounts,
  targets: MacroTargets | null,
): ConcentricRing[] {
  const values: Record<MacroKey, number> = {
    kcal,
    carbs: macros.carbs ?? 0,
    fat: macros.fat ?? 0,
    protein: macros.protein ?? 0,
    fiber: macros.fiber ?? 0,
  };
  const useTargets = !!targets;
  const gramMax = Math.max(1, ...GRAM_KEYS.map((k) => values[k]));
  // Valeur SOUS le nom (2 lignes, demande user 2026-07-15 — gain de largeur, plus aéré).
  const label = (k: MacroKey, target: number | null): string => {
    const name = MACRO_LABEL[k];
    const unit = k === 'kcal' ? 'kcal' : 'g';
    return target && target > 0
      ? `${name}\n${round((values[k] / target) * 100)}%`
      : `${name}\n${round(values[k])} ${unit}`;
  };
  return RING_ORDER.map((k) => {
    const target = targets?.[k] ?? null;
    const denom =
      k === 'kcal'
        ? target && target > 0
          ? target
          : values.kcal || 1
        : useTargets
          ? target && target > 0
            ? target
            : values[k] || 1
          : gramMax;
    return {
      progress: clamp01(values[k] / denom),
      color: MACRO_COLOR[k],
      width: RING_WIDTHS[k],
      label: label(k, target),
    };
  });
}
