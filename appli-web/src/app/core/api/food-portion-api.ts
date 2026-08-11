import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { FoodPortion } from '@core/models/food-portion.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class FoodPortionApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/food-portions`;

  getAll(): Observable<FoodPortion[]> {
    return this.http.get<FoodPortion[]>(this.base);
  }
  upsert(p: FoodPortion): Observable<FoodPortion> {
    return this.http.put<FoodPortion>(`${this.base}/${p.uuid}`, this.toBody(p));
  }
  /** Upsert groupé `PUT /food-portions/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: FoodPortion[]): Observable<FoodPortion[]> {
    return this.http.put<FoodPortion[]>(`${this.base}/bulk`, items.map((p) => this.toBody(p)));
  }
  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }

  /** Corps wire attendu par FoodPortionCreate (camelCase). */
  private toBody(p: FoodPortion) {
    return {
      uuid: p.uuid,
      foodUUID: p.foodUUID,
      label: p.label,
      grams: p.grams,
      updatedAt: p.updatedAt,
    };
  }
}
