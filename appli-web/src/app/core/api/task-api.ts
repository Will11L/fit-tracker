import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Task } from '@core/models/task.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class TaskApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/tasks`;

  getAll(): Observable<Task[]> {
    return this.http.get<Task[]>(this.base);
  }

  upsert(t: Task): Observable<Task> {
    // Corps wire attendu par TaskCreate (camelCase, sans userId). Validation conditionnelle côté serveur.
    return this.http.put<Task>(`${this.base}/${t.uuid}`, {
      uuid: t.uuid,
      title: t.title,
      notes: t.notes ?? null,
      isActive: t.isActive,
      order: t.order,
      recurrenceKind: t.recurrenceKind,
      dueDate: t.dueDate ?? null,
      dueTime: t.dueTime ?? null,
      periodUUID: t.periodUUID ?? null,
      recurrenceWeekdays: t.recurrenceWeekdays ?? null,
      recurrenceStartDate: t.recurrenceStartDate ?? null,
      recurrenceEndDate: t.recurrenceEndDate ?? null,
      excludedDates: t.excludedDates,
      reminderMinutesBefore: t.reminderMinutesBefore ?? null,
      updatedAt: t.updatedAt,
    });
  }

  /** Upsert groupé `PUT /tasks/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: Task[]): Observable<Task[]> {
    return this.http.put<Task[]>(
      `${this.base}/bulk`,
      items.map((t) => ({
        uuid: t.uuid,
        title: t.title,
        notes: t.notes ?? null,
        isActive: t.isActive,
        order: t.order,
        recurrenceKind: t.recurrenceKind,
        dueDate: t.dueDate ?? null,
        dueTime: t.dueTime ?? null,
        periodUUID: t.periodUUID ?? null,
        recurrenceWeekdays: t.recurrenceWeekdays ?? null,
        recurrenceStartDate: t.recurrenceStartDate ?? null,
        recurrenceEndDate: t.recurrenceEndDate ?? null,
        excludedDates: t.excludedDates,
        reminderMinutesBefore: t.reminderMinutesBefore ?? null,
        updatedAt: t.updatedAt,
      })),
    );
  }

  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}
