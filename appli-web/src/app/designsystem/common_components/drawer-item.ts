import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';

/**
 * Item de navigation du drawer — miroir de DrawerItem.kt / Figma M11 : icône 22px + label 14px
 * Medium + slot `[trailing]` optionnel (DrawerIconCountIndicator / DrawerMiniProgress / badge stats).
 * Présentationnel (émet `clicked`) ; l'actif (fond selected-fill) est piloté par `active`.
 * Hairline 1px (divider-strong @ 0.3, inset 18) sous chaque item sauf le dernier / l'actif —
 * rendu via `:host` pour fonctionner avec la projection de contenu de DrawerSection.
 */
@Component({
  selector: 'app-drawer-item',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  host: { '[class.is-active]': 'active()', '[class.is-collapsed]': 'collapsed()' },
  template: `
    <button type="button" class="di" [class.di--active]="active()" (click)="clicked.emit()">
      <app-icon
        [name]="icon()"
        [size]="22"
        [color]="active() ? 'var(--app-text-on-selected)' : 'var(--app-text-primary)'"
      />
      <span class="di__label">{{ label() }}</span>
      <span class="di__trailing"><ng-content select="[trailing]" /></span>
    </button>
  `,
  styles: [
    `
      :host {
        display: block;
        position: relative;
      }
      :host(:not(:last-child))::after {
        content: '';
        position: absolute;
        left: 18px;
        right: 18px;
        bottom: 0;
        height: 1px;
        background: color-mix(in srgb, var(--app-divider-strong) 30%, transparent);
      }
      :host(.is-active)::after {
        display: none;
      }
      .di {
        display: flex;
        align-items: center;
        gap: 10px;
        width: 100%;
        box-sizing: border-box;
        padding: 12px 18px;
        background: none;
        border: none;
        cursor: pointer;
        text-align: left;
        color: var(--app-text-primary);
        font-family: var(--font-family-base);
      }
      .di:hover {
        background: var(--app-bg-surface);
      }
      .di--active {
        background: var(--app-selected-fill);
        color: var(--app-text-on-selected);
      }
      .di__label {
        flex: 1;
        min-width: 0;
        font-size: 14px;
        font-weight: var(--font-weight-medium);
      }
      .di__trailing {
        flex-shrink: 0;
        display: inline-flex;
        align-items: center;
      }
      /* mode rail : icône seule, centrée. */
      :host(.is-collapsed) .di {
        justify-content: center;
        gap: 0;
        padding-left: 0;
        padding-right: 0;
      }
      :host(.is-collapsed) .di__label,
      :host(.is-collapsed) .di__trailing {
        display: none;
      }
    `,
  ],
})
export class DrawerItem {
  readonly icon = input('');
  readonly label = input('');
  readonly active = input(false);
  readonly collapsed = input(false);
  readonly clicked = output<void>();
}
