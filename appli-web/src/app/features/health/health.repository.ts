import { Injectable, Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { LocalHealthStepCount } from '@core/models/health-step-count.model';
import { LocalHealthMetric } from '@core/models/health-metric.model';
import { LocalHealthGoal } from '@core/models/health-goal.model';
import { AppDb } from '@core/sync/dexie-db';

/**
 * Façade de lecture pour la section Santé — expose en signals les 3 sources Dexie
 * (pas intraday, métriques, objectifs), déjà filtrées de `pendingDeletion`. **Lecture seule** :
 * aucune méthode d'écriture (les données viennent de Health Connect via Android). Les objectifs
 * réutilisent le store `health_goals` partagé avec l'hydratation → filtrés par type dans l'UI.
 */
@Injectable({ providedIn: 'root' })
export class HealthRepository {
  private readonly db = inject(AppDb);

  readonly stepCounts: Signal<LocalHealthStepCount[]> = toSignal(
    from(liveQuery(() => this.db.health_step_counts.filter((s) => !s.pendingDeletion).toArray())),
    { initialValue: [] as LocalHealthStepCount[] },
  );

  readonly metrics: Signal<LocalHealthMetric[]> = toSignal(
    from(liveQuery(() => this.db.health_metrics.filter((m) => !m.pendingDeletion).toArray())),
    { initialValue: [] as LocalHealthMetric[] },
  );

  readonly goals: Signal<LocalHealthGoal[]> = toSignal(
    from(liveQuery(() => this.db.health_goals.filter((g) => !g.pendingDeletion).toArray())),
    { initialValue: [] as LocalHealthGoal[] },
  );
}
