/**
 * Cohérence kcal ↔ macros (NUTRITION_DESIGN D12, 2026-06-13).
 *
 * Convention unique : glucides = glucides *disponibles* (fibres exclues), fibres = poste
 * calorique à part. Formule de référence (facteurs Atwater + fibres 2 kcal/g, EU 1169/2011) :
 *
 *     kcal = 4·prot + 4·gluc + 9·lip + 2·fibres
 *
 * La kcal *utilisée/affichée* d'un aliment dépend de sa source :
 *   - `CIQUAL` (brut)  → kcal **dérivée** des macros (cohérence macros↔kcal).
 *   - `OFF` (étiqueté) → kcal **de la source** (l'étiquette fait foi).
 *   - `CUSTOM`         → kcal saisie si fournie (> 0), sinon dérivée.
 *
 * Option (a) retenue (D12, reco) : `kcalPer100g` source reste conservée en base — la valeur
 * effective se dérive au moment où l'aliment entre dans un calcul qui perd la source (snapshot
 * d'une entry, somme d'ingrédients d'une recette) ou s'affiche dans le catalogue. Pas de
 * migration, valeur source toujours disponible « pour info ».
 */

/** Sources d'aliment (UPPER_CASE, politique 11). */
export const FOOD_SOURCE = {
  CUSTOM: 'CUSTOM',
  CIQUAL: 'CIQUAL',
  OFF: 'OFF',
} as const;

/** Sous-ensemble structurel d'un Food suffisant pour résoudre sa kcal effective. */
export interface FoodMacros {
  source: string;
  kcalPer100g: number;
  proteinPer100g: number;
  carbsPer100g: number;
  fatPer100g: number;
  fiberPer100g: number | null;
}

/**
 * kcal pour 100 g dérivée des macros : `4·P + 4·G + 9·L + 2·fibres` (D12). `fiber` null → 0
 * (poste fibres absent). Pure.
 */
export function kcalFromMacros(
  proteinG: number,
  carbsG: number,
  fatG: number,
  fiberG: number | null = 0,
): number {
  return 4 * proteinG + 4 * carbsG + 9 * fatG + 2 * (fiberG ?? 0);
}

/**
 * kcal effective per-100g d'un aliment selon sa source (D12) — voir l'en-tête du fichier.
 * Pure : `CIQUAL` dérive des macros, `OFF` garde l'étiquette, `CUSTOM` garde la valeur saisie
 * si elle est fournie (> 0) sinon dérive. Source inconnue → valeur stockée (repli sûr).
 */
export function effectiveFoodKcal(food: FoodMacros): number {
  const derived = kcalFromMacros(
    food.proteinPer100g,
    food.carbsPer100g,
    food.fatPer100g,
    food.fiberPer100g,
  );
  switch (food.source) {
    case FOOD_SOURCE.CIQUAL:
      return derived;
    case FOOD_SOURCE.OFF:
      return food.kcalPer100g;
    case FOOD_SOURCE.CUSTOM:
      return food.kcalPer100g > 0 ? food.kcalPer100g : derived;
    default:
      return food.kcalPer100g;
  }
}
