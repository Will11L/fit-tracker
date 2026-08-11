/**
 * HealthGoal — forme wire (objectif santé versionné). L'objectif actif d'un `type`
 * un jour J = celui avec le plus grand effectiveFrom ≤ J. `type` UPPER_CASE
 * (politique 11) : STEPS | WATER_ML. Côté web, on ne s'en sert (v1) que pour
 * l'objectif d'hydratation WATER_ML (le domaine Santé n'a pas d'UI web).
 */
export interface HealthGoal {
  uuid: string;
  userId: number;
  type: string;
  target: number;
  effectiveFrom: string;
  updatedAt: string | null;
}

export interface LocalHealthGoal extends HealthGoal {
  synced: boolean;
  pendingDeletion: boolean;
}
