import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';

/** Divider avec titre centré entre deux lignes — miroir de TitledDivider.kt (couleur param, semibold).
 *  `icon` optionnel : une icône (clé APP_ICONS) collée après le titre, dans la couleur du divider. */
@Component({
  selector: 'app-titled-divider',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <div class="td">
      <span class="td__line" [style.background]="color()"></span>
      <span class="td__title" [style.color]="color()" [style.font-size.px]="size()">
        {{ title() }}
        @if (icon()) {
          <app-icon [name]="icon()" [size]="15" [color]="color()" />
        }
      </span>
      <span class="td__line" [style.background]="color()"></span>
    </div>
  `,
  styles: [
    `
      .td {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        padding: 6px 0;
      }
      .td__line {
        flex: 1;
        height: 1px;
      }
      .td__title {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        font-weight: 600;
        font-size: var(--font-size-body);
      }
    `,
  ],
})
export class TitledDivider {
  readonly title = input('');
  /** Couleur des lignes + du titre (défaut = couleur de divider standard). */
  readonly color = input('var(--app-divider)');
  /** Clé d'icône APP_ICONS optionnelle, affichée après le titre (même couleur). */
  readonly icon = input('');
  /** Taille de police du titre en px (null = défaut `var(--font-size-body)`). Input additif — sert aux
   *  mini-dividers de labels de charts (12 px) sans toucher au divider standard. */
  readonly size = input<number | null>(null);
}
