import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ActualWorkoutExercise } from '@core/models/actual-workout-exercise.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class ActualWorkoutExerciseApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/actual-workout-exercises`;

  getAll(): Observable<ActualWorkoutExercise[]> {
    return this.http.get<ActualWorkoutExercise[]>(this.base);
  }

  upsert(e: ActualWorkoutExercise): Observable<ActualWorkoutExercise> {
    // Corps wire attendu par ActualWorkoutExerciseCreate (camelCase).
    return this.http.put<ActualWorkoutExercise>(`${this.base}/${e.uuid}`, {
      uuid: e.uuid,
      actualWorkoutUUID: e.actualWorkoutUUID,
      exerciseUUID: e.exerciseUUID,
      sets: e.sets,
      reps: e.reps,
      phase: e.phase,
      status: e.status,
      order: e.order,
      addedManually: e.addedManually,
      updatedAt: e.updatedAt,
    });
  }

  /** Upsert groupé `PUT /actual-workout-exercises/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: ActualWorkoutExercise[]): Observable<ActualWorkoutExercise[]> {
    return this.http.put<ActualWorkoutExercise[]>(
      `${this.base}/bulk`,
      items.map((e) => ({
        uuid: e.uuid,
        actualWorkoutUUID: e.actualWorkoutUUID,
        exerciseUUID: e.exerciseUUID,
        sets: e.sets,
        reps: e.reps,
        phase: e.phase,
        status: e.status,
        order: e.order,
        addedManually: e.addedManually,
        updatedAt: e.updatedAt,
      })),
    );
  }

  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}
