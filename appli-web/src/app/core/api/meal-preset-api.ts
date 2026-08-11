import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { MealPreset } from '@core/models/meal-preset.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class MealPresetApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/meal-presets`;

  getAll(): Observable<MealPreset[]> {
    return this.http.get<MealPreset[]>(this.base);
  }
  upsert(p: MealPreset): Observable<MealPreset> {
    return this.http.put<MealPreset>(`${this.base}/${p.uuid}`, this.toBody(p));
  }
  /** Upsert groupé `PUT /meal-presets/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: MealPreset[]): Observable<MealPreset[]> {
    return this.http.put<MealPreset[]>(`${this.base}/bulk`, items.map((p) => this.toBody(p)));
  }
  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }

  /** Corps wire attendu par MealPresetCreate (camelCase, sans userId — politique 8). */
  private toBody(p: MealPreset) {
    return {
      uuid: p.uuid,
      name: p.name,
      orderIndex: p.orderIndex,
      defaultTime: p.defaultTime,
      updatedAt: p.updatedAt,
    };
  }
}
