import { Injectable, Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { LocalHealthGoal } from '@core/models/health-goal.model';
import { LocalWaterIntake } from '@core/models/water-intake.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { uuidv4 } from '@core/utils/uuid';
import { WATER_GOAL_TYPE } from './hydration';
import { todayIso } from './journal-utils';

/**
 * Façade UI pour l'Hydratation : prises d'eau (water_intakes) + objectif journalier
 * (HealthGoal type WATER_ML). Lecture réactive Dexie liveQuery → signal ; écritures =
 * mutation locale optimiste (synced=false) puis sync best-effort. Le total du jour est
 * calculé dans la page (SUM prises + entrées repas eau), pas ici.
 */
@Injectable({ providedIn: 'root' })
export class HydrationRepository {
  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);
  private readonly auth = inject(AuthService);

  /** Prises d'eau (hors pendingDeletion). */
  readonly intakes: Signal<LocalWaterIntake[]> = toSignal(
    from(liveQuery(() => this.db.water_intakes.filter((w) => !w.pendingDeletion).toArray())),
    { initialValue: [] as LocalWaterIntake[] },
  );

  /** Objectifs santé (hors pendingDeletion) — on n'exploite que WATER_ML côté web. */
  readonly healthGoals: Signal<LocalHealthGoal[]> = toSignal(
    from(liveQuery(() => this.db.health_goals.filter((g) => !g.pendingDeletion).toArray())),
    { initialValue: [] as LocalHealthGoal[] },
  );

  /** Ajoute une prise d'eau manuelle (ml) au jour donné + push. */
  async addWater(day: string, amountMl: number): Promise<void> {
    if (amountMl <= 0) return;
    const now = new Date().toISOString();
    const row: LocalWaterIntake = {
      uuid: uuidv4(),
      userId: this.auth.currentUser()?.id ?? 0,
      date: day,
      amountMl,
      createdAt: now,
      updatedAt: now,
      synced: false,
      pendingDeletion: false,
    };
    await this.db.water_intakes.put(row);
    this.triggerSync();
  }

  /** Annule la dernière prise MANUELLE du jour (soft-delete). Les boissons eau journalisées
   *  ne sont pas des prises manuelles → se retirent en supprimant l'entrée repas. */
  async undoLastWater(day: string): Promise<void> {
    const list = (await this.db.water_intakes.where('date').equals(day).toArray()).filter(
      (w) => !w.pendingDeletion,
    );
    if (!list.length) return;
    const last = list.reduce((a, b) => ((a.createdAt ?? '') >= (b.createdAt ?? '') ? a : b));
    await this.db.water_intakes.update(last.uuid, {
      pendingDeletion: true,
      updatedAt: new Date().toISOString(),
    });
    this.triggerSync();
  }

  /** Règle l'objectif d'hydratation du jour (ml/jour) : met à jour le HealthGoal WATER_ML
   *  d'effectiveFrom = aujourd'hui s'il existe (interop Android/pull), sinon en crée un. */
  async setWaterGoal(ml: number): Promise<void> {
    if (ml <= 0) return;
    const today = todayIso();
    const now = new Date().toISOString();
    const existing = (await this.db.health_goals.toArray()).find(
      (g) => g.type === WATER_GOAL_TYPE && g.effectiveFrom === today && !g.pendingDeletion,
    );
    if (existing) {
      await this.db.health_goals.update(existing.uuid, { target: ml, synced: false, updatedAt: now });
    } else {
      const row: LocalHealthGoal = {
        uuid: uuidv4(),
        userId: this.auth.currentUser()?.id ?? 0,
        type: WATER_GOAL_TYPE,
        target: ml,
        effectiveFrom: today,
        updatedAt: now,
        synced: false,
        pendingDeletion: false,
      };
      await this.db.health_goals.put(row);
    }
    this.triggerSync();
  }

  private triggerSync(): void {
    void this.sync.syncAll().catch(() => undefined);
  }
}
