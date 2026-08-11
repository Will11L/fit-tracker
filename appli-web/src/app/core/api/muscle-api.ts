import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Muscle } from '@core/models/muscle.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class MuscleApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/muscles`;

  getAll(): Observable<Muscle[]> {
    return this.http.get<Muscle[]>(this.base);
  }

  upsert(m: Muscle): Observable<Muscle> {
    return this.http.put<Muscle>(`${this.base}/${m.uuid}`, toMuscleWire(m));
  }

  /** Upsert groupé `PUT /muscles/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: Muscle[]): Observable<Muscle[]> {
    return this.http.put<Muscle[]>(`${this.base}/bulk`, items.map(toMuscleWire));
  }

  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}

/** Corps wire attendu par MuscleCreate (camelCase, sans userId/synced/pendingDeletion). */
function toMuscleWire(m: Muscle) {
  return {
    uuid: m.uuid,
    name: m.name,
    muscleGroup: m.muscleGroup ?? null,
    zone: m.zone ?? null,
    isFavorite: m.isFavorite,
    updatedAt: m.updatedAt,
  };
}
