import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { HealthGoalApi } from '@core/api/health-goal-api';
import { LocalHealthGoal } from '@core/models/health-goal.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

@Injectable({ providedIn: 'root' })
export class HealthGoalStore extends BaseDexieStore<LocalHealthGoal> {
  readonly name = 'health_goals';
  readonly wsKey = 'health_goal';
  private readonly db = inject(AppDb);
  private readonly api = inject(HealthGoalApi);

  protected table(): Table<LocalHealthGoal, string> {
    return this.db.health_goals;
  }
  async fetchRemote(): Promise<LocalHealthGoal[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((g) => ({ ...g, synced: true, pendingDeletion: false }));
  }
  async pushUpsert(row: LocalHealthGoal): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }
  async pushUpsertBulk(rows: LocalHealthGoal[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }
  async pushDelete(row: LocalHealthGoal): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
