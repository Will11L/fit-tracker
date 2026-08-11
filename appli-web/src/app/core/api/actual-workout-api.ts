import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ActualWorkout } from '@core/models/actual-workout.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class ActualWorkoutApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/actual-workouts`;

  getAll(): Observable<ActualWorkout[]> {
    return this.http.get<ActualWorkout[]>(this.base);
  }

  upsert(w: ActualWorkout): Observable<ActualWorkout> {
    // Corps wire attendu par ActualWorkoutCreate (camelCase, sans userId — posé par le serveur).
    return this.http.put<ActualWorkout>(`${this.base}/${w.uuid}`, {
      uuid: w.uuid,
      name: w.name,
      date: w.date,
      notes: w.notes ?? null,
      location: w.location ?? null,
      isDone: w.isDone,
      updatedAt: w.updatedAt,
    });
  }

  /** Upsert groupé `PUT /actual-workouts/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: ActualWorkout[]): Observable<ActualWorkout[]> {
    return this.http.put<ActualWorkout[]>(
      `${this.base}/bulk`,
      items.map((w) => ({
        uuid: w.uuid,
        name: w.name,
        date: w.date,
        notes: w.notes ?? null,
        location: w.location ?? null,
        isDone: w.isDone,
        updatedAt: w.updatedAt,
      })),
    );
  }

  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}
