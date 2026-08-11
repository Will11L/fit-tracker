import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { AppIcon } from '../icons/app-icon';

/**
 * Icône de statut tintée — miroir de StatusIcon.kt : icône `size` (default 16px) tintée
 * par `tint`, dans une boîte aux coins `shapes.extraSmall` (= radius-sm) + padding 1px.
 * La sémantique (succès/erreur/sync…) est portée par le couple (icon, tint) du callsite
 * — ex. (check_circle, snackbar-success), (cloud_done, primary-action), (cloud_off = offline, yellow-medium).
 */
@Component({
  selector: 'app-status-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <span class="si" [style.width.px]="size()" [style.height.px]="size()">
      <app-icon [name]="icon()" [size]="size()" [color]="tint()" />
    </span>
  `,
  styles: [
    `
      .si {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        padding: 1px;
        border-radius: var(--radius-sm);
      }
    `,
  ],
})
export class StatusIcon {
  /** Clé d'icône dans APP_ICONS. */
  readonly icon = input('');
  readonly tint = input('var(--app-text-primary)');
  readonly size = input(16);
}
