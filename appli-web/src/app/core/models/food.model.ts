/** Food — forme wire (catalogue d'aliments, per-100g). source: CUSTOM | CIQUAL | OFF (politique 11). */
export interface Food {
  uuid: string;
  userId: number;
  name: string;
  brand: string | null;
  source: string;
  sourceRef: string | null;
  // Groupe curaté UPPER_CASE (politique 11), ~18 valeurs (cf. food-category.ts). Nullable :
  // legacy + mappé au mieux à l'import (CIQUAL/OFF) ou saisi à la création custom. Le règne
  // (ANIMALE/VEGETALE/COMPLEMENT/AUTRE) n'est PAS stocké : dérivé via realmOf().
  foodGroup: string | null;
  kcalPer100g: number;
  proteinPer100g: number;
  carbsPer100g: number;
  fatPer100g: number;
  fiberPer100g: number | null;
  sugarPer100g: number | null;
  satFatPer100g: number | null;
  saltPer100g: number | null;
  // Vitamines & minéraux (pack essentiel ~10, D11 étendu) — minéraux en mg, vit C en mg, vit D/B12/A en µg.
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
  isFavorite: boolean;
  archived: boolean;
  // Hydratation (2026-07-05) : boisson eau → auto-comptage hydratation (1 g = 1 ml).
  // Posé à l'import OFF (categoriesTags famille `en:waters`) ou coché manuellement.
  isWater: boolean;
  updatedAt: string | null;
}

export interface LocalFood extends Food {
  synced: boolean;
  pendingDeletion: boolean;
}
