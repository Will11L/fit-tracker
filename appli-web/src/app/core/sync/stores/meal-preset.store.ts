import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { MealPresetApi } from '@core/api/meal-preset-api';
import { LocalMealPreset } from '@core/models/meal-preset.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

@Injectable({ providedIn: 'root' })
export class MealPresetStore extends BaseDexieStore<LocalMealPreset> {
  readonly name = 'meal_presets';
  readonly wsKey = 'meal_preset';
  private readonly db = inject(AppDb);
  private readonly api = inject(MealPresetApi);

  protected table(): Table<LocalMealPreset, string> {
    return this.db.meal_presets;
  }
  async fetchRemote(): Promise<LocalMealPreset[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((p) => ({ ...p, synced: true, pendingDeletion: false }));
  }
  async pushUpsert(row: LocalMealPreset): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }
  async pushUpsertBulk(rows: LocalMealPreset[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }
  async pushDelete(row: LocalMealPreset): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
