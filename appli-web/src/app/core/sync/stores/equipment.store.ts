import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { EquipmentApi } from '@core/api/equipment-api';
import { LocalEquipment } from '@core/models/equipment.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

/** Equipment = global, lecture seule côté web (push jamais déclenché : aucune création locale). */
@Injectable({ providedIn: 'root' })
export class EquipmentStore extends BaseDexieStore<LocalEquipment> {
  readonly name = 'equipments';
  readonly wsKey = 'equipment';
  private readonly db = inject(AppDb);
  private readonly api = inject(EquipmentApi);

  protected table(): Table<LocalEquipment, string> {
    return this.db.equipments;
  }
  async fetchRemote(): Promise<LocalEquipment[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((e) => ({ ...e, synced: true, pendingDeletion: false }));
  }
  async pushUpsert(row: LocalEquipment): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }
  async pushUpsertBulk(rows: LocalEquipment[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }
  async pushDelete(row: LocalEquipment): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
