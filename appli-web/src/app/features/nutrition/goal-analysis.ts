/**
 * Analyse dérivée d'une cible nutrition (page Objectifs) — répartition calorique par macro,
 * apport protéique rapporté au poids, densité en fibres. Logique pure et testable (hors DI), dans
 * l'esprit de `goal-macros.ts` : la page se contente d'afficher ces dérivés.
 */
import { fiberTargetG } from './journal-utils';
import { MacroKey } from './macro-colors';

/** Sous-ensemble d'une cible suffisant pour l'analyse (grammes + kcal stockée). */
export interface GoalMacroAmounts {
  kcal: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
}

/** Macros porteurs de calories (la ligne « kcal » n'est pas une part d'elle-même). */
export type KcalMacroKey = Extract<MacroKey, 'carbs' | 'fat' | 'protein' | 'fiber'>;

/** Ordre canonique des parts du donut (= MACRO_KEYS sans kcal). */
export const KCAL_MACRO_KEYS: readonly KcalMacroKey[] = ['carbs', 'fat', 'protein', 'fiber'];

/** Part calorique d'un macro de la cible : kcal apportées + % du total. */
export interface MacroKcalShare {
  key: KcalMacroKey;
  kcal: number;
  /** Part du total calorique, 0..100 (0 si total nul). */
  percent: number;
}

/**
 * Répartition calorique de la cible par macro (facteurs Atwater 4·P + 4·G + 9·L + 2·fibres, D12).
 * Les fibres de la cible sont dérivées du kcal (15 g/1000 kcal, `fiberTargetG`) ; leur poste
 * calorique (≈ 3 % par construction) est inclus pour que la somme des parts fasse 100 %. Pure.
 */
export function macroKcalBreakdown(goal: GoalMacroAmounts): MacroKcalShare[] {
  const fiberG = fiberTargetG(goal.kcal) ?? 0;
  const kcalByKey: Record<KcalMacroKey, number> = {
    carbs: 4 * goal.carbsG,
    fat: 9 * goal.fatG,
    protein: 4 * goal.proteinG,
    fiber: 2 * fiberG,
  };
  const total = KCAL_MACRO_KEYS.reduce((s, k) => s + kcalByKey[k], 0);
  return KCAL_MACRO_KEYS.map((key) => ({
    key,
    kcal: kcalByKey[key],
    percent: total > 0 ? (kcalByKey[key] / total) * 100 : 0,
  }));
}

/**
 * Apport d'un macro rapporté au poids corporel (g/kg) — repère muscu/diète courant, valable pour
 * protéines / glucides / lipides (même source de poids, même repli). null si le poids n'est pas
 * renseigné dans le profil (dégradation propre : la page affiche `—`). Pure.
 */
export function macroPerKg(macroG: number, weightKg: number | null | undefined): number | null {
  return weightKg && weightKg > 0 ? macroG / weightKg : null;
}

/**
 * Densité en fibres de la cible (g / 1000 kcal). Par construction ≈ 15 (cible dérivée à
 * 15 g/1000 kcal) — exposée comme repère santé. 0 si la cible n'a pas de kcal. Pure.
 */
export function fiberDensity(kcal: number): number {
  const fiberG = fiberTargetG(kcal) ?? 0;
  return kcal > 0 ? fiberG / (kcal / 1000) : 0;
}
