import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';

/**
 * Ligne d'état vide — miroir de EmptyListRow.kt : texte italique Medium sur fond bgRecessed,
 * boîte 44px (radius 8). Avec icône (18px, alpha 0.5) → icône + texte centrés ; sans icône →
 * texte aligné à gauche (padding horizontal 12px). contentColor default blueMedium.
 */
@Component({
  selector: 'app-empty-list-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <div class="elr" [style.padding-block.px]="verticalPadding()">
      <div class="elr__inner" [class.elr__inner--icon]="!!icon()" [style.background]="backgroundColor()">
        @if (icon()) {
          <app-icon class="elr__icon" [name]="icon()" [size]="18" [color]="contentColor()" />
        }
        <span class="elr__text" [style.color]="contentColor()" [style.font-size.px]="fontSize()">{{ text() }}</span>
      </div>
    </div>
  `,
  styles: [
    `
      .elr {
        width: 100%;
      }
      .elr__inner {
        display: flex;
        align-items: center;
        justify-content: flex-start;
        width: 100%;
        height: 44px;
        border-radius: var(--radius-md);
        padding: 0 var(--space-3);
        box-sizing: border-box;
      }
      .elr__inner--icon {
        justify-content: center;
        padding: 0;
      }
      .elr__icon {
        opacity: 0.5;
        margin-right: var(--space-2);
      }
      .elr__text {
        font-weight: var(--font-weight-medium);
        font-style: italic;
      }
    `,
  ],
})
export class EmptyListRow {
  readonly text = input('');
  /** Ligature Material Symbols ; vide = pas d'icône (texte aligné à gauche). */
  readonly icon = input('');
  readonly backgroundColor = input('var(--app-bg-recessed)');
  readonly contentColor = input('var(--c-blue-medium)');
  readonly fontSize = input(14);
  readonly verticalPadding = input(4);
}
