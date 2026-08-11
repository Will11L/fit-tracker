/**
 * NutritionGoal — forme wire (cibles quotidiennes kcal + macros). La cible active un jour J
 * = celle avec le plus grand effectiveFrom ≤ J. dayKind: ALL (v1, politique 11).
 */
export interface NutritionGoal {
  uuid: string;
  userId: number;
  effectiveFrom: string;
  dayKind: string;
  kcal: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
  updatedAt: string | null;
}

export interface LocalNutritionGoal extends NutritionGoal {
  synced: boolean;
  pendingDeletion: boolean;
}
