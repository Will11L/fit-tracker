import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ExerciseEquipment } from '@core/models/exercise-equipment.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class ExerciseEquipmentApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/exercise-equipments`;

  getAll(): Observable<ExerciseEquipment[]> {
    return this.http.get<ExerciseEquipment[]>(this.base);
  }

  upsert(m: ExerciseEquipment): Observable<ExerciseEquipment> {
    // Corps wire attendu par ExerciseEquipmentCreate (camelCase).
    return this.http.put<ExerciseEquipment>(`${this.base}/${m.uuid}`, {
      uuid: m.uuid,
      exerciseUUID: m.exerciseUUID,
      equipmentUUID: m.equipmentUUID,
      updatedAt: m.updatedAt,
    });
  }

  /** Upsert groupé `PUT /exercise-equipments/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: ExerciseEquipment[]): Observable<ExerciseEquipment[]> {
    return this.http.put<ExerciseEquipment[]>(
      `${this.base}/bulk`,
      items.map((m) => ({
        uuid: m.uuid,
        exerciseUUID: m.exerciseUUID,
        equipmentUUID: m.equipmentUUID,
        updatedAt: m.updatedAt,
      })),
    );
  }

  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}
