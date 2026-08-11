import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';

/**
 * Bouton-segment d'un toggle — pendant web de SegmentedIconButton.kt : icône dans une boîte
 * **carrée** bordée (côté = `width`, défaut 34px ; coins `--radius-md` comme ActionIconButton),
 * sélectionné = fond primaryAction + bordure primaryAction + icône textPrimary ; sinon transparent
 * + bordure atténuée + icône lightGrayBlue. (Android : 30px de haut / coins 6px — le web adopte un
 * carré au format ActionIconButton, jugé plus net que le rectangle « couché ».)
 */
@Component({
  selector: 'app-segmented-icon-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <button
      class="sib"
      [class.sib--selected]="selected()"
      [style.width.px]="width()"
      [style.height.px]="width()"
      [style.border-color]="selected() ? 'var(--app-primary-action)' : unselectedBorderColor()"
      [attr.aria-label]="description()"
      [attr.aria-pressed]="selected()"
      (click)="clicked.emit()"
    >
      <app-icon
        [name]="icon()"
        [size]="iconSize()"
        [color]="selected() ? 'var(--app-text-primary)' : 'var(--c-light-gray-blue)'"
      />
    </button>
  `,
  styles: [
    `
      .sib {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        box-sizing: border-box;
        border-radius: var(--radius-md);
        border: 1px solid transparent;
        background: transparent;
        padding: 0;
        cursor: pointer;
        appearance: none;
        -webkit-appearance: none;
      }
      .sib--selected {
        background: var(--app-primary-action);
      }
    `,
  ],
})
export class SegmentedIconButton {
  readonly selected = input(false);
  readonly icon = input('');
  readonly description = input('');
  /** Côté du carré (largeur = hauteur). Défaut 34px (footprint ActionIconButton). */
  readonly width = input(34);
  readonly iconSize = input(18);
  readonly unselectedBorderColor = input('color-mix(in srgb, var(--app-text-secondary) 60%, transparent)');
  readonly clicked = output<void>();
}
