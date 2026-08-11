/** MealPreset — forme wire (période habituelle du journal, D10). defaultTime "HH:MM" indicatif. */
export interface MealPreset {
  uuid: string;
  userId: number;
  name: string;
  orderIndex: number;
  defaultTime: string | null;
  updatedAt: string | null;
}

export interface LocalMealPreset extends MealPreset {
  synced: boolean;
  pendingDeletion: boolean;
}
