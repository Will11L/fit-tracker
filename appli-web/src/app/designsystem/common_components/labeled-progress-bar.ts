import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ProgressBarPrimitive } from './progress-bar-primitive';

/**
 * Couleur d'une barre selon l'avancement (seuils) — miroir de progressColor() (LabeledProgressBar.kt).
 */
export function progressColor(value: number): string {
  if (value >= 1) return 'var(--app-primary-action)';
  if (value >= 0.75) return 'var(--c-medium-green)';
  if (value >= 0.5) return 'var(--c-light-green)';
  if (value >= 0.2) return 'var(--c-orange-medium)';
  return 'var(--c-red-medium)';
}

/** Barre de progression + pourcentage — miroir de LabeledProgressBar.kt. */
@Component({
  selector: 'app-labeled-progress-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ProgressBarPrimitive],
  template: `
    <div class="row">
      <app-progress-bar-primitive
        class="bar"
        [progress]="progress()"
        [color]="barColor()"
        [troughColor]="troughColor()"
      />
      @if (showPercent()) {
        <span class="pct" [style.color]="barColor()">{{ percent() }}%</span>
      }
    </div>
  `,
  styles: [
    `
      .row {
        display: flex;
        align-items: center;
        gap: var(--space-3);
        padding: var(--space-2) 0;
      }
      .bar {
        flex: 1;
      }
      .pct {
        min-width: 48px;
        text-align: center;
        font-size: 14px;
        font-weight: 600;
      }
    `,
  ],
})
export class LabeledProgressBar {
  /** Progression 0..1. */
  readonly progress = input(0);
  readonly showPercent = input(true);
  readonly troughColor = input('var(--app-bg-recessed)');

  protected readonly percent = computed(() => Math.round(this.progress() * 100));
  protected readonly barColor = computed(() => progressColor(this.progress()));
}
