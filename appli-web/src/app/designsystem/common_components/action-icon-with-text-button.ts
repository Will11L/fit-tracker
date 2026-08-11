import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { AppIcon } from '../icons/app-icon';

/**
 * Bouton icône + texte — miroir de ActionIconWithTextButton.kt : bouton plein (fond
 * backgroundColor), icône à gauche puis texte. Coins `shapes.small` (= radius-md),
 * padding 12/8, gap 8px. Texte via input `text` ou `<ng-content>`.
 */
@Component({
  selector: 'app-action-icon-with-text-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  host: { '[class.aitwb--full]': 'fullWidth()' },
  template: `
    <button
      type="button"
      class="aitb"
      [class.aitb--full]="fullWidth()"
      [style.background]="backgroundColor()"
      [disabled]="disabled() || !clickable()"
      (click)="clicked.emit()"
    >
      <app-icon [name]="icon()" [size]="iconSize()" [color]="tint()" />
      <span class="aitb__label" [style.color]="textColor()">
        @if (text()) {
          {{ text() }}
        } @else {
          <ng-content />
        }
      </span>
    </button>
  `,
  styles: [
    `
      .aitb {
        display: inline-flex;
        align-items: center;
        gap: var(--space-2);
        padding: var(--space-2) var(--space-3);
        border: none;
        border-radius: var(--radius-md);
        cursor: pointer;
        transition: filter 0.15s ease;
      }
      .aitb:hover:not(:disabled) {
        filter: brightness(1.08);
      }
      .aitb:disabled {
        opacity: 0.4;
        cursor: default;
      }
      /* Variante pleine largeur — miroir de Modifier.weight(1f).height(46.dp) (pages chrono). */
      :host(.aitwb--full) {
        display: flex;
        flex: 1;
      }
      .aitb--full {
        flex: 1;
        justify-content: center;
        min-height: 46px;
      }
      .aitb__label {
        font-family: var(--font-family-base);
        font-size: 14px;
      }
    `,
  ],
})
export class ActionIconWithTextButton {
  /** Clé d'icône dans APP_ICONS. */
  readonly icon = input('');
  /** Texte inline (sinon utiliser `<ng-content>`). */
  readonly text = input('');
  readonly backgroundColor = input('var(--app-primary-action)');
  readonly textColor = input('#ffffff');
  readonly tint = input('#ffffff');
  readonly iconSize = input(24);
  readonly disabled = input(false);
  readonly clickable = input(true);
  /** Pleine largeur + contenu centré + min-height 46px (= weight(1f).height(46.dp) Android). */
  readonly fullWidth = input(false);
  readonly clicked = output<void>();
}
