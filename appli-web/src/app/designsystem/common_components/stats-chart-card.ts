import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TitledDivider } from '@designsystem/common_components/titled-divider';

/**
 * Bloc « chart titré » — miroir de StatsChartCard.kt : TitledDivider + soit un état vide
 * (zone 120px centrée, `emptyText`), soit le graphe (slot `[chart]`) + une légende optionnelle
 * (slot `[legend]`). Agnostique du type de graphe (projeté).
 */
@Component({
  selector: 'app-stats-chart-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TitledDivider],
  template: `
    <app-titled-divider [title]="title()" />
    @if (isEmpty()) {
      <div class="scc__empty">{{ emptyText() }}</div>
    } @else {
      <ng-content select="[chart]" />
      <div class="scc__legend"><ng-content select="[legend]" /></div>
    }
  `,
  styles: [
    `
      .scc__empty {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 100%;
        height: 120px;
        color: var(--app-primary-action);
      }
      .scc__legend {
        margin-top: var(--space-2);
      }
    `,
  ],
})
export class StatsChartCard {
  readonly title = input('');
  readonly isEmpty = input(false);
  readonly emptyText = input('');
}
