import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { NutritionGoalApi } from '@core/api/nutrition-goal-api';
import { LocalNutritionGoal } from '@core/models/nutrition-goal.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

@Injectable({ providedIn: 'root' })
export class NutritionGoalStore extends BaseDexieStore<LocalNutritionGoal> {
  readonly name = 'nutrition_goals';
  readonly wsKey = 'nutrition_goal';
  private readonly db = inject(AppDb);
  private readonly api = inject(NutritionGoalApi);

  protected table(): Table<LocalNutritionGoal, string> {
    return this.db.nutrition_goals;
  }
  async fetchRemote(): Promise<LocalNutritionGoal[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((g) => ({ ...g, synced: true, pendingDeletion: false }));
  }
  async pushUpsert(row: LocalNutritionGoal): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }
  async pushUpsertBulk(rows: LocalNutritionGoal[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }
  async pushDelete(row: LocalNutritionGoal): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
