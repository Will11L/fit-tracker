import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { WaterIntakeApi } from '@core/api/water-intake-api';
import { LocalWaterIntake } from '@core/models/water-intake.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

@Injectable({ providedIn: 'root' })
export class WaterIntakeStore extends BaseDexieStore<LocalWaterIntake> {
  readonly name = 'water_intakes';
  readonly wsKey = 'water_intake';
  private readonly db = inject(AppDb);
  private readonly api = inject(WaterIntakeApi);

  protected table(): Table<LocalWaterIntake, string> {
    return this.db.water_intakes;
  }
  async fetchRemote(): Promise<LocalWaterIntake[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((w) => ({ ...w, synced: true, pendingDeletion: false }));
  }
  async pushUpsert(row: LocalWaterIntake): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }
  async pushUpsertBulk(rows: LocalWaterIntake[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }
  async pushDelete(row: LocalWaterIntake): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
