/** RecipeIngredient — forme wire. Référence vivante vers Food (pas de snapshot : une recette est un modèle). */
export interface RecipeIngredient {
  uuid: string;
  recipeUUID: string;
  foodUUID: string;
  quantityG: number;
  orderIndex: number;
  updatedAt: string | null;
}

export interface LocalRecipeIngredient extends RecipeIngredient {
  synced: boolean;
  pendingDeletion: boolean;
}
