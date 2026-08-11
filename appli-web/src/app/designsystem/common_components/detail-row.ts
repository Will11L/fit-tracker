import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';

/**
 * Ligne de détail — miroir de DetailRow.kt : icône 16px + `label:` + `value` (Medium),
 * tout en 13px, couleurs default textTertiary, alignés sur une ligne (gap 8px).
 */
@Component({
  selector: 'app-detail-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <div class="dr">
      <app-icon [name]="icon()" [size]="16" [color]="iconColor()" />
      <span class="dr__label" [style.color]="labelColor()">{{ label() }}:</span>
      <span class="dr__value" [style.color]="valueColor()">{{ value() }}</span>
    </div>
  `,
  styles: [
    `
      .dr {
        display: inline-flex;
        align-items: center;
        gap: var(--space-2);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-1) var(--space-3);
      }
      .dr__label {
        font-size: 13px;
      }
      .dr__value {
        font-size: 13px;
        font-weight: var(--font-weight-medium);
      }
    `,
  ],
})
export class DetailRow {
  readonly icon = input('');
  readonly iconColor = input('var(--app-text-tertiary)');
  readonly label = input('');
  readonly labelColor = input('var(--app-text-tertiary)');
  readonly value = input('');
  readonly valueColor = input('var(--app-text-tertiary)');
}
