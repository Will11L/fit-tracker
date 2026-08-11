import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Equipment } from '@core/models/equipment.model';
import { API_BASE_URL } from './api.config';

/** Equipment = global, écriture admin only. Côté web : pull seul (upsert/delete jamais appelés). */
@Injectable({ providedIn: 'root' })
export class EquipmentApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/equipments`;

  getAll(): Observable<Equipment[]> {
    return this.http.get<Equipment[]>(this.base);
  }
  upsert(e: Equipment): Observable<Equipment> {
    return this.http.put<Equipment>(`${this.base}/${e.uuid}`, {
      uuid: e.uuid,
      name: e.name,
      updatedAt: e.updatedAt,
    });
  }
  /** Upsert groupé `PUT /equipments/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: Equipment[]): Observable<Equipment[]> {
    return this.http.put<Equipment[]>(
      `${this.base}/bulk`,
      items.map((e) => ({
        uuid: e.uuid,
        name: e.name,
        updatedAt: e.updatedAt,
      })),
    );
  }
  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}
