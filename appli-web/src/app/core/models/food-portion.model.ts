/** FoodPortion — forme wire (portion nommée « 1 œuf = 60 g »). Enfant de Food (pas de userId). */
export interface FoodPortion {
  uuid: string;
  foodUUID: string;
  label: string;
  grams: number;
  updatedAt: string | null;
}

export interface LocalFoodPortion extends FoodPortion {
  synced: boolean;
  pendingDeletion: boolean;
}
