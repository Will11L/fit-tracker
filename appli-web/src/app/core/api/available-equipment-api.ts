import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AvailableEquipment } from '@core/models/available-equipment.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class AvailableEquipmentApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/available-equipments`;

  getAll(): Observable<AvailableEquipment[]> {
    return this.http.get<AvailableEquipment[]>(this.base);
  }
  upsert(e: AvailableEquipment): Observable<AvailableEquipment> {
    return this.http.put<AvailableEquipment>(`${this.base}/${e.uuid}`, {
      uuid: e.uuid,
      name: e.name,
      updatedAt: e.updatedAt,
    });
  }
  /** Upsert groupé `PUT /available-equipments/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: AvailableEquipment[]): Observable<AvailableEquipment[]> {
    return this.http.put<AvailableEquipment[]>(
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
