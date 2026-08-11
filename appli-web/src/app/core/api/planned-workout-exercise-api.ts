import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PlannedWorkoutExercise } from '@core/models/planned-workout-exercise.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class PlannedWorkoutExerciseApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/planned-workout-exercises`;

  getAll(): Observable<PlannedWorkoutExercise[]> {
    return this.http.get<PlannedWorkoutExercise[]>(this.base);
  }

  upsert(e: PlannedWorkoutExercise): Observable<PlannedWorkoutExercise> {
    // Corps wire attendu par PlannedWorkoutExerciseCreate (camelCase).
    return this.http.put<PlannedWorkoutExercise>(`${this.base}/${e.uuid}`, {
      uuid: e.uuid,
      plannedWorkoutUUID: e.plannedWorkoutUUID,
      exerciseUUID: e.exerciseUUID,
      sets: e.sets,
      reps: e.reps,
      phase: e.phase,
      status: e.status,
      order: e.order,
      ignored: e.ignored,
      updatedAt: e.updatedAt,
    });
  }

  /** Upsert groupé `PUT /planned-workout-exercises/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: PlannedWorkoutExercise[]): Observable<PlannedWorkoutExercise[]> {
    return this.http.put<PlannedWorkoutExercise[]>(
      `${this.base}/bulk`,
      items.map((e) => ({
        uuid: e.uuid,
        plannedWorkoutUUID: e.plannedWorkoutUUID,
        exerciseUUID: e.exerciseUUID,
        sets: e.sets,
        reps: e.reps,
        phase: e.phase,
        status: e.status,
        order: e.order,
        ignored: e.ignored,
        updatedAt: e.updatedAt,
      })),
    );
  }

  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}
