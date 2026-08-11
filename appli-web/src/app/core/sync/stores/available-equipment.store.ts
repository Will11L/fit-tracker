import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { AvailableEquipmentApi } from '@core/api/available-equipment-api';
import { LocalAvailableEquipment } from '@core/models/available-equipment.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

@Injectable({ providedIn: 'root' })
export class AvailableEquipmentStore extends BaseDexieStore<LocalAvailableEquipment> {
  readonly name = 'available_equipments';
  readonly wsKey = 'available_equipment';
  private readonly db = inject(AppDb);
  private readonly api = inject(AvailableEquipmentApi);

  protected table(): Table<LocalAvailableEquipment, string> {
    return this.db.available_equipments;
  }
  async fetchRemote(): Promise<LocalAvailableEquipment[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((e) => ({ ...e, synced: true, pendingDeletion: false }));
  }
  async pushUpsert(row: LocalAvailableEquipment): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }
  async pushUpsertBulk(rows: LocalAvailableEquipment[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }
  async pushDelete(row: LocalAvailableEquipment): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
