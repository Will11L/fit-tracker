import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { SupersetExercise } from '@core/models/superset-exercise.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class SupersetExerciseApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/superset-exercises`;

  getAll(): Observable<SupersetExercise[]> {
    return this.http.get<SupersetExercise[]>(this.base);
  }

  upsert(s: SupersetExercise): Observable<SupersetExercise> {
    // Corps wire attendu par SupersetExerciseCreate (camelCase).
    return this.http.put<SupersetExercise>(`${this.base}/${s.uuid}`, {
      uuid: s.uuid,
      supersetGroupUUID: s.supersetGroupUUID,
      exerciseUUID: s.exerciseUUID,
      orderInGroup: s.orderInGroup,
      updatedAt: s.updatedAt,
    });
  }

  /** Upsert groupé `PUT /superset-exercises/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: SupersetExercise[]): Observable<SupersetExercise[]> {
    return this.http.put<SupersetExercise[]>(
      `${this.base}/bulk`,
      items.map((s) => ({
        uuid: s.uuid,
        supersetGroupUUID: s.supersetGroupUUID,
        exerciseUUID: s.exerciseUUID,
        orderInGroup: s.orderInGroup,
        updatedAt: s.updatedAt,
      })),
    );
  }

  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}
