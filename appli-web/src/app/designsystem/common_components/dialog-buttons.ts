import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/**
 * Boutons de dialog — miroir de DialogButtons.kt (Android) : un bouton primaire plein
 * et un bouton secondaire bordé, split en deux composants standalone (cf. commit
 * "refonte des boutons de dialog").
 */

/** Bouton primaire (plein) — fond primaryAction, texte blanc. */
@Component({
  selector: 'app-dialog-primary-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      class="app-btn app-btn--primary"
      [style.background-color]="color()"
      [style.border-color]="color()"
      [disabled]="disabled()"
      (click)="clicked.emit()"
    >
      <ng-content />
    </button>
  `,
  styles: [
    `
      /* Hauteur calée sur ActionIconWithTextButton (padding vertical var(--space-2), sans min-height
         fixe) — bouton de dialog plus compact. */
      .app-btn {
        font-family: var(--font-family-base);
        font-size: 14px;
        font-weight: var(--font-weight-medium);
        display: inline-flex;
        align-items: center;
        justify-content: center;
        box-sizing: border-box;
        padding: var(--space-2) var(--space-4);
        border-radius: var(--radius-md);
        border: 1.5px solid transparent;
        cursor: pointer;
        transition: filter 0.15s ease, background-color 0.15s ease;
      }
      .app-btn:disabled {
        opacity: 0.5;
        cursor: default;
      }
      .app-btn--primary {
        background-color: var(--app-primary-action);
        color: #ffffff;
        border-color: var(--app-primary-action);
      }
      .app-btn--primary:hover:not(:disabled) {
        filter: brightness(1.08);
      }
    `,
  ],
})
export class DialogPrimaryButton {
  /** Couleur de fond (= param `color` Android) — ex. redMedium pour une confirmation destructive. */
  readonly color = input('var(--app-primary-action)');
  readonly disabled = input(false);
  readonly clicked = output<void>();
}

/** Bouton secondaire (bordé) — fond transparent, texte + bordure light-gray-blue. */
@Component({
  selector: 'app-dialog-secondary-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" class="app-btn app-btn--secondary" [disabled]="disabled()" (click)="clicked.emit()">
      <ng-content />
    </button>
  `,
  styles: [
    `
      /* Hauteur calée sur ActionIconWithTextButton (padding vertical var(--space-2), sans min-height
         fixe) — bouton de dialog plus compact. */
      .app-btn {
        font-family: var(--font-family-base);
        font-size: 14px;
        font-weight: var(--font-weight-medium);
        display: inline-flex;
        align-items: center;
        justify-content: center;
        box-sizing: border-box;
        padding: var(--space-2) var(--space-4);
        border-radius: var(--radius-md);
        border: 1.5px solid transparent;
        cursor: pointer;
        transition: filter 0.15s ease, background-color 0.15s ease;
      }
      .app-btn:disabled {
        opacity: 0.5;
        cursor: default;
      }
      .app-btn--secondary {
        background-color: transparent;
        color: var(--c-light-gray-blue);
        border-color: var(--c-light-gray-blue);
      }
      .app-btn--secondary:hover:not(:disabled) {
        background-color: color-mix(in srgb, var(--c-light-gray-blue) 12%, transparent);
      }
    `,
  ],
})
export class DialogSecondaryButton {
  readonly disabled = input(false);
  readonly clicked = output<void>();
}
