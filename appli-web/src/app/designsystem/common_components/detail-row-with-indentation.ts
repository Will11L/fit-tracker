import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';

/**
 * Ligne de détail avec indentation — miroir de DetailRowWithIndentation.kt : icône 16px
 * alignée en haut + texte `label: value` (value en Medium) qui passe à la ligne sous
 * lui-même (le texte prend la largeur restante, l'icône reste en haut). 13px, lineHeight 20px.
 */
@Component({
  selector: 'app-detail-row-with-indentation',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <div class="dri">
      <app-icon [name]="icon()" [size]="16" [color]="iconColor()" />
      <span class="dri__text">
        <span [style.color]="labelColor()">{{ label() }}: </span><span
          class="dri__value"
          [style.color]="valueColor()"
          >{{ value() }}</span
        >
      </span>
    </div>
  `,
  styles: [
    `
      .dri {
        display: flex;
        align-items: flex-start;
        gap: var(--space-2);
        width: 100%;
        box-sizing: border-box;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-2) var(--space-3);
      }
      .dri__text {
        flex: 1;
        font-size: 13px;
        line-height: 20px;
      }
      .dri__value {
        font-weight: var(--font-weight-medium);
      }
    `,
  ],
})
export class DetailRowWithIndentation {
  readonly icon = input('');
  readonly iconColor = input('var(--app-text-tertiary)');
  readonly label = input('');
  readonly labelColor = input('var(--app-text-tertiary)');
  readonly value = input('');
  readonly valueColor = input('var(--app-text-tertiary)');
}
