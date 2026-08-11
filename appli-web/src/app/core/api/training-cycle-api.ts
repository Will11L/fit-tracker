import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { TrainingCycle } from '@core/models/training-cycle.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class TrainingCycleApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/training-cycles`;

  getAll(): Observable<TrainingCycle[]> {
    return this.http.get<TrainingCycle[]>(this.base);
  }

  upsert(c: TrainingCycle): Observable<TrainingCycle> {
    // Corps wire attendu par TrainingCycleCreate (camelCase, sans userId).
    return this.http.put<TrainingCycle>(`${this.base}/${c.uuid}`, {
      uuid: c.uuid,
      name: c.name,
      startDate: c.startDate,
      endDate: c.endDate,
      updatedAt: c.updatedAt,
    });
  }

  /** Upsert groupé `PUT /training-cycles/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: TrainingCycle[]): Observable<TrainingCycle[]> {
    return this.http.put<TrainingCycle[]>(
      `${this.base}/bulk`,
      items.map((c) => ({
        uuid: c.uuid,
        name: c.name,
        startDate: c.startDate,
        endDate: c.endDate,
        updatedAt: c.updatedAt,
      })),
    );
  }

  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}
