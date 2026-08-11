import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ExerciseMuscle } from '@core/models/exercise-muscle.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class ExerciseMuscleApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/exercise-muscles`;

  getAll(): Observable<ExerciseMuscle[]> {
    return this.http.get<ExerciseMuscle[]>(this.base);
  }

  upsert(m: ExerciseMuscle): Observable<ExerciseMuscle> {
    // Corps wire attendu par ExerciseMuscleCreate (camelCase).
    return this.http.put<ExerciseMuscle>(`${this.base}/${m.uuid}`, {
      uuid: m.uuid,
      exerciseUUID: m.exerciseUUID,
      muscleUUID: m.muscleUUID,
      coefficient: m.coefficient,
      updatedAt: m.updatedAt,
    });
  }

  /** Upsert groupé `PUT /exercise-muscles/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: ExerciseMuscle[]): Observable<ExerciseMuscle[]> {
    return this.http.put<ExerciseMuscle[]>(
      `${this.base}/bulk`,
      items.map((m) => ({
        uuid: m.uuid,
        exerciseUUID: m.exerciseUUID,
        muscleUUID: m.muscleUUID,
        coefficient: m.coefficient,
        updatedAt: m.updatedAt,
      })),
    );
  }

  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}
