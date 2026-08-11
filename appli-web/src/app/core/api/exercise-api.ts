import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Exercise } from '@core/models/exercise.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class ExerciseApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/exercises`;

  getAll(): Observable<Exercise[]> {
    return this.http.get<Exercise[]>(this.base);
  }

  upsert(e: Exercise): Observable<Exercise> {
    return this.http.put<Exercise>(`${this.base}/${e.uuid}`, toExerciseWire(e));
  }

  /** Upsert groupé `PUT /exercises/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: Exercise[]): Observable<Exercise[]> {
    return this.http.put<Exercise[]>(`${this.base}/bulk`, items.map(toExerciseWire));
  }

  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}

/** Corps wire attendu par ExerciseCreate (camelCase, sans userId/synced/pendingDeletion). */
function toExerciseWire(e: Exercise) {
  return {
    uuid: e.uuid,
    name: e.name,
    description: e.description ?? null,
    instructions: e.instructions ?? null,
    recommendedSets: e.recommendedSets ?? null,
    recommendedReps: e.recommendedReps ?? null,
    restTimeSeconds: e.restTimeSeconds ?? null,
    durationInSeconds: e.durationInSeconds ?? null,
    gifUrl: e.gifUrl ?? null,
    isFavorite: e.isFavorite,
    lastDone: e.lastDone ?? null,
    updatedAt: e.updatedAt,
  };
}
