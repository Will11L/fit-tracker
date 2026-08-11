/** Recipe — forme wire. kind: RECIPE (plat, macros au prorata) | SAVED_MEAL (insertion des ingrédients). */
export interface Recipe {
  uuid: string;
  userId: number;
  name: string;
  kind: string;
  totalWeightG: number | null;
  updatedAt: string | null;
}

export interface LocalRecipe extends Recipe {
  synced: boolean;
  pendingDeletion: boolean;
}
