import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';

export type SnackbarType = 'SUCCESS' | 'WARNING' | 'ERROR' | 'INFO';

/** Un snackbar actif (= SnackbarEvent.kt côté Android, simplifié pour le wire UI). */
export interface SnackbarEvent {
  id: string;
  message: string;
  type: SnackbarType;
  actionLabel?: string;
  secondaryActionLabel?: string;
}

/**
 * Host des snackbars de l'app — miroir de AppSnackbarHost.kt (O14) : colonne de barres
 * bgRecessed (radius 12 + bordure 1.5 accent par type) avec icône tintée + message + actions
 * optionnelles. Couleur/icône résolues par `type` (theme-aware via tokens). Apparition slide+fade
 * décalée par index.
 *
 * Déviation : pas d'animation de sortie (Compose AnimatedVisibility exit) — le retrait du tableau
 * suffit ; l'entrée est animée en CSS.
 */
@Component({
  selector: 'app-snackbar-host',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <div class="snk">
      @for (e of snackbars(); track e.id; let i = $index) {
        <div class="snk__item" [style.border-color]="accent(e.type)" [style.animation-delay.ms]="i * 100">
          <div class="snk__row">
            <app-icon [name]="iconFor(e.type)" [size]="20" [color]="accent(e.type)" />
            <span class="snk__msg">{{ e.message }}</span>
          </div>
          @if (e.actionLabel || e.secondaryActionLabel) {
            <div class="snk__actions">
              @if (e.secondaryActionLabel) {
                <button class="snk__btn snk__btn--secondary" (click)="secondaryActionClick.emit(e.id)">{{ e.secondaryActionLabel }}</button>
              }
              @if (e.actionLabel) {
                <button class="snk__btn" [style.color]="accent(e.type)" (click)="actionClick.emit(e.id)">{{ e.actionLabel }}</button>
              }
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [
    `
      .snk {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        width: 100%;
      }
      .snk__item {
        background: var(--app-bg-recessed);
        border: 1.5px solid transparent;
        border-radius: var(--radius-lg);
        padding: var(--space-3);
        animation: snkIn 0.25s ease both;
      }
      .snk__row {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      .snk__msg {
        color: var(--app-text-primary);
        font-size: var(--font-size-body);
      }
      .snk__actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--space-2);
        margin-top: var(--space-1);
      }
      .snk__btn {
        background: transparent;
        border: none;
        cursor: pointer;
        font-family: var(--font-family-base);
        font-size: var(--font-size-body);
        font-weight: var(--font-weight-medium);
        padding: var(--space-1) var(--space-2);
        appearance: none;
        -webkit-appearance: none;
      }
      .snk__btn--secondary {
        color: var(--app-text-tertiary);
      }
      @keyframes snkIn {
        from {
          opacity: 0;
          transform: translateX(-16px);
        }
        to {
          opacity: 1;
          transform: translateX(0);
        }
      }
    `,
  ],
})
export class AppSnackbarHost {
  readonly snackbars = input<SnackbarEvent[]>([]);
  readonly actionClick = output<string>();
  readonly secondaryActionClick = output<string>();

  protected accent(t: SnackbarType): string {
    switch (t) {
      case 'SUCCESS':
        return 'var(--app-snackbar-success)';
      case 'WARNING':
        return 'var(--app-snackbar-warning)';
      case 'ERROR':
        return 'var(--app-snackbar-error)';
      default:
        return 'var(--app-primary-action)';
    }
  }

  protected iconFor(t: SnackbarType): string {
    switch (t) {
      case 'SUCCESS':
        return 'check_circle';
      case 'WARNING':
        return 'warning';
      case 'ERROR':
        return 'error';
      default:
        return 'info';
    }
  }
}
