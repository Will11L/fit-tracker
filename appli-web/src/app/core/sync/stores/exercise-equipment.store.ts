import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { ExerciseEquipmentApi } from '@core/api/exercise-equipment-api';
import { LocalExerciseEquipment } from '@core/models/exercise-equipment.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

/** Adaptateur de sync pour ExerciseEquipment — miroir de ExerciseEquipmentSyncable (Android). */
@Injectable({ providedIn: 'root' })
export class ExerciseEquipmentStore extends BaseDexieStore<LocalExerciseEquipment> {
  readonly name = 'exercise_equipment';
  readonly wsKey = 'exercise_equipment';
  private readonly db = inject(AppDb);
  private readonly api = inject(ExerciseEquipmentApi);

  protected table(): Table<LocalExerciseEquipment, string> {
    return this.db.exercise_equipment;
  }

  async fetchRemote(): Promise<LocalExerciseEquipment[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((e) => ({ ...e, synced: true, pendingDeletion: false }));
  }

  async pushUpsert(row: LocalExerciseEquipment): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }

  async pushUpsertBulk(rows: LocalExerciseEquipment[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }

  async pushDelete(row: LocalExerciseEquipment): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
