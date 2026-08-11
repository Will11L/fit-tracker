import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PlannedWorkout } from '@core/models/planned-workout.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class PlannedWorkoutApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/planned-workouts`;

  getAll(): Observable<PlannedWorkout[]> {
    return this.http.get<PlannedWorkout[]>(this.base);
  }

  upsert(w: PlannedWorkout): Observable<PlannedWorkout> {
    // Corps wire attendu par PlannedWorkoutCreate (camelCase, sans userId).
    return this.http.put<PlannedWorkout>(`${this.base}/${w.uuid}`, {
      uuid: w.uuid,
      name: w.name,
      dayOfWeek: w.dayOfWeek,
      updatedAt: w.updatedAt,
    });
  }

  /** Upsert groupé `PUT /planned-workouts/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: PlannedWorkout[]): Observable<PlannedWorkout[]> {
    return this.http.put<PlannedWorkout[]>(
      `${this.base}/bulk`,
      items.map((w) => ({
        uuid: w.uuid,
        name: w.name,
        dayOfWeek: w.dayOfWeek,
        updatedAt: w.updatedAt,
      })),
    );
  }

  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}
