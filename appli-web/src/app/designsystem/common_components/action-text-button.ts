import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/**
 * Bouton texte — miroir de ActionTextButton.kt : texte (`textColor`, default textPrimary = blanc),
 * fond optionnel (`hasBackground` → `backgroundColor`, default bgButton), coins shapes.small (radius-md),
 * fontSize 14. Texte via input `text` ou `<ng-content>`.
 */
@Component({
  selector: 'app-action-text-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      class="atb"
      [style.color]="textColor()"
      [style.background]="hasBackground() ? backgroundColor() : 'transparent'"
      [disabled]="disabled()"
      (click)="clicked.emit()"
    >
      @if (text()) {
        {{ text() }}
      } @else {
        <ng-content />
      }
    </button>
  `,
  styles: [
    `
      .atb {
        font-family: var(--font-family-base);
        font-size: 14px;
        min-height: 40px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        padding: 0 var(--space-3);
        border: none;
        border-radius: var(--radius-md);
        appearance: none;
        -webkit-appearance: none;
        cursor: pointer;
        transition: filter 0.15s ease;
      }
      .atb:hover:not(:disabled) {
        filter: brightness(1.1);
      }
      .atb:disabled {
        opacity: 0.4;
        cursor: default;
      }
    `,
  ],
})
export class ActionTextButton {
  /** Texte inline (sinon utiliser `<ng-content>`). */
  readonly text = input('');
  readonly textColor = input('var(--app-text-primary)');
  readonly hasBackground = input(true);
  readonly backgroundColor = input('var(--app-bg-button)');
  readonly disabled = input(false);
  readonly clicked = output<void>();
}
