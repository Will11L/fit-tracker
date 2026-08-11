import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { MealApi } from '@core/api/meal-api';
import { LocalMeal } from '@core/models/meal.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

@Injectable({ providedIn: 'root' })
export class MealStore extends BaseDexieStore<LocalMeal> {
  readonly name = 'meals';
  readonly wsKey = 'meal';
  private readonly db = inject(AppDb);
  private readonly api = inject(MealApi);

  protected table(): Table<LocalMeal, string> {
    return this.db.meals;
  }
  async fetchRemote(): Promise<LocalMeal[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((m) => ({ ...m, synced: true, pendingDeletion: false }));
  }
  async pushUpsert(row: LocalMeal): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }
  async pushUpsertBulk(rows: LocalMeal[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }
  async pushDelete(row: LocalMeal): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
