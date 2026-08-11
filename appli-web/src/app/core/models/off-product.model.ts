/**
 * OffProduct — produit Open Food Facts normalisé per-100g par le proxy serveur
 * (`GET /nutrition/off/*`, docs/NUTRITION_DESIGN.md §4.1). Mêmes noms de champs que Food
 * pour copie directe vers le catalogue (source=OFF, sourceRef=barcode). Pas persisté localement.
 */
export interface OffProduct {
  sourceRef: string;
  name: string;
  brand: string | null;
  kcalPer100g: number;
  proteinPer100g: number;
  carbsPer100g: number;
  fatPer100g: number;
  fiberPer100g: number | null;
  sugarPer100g: number | null;
  satFatPer100g: number | null;
  saltPer100g: number | null;
  // Vitamines & minéraux (pack essentiel ~10, D11 étendu) — souvent partiels côté OFF.
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
  servingSize: string | null;
  servingQuantityG: number | null;
  // Catégories OFF brutes (tags slugifiés `en:`/`fr:`…) — mappées vers un groupe curaté à l'import
  // via food-category.ts (mapOffCategoriesToGroup). Best-effort, fallback AUTRE.
  categoriesTags: string[];
}
