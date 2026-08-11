import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';

/**
 * Indicateur icône + compteur d'un DrawerItem — miroir de DrawerIconCountIndicator.kt / Figma :
 * petite icône (16px) + nombre (12px SemiBold), tous deux teintés (primaryAction par défaut).
 * Ex. « ✉ 3 » en trailing de l'item Notifications. Masqué si count ≤ 0.
 */
@Component({
  selector: 'app-drawer-icon-count-indicator',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    @if (count() > 0) {
      <span class="dci">
        <app-icon [name]="icon()" [size]="16" [color]="color()" />
        <span class="dci__n" [style.color]="color()">{{ count() }}</span>
      </span>
    }
  `,
  styles: [
    `
      .dci {
        display: inline-flex;
        align-items: center;
        gap: 6px;
      }
      .dci__n {
        font-size: 12px;
        font-weight: 600;
      }
    `,
  ],
})
export class DrawerIconCountIndicator {
  readonly icon = input('notifications');
  readonly count = input(0);
  readonly color = input('var(--app-primary-action)');
}
