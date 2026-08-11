/**
 * HealthMetric — forme wire (métrique santé générique, vendor-agnostic). `type`
 * UPPER_CASE (politique 11) : HEART_RATE | SLEEP | SPO2 | DISTANCE | ACTIVE_CALORIES ;
 * `value` + `unit` self-describing (bpm | min | % | m | km | kcal). `startTime` ("HH:MM")
 * optionnel pour les mesures intraday. **Lecture seule côté web** (source Health Connect).
 */
export interface HealthMetric {
  uuid: string;
  userId: number;
  type: string;
  value: number;
  unit: string;
  date: string;
  startTime: string | null;
  updatedAt: string | null;
}

export interface LocalHealthMetric extends HealthMetric {
  synced: boolean;
  pendingDeletion: boolean;
}
