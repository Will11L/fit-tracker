import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { WaterIntake } from '@core/models/water-intake.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class WaterIntakeApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/water-intakes`;

  getAll(): Observable<WaterIntake[]> {
    return this.http.get<WaterIntake[]>(this.base);
  }
  upsert(w: WaterIntake): Observable<WaterIntake> {
    return this.http.put<WaterIntake>(`${this.base}/${w.uuid}`, this.toBody(w));
  }
  /** Upsert groupé `PUT /water-intakes/bulk` — 1 requête pour N rows. */
  upsertAll(items: WaterIntake[]): Observable<WaterIntake[]> {
    return this.http.put<WaterIntake[]>(`${this.base}/bulk`, items.map((w) => this.toBody(w)));
  }
  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }

  /** Corps wire attendu par WaterIntakeCreate (camelCase, sans userId — politique 8). */
  private toBody(w: WaterIntake) {
    return {
      uuid: w.uuid,
      date: w.date,
      amountMl: w.amountMl,
      createdAt: w.createdAt,
      updatedAt: w.updatedAt,
    };
  }
}
