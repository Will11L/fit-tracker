/**
 * HealthStepCount — forme wire (pas en tranches intraday). Une row = les pas d'une
 * tranche `bucketStart` ("HH:MM") d'un jour `date` ; le total du jour se dérive par
 * SUM(steps) sur la date (calculé côté client). **Lecture seule côté web** : les pas
 * viennent de Health Connect (Android), le web ne fait qu'afficher.
 */
export interface HealthStepCount {
  uuid: string;
  userId: number;
  date: string;
  bucketStart: string;
  steps: number;
  updatedAt: string | null;
}

export interface LocalHealthStepCount extends HealthStepCount {
  synced: boolean;
  pendingDeletion: boolean;
}
