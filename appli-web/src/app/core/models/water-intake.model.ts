/**
 * WaterIntake — forme wire (hydratation). Une row = une prise d'eau horodatée.
 * Le total du jour se dérive par SUM(amountMl) sur la date (calculé côté client).
 * `date` = jour local "YYYY-MM-DD" (regroupement) ; `createdAt` = instant de la prise.
 */
export interface WaterIntake {
  uuid: string;
  userId: number;
  date: string;
  amountMl: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface LocalWaterIntake extends WaterIntake {
  synced: boolean;
  pendingDeletion: boolean;
}
