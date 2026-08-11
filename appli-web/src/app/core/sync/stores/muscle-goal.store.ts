import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { MuscleGoalApi } from '@core/api/muscle-goal-api';
import { LocalMuscleGoal } from '@core/models/muscle-goal.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

@Injectable({ providedIn: 'root' })
export class MuscleGoalStore extends BaseDexieStore<LocalMuscleGoal> {
  readonly name = 'muscle_goals';
  readonly wsKey = 'muscle_goal';
  private readonly db = inject(AppDb);
  private readonly api = inject(MuscleGoalApi);

  protected table(): Table<LocalMuscleGoal, string> {
    return this.db.muscle_goals;
  }
  async fetchRemote(): Promise<LocalMuscleGoal[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((g) => ({ ...g, synced: true, pendingDeletion: false }));
  }
  async pushUpsert(row: LocalMuscleGoal): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }
  async pushUpsertBulk(rows: LocalMuscleGoal[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }
  async pushDelete(row: LocalMuscleGoal): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
