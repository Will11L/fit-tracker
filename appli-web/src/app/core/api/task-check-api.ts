import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { TaskCheck } from '@core/models/task-check.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class TaskCheckApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/task-checks`;

  getAll(): Observable<TaskCheck[]> {
    return this.http.get<TaskCheck[]>(this.base);
  }

  upsert(c: TaskCheck): Observable<TaskCheck> {
    // Corps wire attendu par TaskCheckCreate (camelCase, sans userId).
    return this.http.put<TaskCheck>(`${this.base}/${c.uuid}`, {
      uuid: c.uuid,
      taskUUID: c.taskUUID,
      occurrenceDate: c.occurrenceDate,
      isChecked: c.isChecked,
      checkedAt: c.checkedAt ?? null,
      updatedAt: c.updatedAt,
    });
  }

  /** Upsert groupé `PUT /task-checks/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: TaskCheck[]): Observable<TaskCheck[]> {
    return this.http.put<TaskCheck[]>(
      `${this.base}/bulk`,
      items.map((c) => ({
        uuid: c.uuid,
        taskUUID: c.taskUUID,
        occurrenceDate: c.occurrenceDate,
        isChecked: c.isChecked,
        checkedAt: c.checkedAt ?? null,
        updatedAt: c.updatedAt,
      })),
    );
  }

  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}
