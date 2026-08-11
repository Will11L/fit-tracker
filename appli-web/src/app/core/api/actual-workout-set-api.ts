import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ActualWorkoutSet } from '@core/models/actual-workout-set.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class ActualWorkoutSetApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/actual-workout-sets`;

  getAll(): Observable<ActualWorkoutSet[]> {
    return this.http.get<ActualWorkoutSet[]>(this.base);
  }

  upsert(s: ActualWorkoutSet): Observable<ActualWorkoutSet> {
    // Corps wire attendu par ActualWorkoutSetCreate (camelCase).
    return this.http.put<ActualWorkoutSet>(`${this.base}/${s.uuid}`, {
      uuid: s.uuid,
      actualWorkoutExerciseUUID: s.actualWorkoutExerciseUUID,
      setOrder: s.setOrder,
      reps: s.reps,
      weight: s.weight,
      isDropset: s.isDropset,
      notes: s.notes ?? null,
      recommendation: s.recommendation ?? null,
      status: s.status,
      updatedAt: s.updatedAt,
    });
  }

  /** Upsert groupé `PUT /actual-workout-sets/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: ActualWorkoutSet[]): Observable<ActualWorkoutSet[]> {
    return this.http.put<ActualWorkoutSet[]>(
      `${this.base}/bulk`,
      items.map((s) => ({
        uuid: s.uuid,
        actualWorkoutExerciseUUID: s.actualWorkoutExerciseUUID,
        setOrder: s.setOrder,
        reps: s.reps,
        weight: s.weight,
        isDropset: s.isDropset,
        notes: s.notes ?? null,
        recommendation: s.recommendation ?? null,
        status: s.status,
        updatedAt: s.updatedAt,
      })),
    );
  }

  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}
