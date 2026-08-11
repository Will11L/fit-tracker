import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { FoodPortionApi } from '@core/api/food-portion-api';
import { LocalFoodPortion } from '@core/models/food-portion.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

@Injectable({ providedIn: 'root' })
export class FoodPortionStore extends BaseDexieStore<LocalFoodPortion> {
  readonly name = 'food_portions';
  readonly wsKey = 'food_portion';
  private readonly db = inject(AppDb);
  private readonly api = inject(FoodPortionApi);

  protected table(): Table<LocalFoodPortion, string> {
    return this.db.food_portions;
  }
  async fetchRemote(): Promise<LocalFoodPortion[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((p) => ({ ...p, synced: true, pendingDeletion: false }));
  }
  async pushUpsert(row: LocalFoodPortion): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }
  async pushUpsertBulk(rows: LocalFoodPortion[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }
  async pushDelete(row: LocalFoodPortion): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
