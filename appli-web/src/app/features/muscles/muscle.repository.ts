import { Injectable, Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { LocalMuscle, Muscle } from '@core/models/muscle.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { uuidv4 } from '@core/utils/uuid';

/** Façade UI pour Muscles (miroir d'ExerciseRepository). Lecture réactive Dexie liveQuery -> signal. */
@Injectable({ providedIn: 'root' })
export class MuscleRepository {
  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);
  private readonly auth = inject(AuthService);

  readonly muscles: Signal<LocalMuscle[]> = toSignal(
    from(liveQuery(() => this.db.muscles.filter((m) => !m.pendingDeletion).sortBy('name'))),
    { initialValue: [] as LocalMuscle[] },
  );

  async create(input: { name: string; zone?: string | null }): Promise<void> {
    const row: LocalMuscle = {
      uuid: uuidv4(),
      userId: this.auth.currentUser()?.id ?? 0,
      name: input.name,
      muscleGroup: null,
      zone: input.zone ?? null,
      isFavorite: false,
      updatedAt: new Date().toISOString(),
      synced: false,
      pendingDeletion: false,
    };
    await this.db.muscles.put(row);
    this.triggerSync();
  }

  async update(
    uuid: string,
    patch: Partial<Pick<Muscle, 'name' | 'isFavorite' | 'zone' | 'muscleGroup'>>,
  ): Promise<void> {
    await this.db.muscles.update(uuid, { ...patch, synced: false, updatedAt: new Date().toISOString() });
    this.triggerSync();
  }

  async remove(uuid: string): Promise<void> {
    await this.db.muscles.update(uuid, { pendingDeletion: true, updatedAt: new Date().toISOString() });
    this.triggerSync();
  }

  /** « Tout effacer » : marque tous les muscles pendingDeletion (DELETE poussés au prochain sync). */
  async removeAll(): Promise<void> {
    const now = new Date().toISOString();
    await this.db.muscles.toCollection().modify({ pendingDeletion: true, synced: false, updatedAt: now });
    this.triggerSync();
  }

  private triggerSync(): void {
    void this.sync.syncAll().catch(() => undefined);
  }
}
