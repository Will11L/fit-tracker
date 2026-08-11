import { Injectable, Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { LocalNutritionGoal, NutritionGoal } from '@core/models/nutrition-goal.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { uuidv4 } from '@core/utils/uuid';

/**
 * Façade UI pour NutritionGoals (cibles quotidiennes kcal + macros).
 * La cible active un jour J = celle avec le plus grand effectiveFrom ≤ J (§3.7 NUTRITION_DESIGN) —
 * les stats passées comparent chaque jour à la cible qui était active ce jour-là.
 */
@Injectable({ providedIn: 'root' })
export class NutritionGoalRepository {
  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);
  private readonly auth = inject(AuthService);

  /** Historique des cibles trié par effectiveFrom croissant. */
  readonly goals: Signal<LocalNutritionGoal[]> = toSignal(
    from(liveQuery(() => this.db.nutrition_goals.filter((g) => !g.pendingDeletion).sortBy('effectiveFrom'))),
    { initialValue: [] as LocalNutritionGoal[] },
  );

  /** Cible active pour un jour donné ("YYYY-MM-DD") parmi une liste triée — null si aucune. */
  activeGoalFor(goals: LocalNutritionGoal[], date: string): LocalNutritionGoal | null {
    let active: LocalNutritionGoal | null = null;
    for (const g of goals) {
      if (g.effectiveFrom <= date && (!active || g.effectiveFrom > active.effectiveFrom)) active = g;
    }
    return active;
  }

  async create(input: {
    effectiveFrom: string;
    kcal: number;
    proteinG: number;
    carbsG: number;
    fatG: number;
  }): Promise<string> {
    const row: LocalNutritionGoal = {
      uuid: uuidv4(),
      userId: this.auth.currentUser()?.id ?? 0,
      effectiveFrom: input.effectiveFrom,
      dayKind: 'ALL',
      kcal: input.kcal,
      proteinG: input.proteinG,
      carbsG: input.carbsG,
      fatG: input.fatG,
      updatedAt: new Date().toISOString(),
      synced: false,
      pendingDeletion: false,
    };
    await this.db.nutrition_goals.put(row);
    this.triggerSync();
    return row.uuid;
  }

  async update(
    uuid: string,
    patch: Partial<Pick<NutritionGoal, 'effectiveFrom' | 'kcal' | 'proteinG' | 'carbsG' | 'fatG'>>,
  ): Promise<void> {
    await this.db.nutrition_goals.update(uuid, { ...patch, synced: false, updatedAt: new Date().toISOString() });
    this.triggerSync();
  }

  async remove(uuid: string): Promise<void> {
    await this.db.nutrition_goals.update(uuid, { pendingDeletion: true, updatedAt: new Date().toISOString() });
    this.triggerSync();
  }

  private triggerSync(): void {
    void this.sync.syncAll().catch(() => undefined);
  }
}
