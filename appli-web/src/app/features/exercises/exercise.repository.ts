import { Injectable, Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { Exercise, LocalExercise } from '@core/models/exercise.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { uuidv4 } from '@core/utils/uuid';

/**
 * Façade UI pour Exercises (miroir d'un ViewModel + repository Android).
 * Lecture réactive via Dexie liveQuery -> signal (équivalent Room Flow -> StateFlow).
 * Écritures = mutation locale optimiste (synced=false) puis sync best-effort.
 */
@Injectable({ providedIn: 'root' })
export class ExerciseRepository {
  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);
  private readonly auth = inject(AuthService);

  /** Liste réactive triée par nom, masquant les rows en attente de suppression. */
  readonly exercises: Signal<LocalExercise[]> = toSignal(
    from(liveQuery(() => this.db.exercises.filter((e) => !e.pendingDeletion).sortBy('name'))),
    { initialValue: [] as LocalExercise[] },
  );

  /** Crée l'exercice et retourne son uuid (pour créer les jonctions muscles/équipement ensuite). */
  async create(input: {
    name: string;
    description?: string | null;
    isFavorite?: boolean;
    recommendedSets?: number | null;
    recommendedReps?: string | null;
    restTimeSeconds?: number | null;
  }): Promise<string> {
    const row: LocalExercise = {
      uuid: uuidv4(),
      userId: this.auth.currentUser()?.id ?? 0,
      name: input.name,
      description: input.description ?? null,
      instructions: null,
      recommendedSets: input.recommendedSets ?? null,
      recommendedReps: input.recommendedReps ?? null,
      restTimeSeconds: input.restTimeSeconds ?? null,
      durationInSeconds: null,
      gifUrl: null,
      isFavorite: input.isFavorite ?? false,
      lastDone: null,
      updatedAt: new Date().toISOString(),
      synced: false,
      pendingDeletion: false,
    };
    await this.db.exercises.put(row);
    this.triggerSync();
    return row.uuid;
  }

  async update(
    uuid: string,
    patch: Partial<
      Pick<
        Exercise,
        'name' | 'description' | 'isFavorite' | 'recommendedSets' | 'recommendedReps' | 'restTimeSeconds' | 'instructions'
      >
    >,
  ): Promise<void> {
    await this.db.exercises.update(uuid, { ...patch, synced: false, updatedAt: new Date().toISOString() });
    this.triggerSync();
  }

  /**
   * Remplace les équipements liés à un exercice (diff vs jonctions existantes, miroir
   * ExerciseScreenViewModel.updateExercise Android) : retirés → pendingDeletion, ajoutés → nouvelles rows.
   */
  async setEquipments(exerciseUuid: string, equipmentUuids: string[]): Promise<void> {
    const now = new Date().toISOString();
    const existing = await this.db.exercise_equipment
      .filter((ee) => ee.exerciseUUID === exerciseUuid && !ee.pendingDeletion)
      .toArray();
    const wanted = new Set(equipmentUuids);
    const existingUuids = new Set(existing.map((ee) => ee.equipmentUUID));

    for (const ee of existing) {
      if (!wanted.has(ee.equipmentUUID)) {
        await this.db.exercise_equipment.update(ee.uuid, { pendingDeletion: true, synced: false, updatedAt: now });
      }
    }
    for (const equipmentUUID of equipmentUuids) {
      if (!existingUuids.has(equipmentUUID)) {
        await this.db.exercise_equipment.put({
          uuid: uuidv4(),
          exerciseUUID: exerciseUuid,
          equipmentUUID,
          updatedAt: now,
          synced: false,
          pendingDeletion: false,
        });
      }
    }
    this.triggerSync();
  }

  /**
   * Remplace les muscles ciblés d'un exercice (diff vs jonctions existantes, coefficient 1.0
   * comme addExerciseManually/updateExercise Android).
   */
  async setMuscles(exerciseUuid: string, muscleUuids: string[]): Promise<void> {
    const now = new Date().toISOString();
    const existing = await this.db.exercise_muscles
      .filter((em) => em.exerciseUUID === exerciseUuid && !em.pendingDeletion)
      .toArray();
    const wanted = new Set(muscleUuids);
    const existingUuids = new Set(existing.map((em) => em.muscleUUID));

    for (const em of existing) {
      if (!wanted.has(em.muscleUUID)) {
        await this.db.exercise_muscles.update(em.uuid, { pendingDeletion: true, synced: false, updatedAt: now });
      }
    }
    for (const muscleUUID of muscleUuids) {
      if (!existingUuids.has(muscleUUID)) {
        await this.db.exercise_muscles.put({
          uuid: uuidv4(),
          exerciseUUID: exerciseUuid,
          muscleUUID,
          coefficient: 1.0,
          updatedAt: now,
          synced: false,
          pendingDeletion: false,
        });
      }
    }
    this.triggerSync();
  }

  async remove(uuid: string): Promise<void> {
    // Tombstone local : marqué pendingDeletion, masqué de l'UI, DELETE poussé au prochain sync.
    await this.db.exercises.update(uuid, { pendingDeletion: true, updatedAt: new Date().toISOString() });
    this.triggerSync();
  }

  /** « Tout effacer » : marque tous les exercices pendingDeletion (DELETE poussés au prochain sync). */
  async removeAll(): Promise<void> {
    const now = new Date().toISOString();
    await this.db.exercises.toCollection().modify({ pendingDeletion: true, synced: false, updatedAt: now });
    this.triggerSync();
  }

  private triggerSync(): void {
    // Fire-and-forget, tolérant offline / non connecté : les rows non syncées repartiront plus tard.
    void this.sync.syncAll().catch(() => undefined);
  }
}
