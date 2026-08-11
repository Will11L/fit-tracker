import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { FoodApi } from '@core/api/food-api';
import { LocalFood } from '@core/models/food.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

@Injectable({ providedIn: 'root' })
export class FoodStore extends BaseDexieStore<LocalFood> {
  readonly name = 'foods';
  readonly wsKey = 'food';
  private readonly db = inject(AppDb);
  private readonly api = inject(FoodApi);

  protected table(): Table<LocalFood, string> {
    return this.db.foods;
  }
  async fetchRemote(): Promise<LocalFood[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((f) => ({ ...f, synced: true, pendingDeletion: false }));
  }
  async pushUpsert(row: LocalFood): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }
  async pushUpsertBulk(rows: LocalFood[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }
  async pushDelete(row: LocalFood): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
