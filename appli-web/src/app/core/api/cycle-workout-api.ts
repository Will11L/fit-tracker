import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CycleWorkout } from '@core/models/cycle-workout.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class CycleWorkoutApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/cycle-workouts`;

  getAll(): Observable<CycleWorkout[]> {
    return this.http.get<CycleWorkout[]>(this.base);
  }

  upsert(c: CycleWorkout): Observable<CycleWorkout> {
    // Corps wire attendu par CycleWorkoutCreate (camelCase).
    return this.http.put<CycleWorkout>(`${this.base}/${c.uuid}`, {
      uuid: c.uuid,
      trainingCycleUUID: c.trainingCycleUUID,
      plannedWorkoutUUID: c.plannedWorkoutUUID,
      updatedAt: c.updatedAt,
    });
  }

  /** Upsert groupé `PUT /cycle-workouts/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: CycleWorkout[]): Observable<CycleWorkout[]> {
    return this.http.put<CycleWorkout[]>(
      `${this.base}/bulk`,
      items.map((c) => ({
        uuid: c.uuid,
        trainingCycleUUID: c.trainingCycleUUID,
        plannedWorkoutUUID: c.plannedWorkoutUUID,
        updatedAt: c.updatedAt,
      })),
    );
  }

  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}
