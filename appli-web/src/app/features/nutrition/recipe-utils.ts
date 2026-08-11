import { LocalFood } from '@core/models/food.model';
import { LocalRecipe } from '@core/models/recipe.model';
import { LocalRecipeIngredient } from '@core/models/recipe-ingredient.model';
import { MacroTotals, ZERO_TOTALS } from './journal-utils';
import { MICRO_KEYS, MicroTotals, ZERO_MICRO_TOTALS } from './micros';
import { effectiveFoodKcal } from './food-kcal';

/**
 * Macros dérivées d'une recette (NUTRITION_DESIGN §3.3) — les ingrédients sont des références
 * vivantes vers Food (pas de snapshot) : tout est recalculé à la volée depuis le catalogue.
 */
export interface RecipeMacros {
  /** Totaux des ingrédients (somme des per-100g × quantité). */
  totals: MacroTotals;
  /** Somme des quantités d'ingrédients (poids cru). */
  ingredientsWeightG: number;
  /** Base de calcul per-100g : totalWeightG (poids cuit, kind=RECIPE) sinon poids cru. */
  weightBaseG: number;
  /** Macros per-100g du plat — snapshot à poser sur une MealEntry kind=RECIPE (au prorata). */
  per100g: MacroTotals;
  /**
   * Micros agrégés du plat (somme per-100g × quantité de chaque ingrédient, référence vivante) —
   * totaux absolus de la recette, façon `totals` pour les macros. Forme `MicroNutrients` (clés
   * `*Per100g`) pour alimenter directement le résumé micros (NutritionSummaryPanel, T4). null = 0.
   */
  microTotals: MicroTotals;
  /**
   * Micros per-100g du plat (microTotals rapportés à weightBaseG) — snapshot à poser sur une MealEntry
   * kind=RECIPE, comme `per100g` pour les macros, pour symétriser le tracking micros au journal (T7).
   */
  microPer100g: MicroTotals;
}

/** Recettes scindées par kind pour un affichage en deux sections (page Recettes & repas). */
export interface RecipesByKind<T extends Pick<LocalRecipe, 'kind'>> {
  /** kind=RECIPE (plats composés, insérés au prorata du poids consommé). */
  recipes: T[];
  /** kind=SAVED_MEAL (repas enregistrés, ingrédients insérés tels quels en un tap). */
  savedMeals: T[];
}

/**
 * Sépare une liste (recette ou ligne dérivée portant `recipe.kind`) en plats (RECIPE) et repas
 * enregistrés (SAVED_MEAL), en préservant l'ordre d'entrée. Pure, testable sans Angular.
 */
export function splitRecipesByKind<T extends { recipe: Pick<LocalRecipe, 'kind'> }>(
  rows: T[],
): { recipes: T[]; savedMeals: T[] } {
  const recipes: T[] = [];
  const savedMeals: T[] = [];
  for (const row of rows) {
    if (row.recipe.kind === 'SAVED_MEAL') savedMeals.push(row);
    else recipes.push(row);
  }
  return { recipes, savedMeals };
}

/**
 * Calcule les macros d'une recette depuis ses ingrédients et le catalogue. Les ingrédients
 * dont le Food n'existe plus sont ignorés (référence vivante). `totalWeightG` (kind=RECIPE)
 * gère le ratio cru/cuit : les mêmes macros réparties sur le poids final cuit.
 */
export function recipeMacros(
  recipe: Pick<LocalRecipe, 'kind' | 'totalWeightG'>,
  ingredients: Pick<LocalRecipeIngredient, 'foodUUID' | 'quantityG'>[],
  foodsByUuid: Map<string, LocalFood>,
): RecipeMacros {
  const totals: MacroTotals = { ...ZERO_TOTALS };
  const microTotals: MicroTotals = { ...ZERO_MICRO_TOTALS };
  let ingredientsWeightG = 0;
  for (const ing of ingredients) {
    const food = foodsByUuid.get(ing.foodUUID);
    if (!food) continue;
    const f = ing.quantityG / 100;
    // kcal effective selon la source de l'ingrédient (D12) : un brut CIQUAL dérive ses kcal des
    // macros, OFF garde l'étiquette — cohérent avec la kcal snapshotée des entries du journal.
    totals.kcal += effectiveFoodKcal(food) * f;
    totals.protein += food.proteinPer100g * f;
    totals.carbs += food.carbsPer100g * f;
    totals.fat += food.fatPer100g * f;
    totals.fiber += (food.fiberPer100g ?? 0) * f;
    // Micros : même règle que les macros (per-100g × facteur quantité, null traité 0) — D11.
    for (const k of MICRO_KEYS) {
      microTotals[k] += (food[k] ?? 0) * f;
    }
    ingredientsWeightG += ing.quantityG;
  }
  const weightBaseG =
    recipe.kind === 'RECIPE' && recipe.totalWeightG && recipe.totalWeightG > 0
      ? recipe.totalWeightG
      : ingredientsWeightG;
  const per100g: MacroTotals =
    weightBaseG > 0
      ? {
          kcal: (totals.kcal / weightBaseG) * 100,
          protein: (totals.protein / weightBaseG) * 100,
          carbs: (totals.carbs / weightBaseG) * 100,
          fat: (totals.fat / weightBaseG) * 100,
          fiber: (totals.fiber / weightBaseG) * 100,
        }
      : { ...ZERO_TOTALS };
  // Micros per-100g : même règle que les macros (totaux rapportés au poids de base, 0 si recette vide).
  const microPer100g: MicroTotals = { ...ZERO_MICRO_TOTALS };
  if (weightBaseG > 0) {
    for (const k of MICRO_KEYS) {
      microPer100g[k] = (microTotals[k] / weightBaseG) * 100;
    }
  }
  return { totals, ingredientsWeightG, weightBaseG, per100g, microTotals, microPer100g };
}
