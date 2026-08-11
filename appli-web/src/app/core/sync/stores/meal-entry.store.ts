import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { MealEntryApi } from '@core/api/meal-entry-api';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

@Injectable({ providedIn: 'root' })
export class MealEntryStore extends BaseDexieStore<LocalMealEntry> {
  readonly name = 'meal_entries';
  readonly wsKey = 'meal_entry';
  private readonly db = inject(AppDb);
  private readonly api = inject(MealEntryApi);

  protected table(): Table<LocalMealEntry, string> {
    return this.db.meal_entries;
  }
  async fetchRemote(): Promise<LocalMealEntry[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((e) => ({ ...e, synced: true, pendingDeletion: false }));
  }
  async pushUpsert(row: LocalMealEntry): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }
  async pushUpsertBulk(rows: LocalMealEntry[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }
  async pushDelete(row: LocalMealEntry): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
