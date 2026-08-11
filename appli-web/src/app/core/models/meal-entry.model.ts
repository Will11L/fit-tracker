/**
 * MealEntry — forme wire (table centrale du journal). Snapshot D5 : macros per-100g
 * figées à l'ajout ; food/recipe = refs informatives (SET NULL en DB), nullables.
 * Totaux dérivés : total = per100g × quantityG / 100.
 */
export interface MealEntry {
  uuid: string;
  mealUUID: string;
  foodUUID: string | null;
  recipeUUID: string | null;
  displayName: string;
  quantityG: number;
  portionLabel: string | null;
  kcalPer100g: number;
  proteinPer100g: number;
  carbsPer100g: number;
  fatPer100g: number;
  fiberPer100g: number | null;
  sugarPer100g: number | null;
  satFatPer100g: number | null;
  saltPer100g: number | null;
  // Snapshot vitamines & minéraux (pack essentiel ~10, D11 étendu).
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
  updatedAt: string | null;
}

export interface LocalMealEntry extends MealEntry {
  synced: boolean;
  pendingDeletion: boolean;
}
