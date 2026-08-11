import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { SupersetGroup } from '@core/models/superset-group.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class SupersetGroupApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/superset-groups`;

  getAll(): Observable<SupersetGroup[]> {
    return this.http.get<SupersetGroup[]>(this.base);
  }

  upsert(g: SupersetGroup): Observable<SupersetGroup> {
    // Corps wire attendu par SupersetGroupCreate (camelCase, sans userId).
    return this.http.put<SupersetGroup>(`${this.base}/${g.uuid}`, {
      uuid: g.uuid,
      name: g.name,
      updatedAt: g.updatedAt,
    });
  }

  /** Upsert groupé `PUT /superset-groups/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: SupersetGroup[]): Observable<SupersetGroup[]> {
    return this.http.put<SupersetGroup[]>(
      `${this.base}/bulk`,
      items.map((g) => ({
        uuid: g.uuid,
        name: g.name,
        updatedAt: g.updatedAt,
      })),
    );
  }

  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}
