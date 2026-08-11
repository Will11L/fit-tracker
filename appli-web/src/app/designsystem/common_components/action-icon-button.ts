import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { AppIcon } from '../icons/app-icon';

/**
 * Bouton icône seule — pendant web de ActionIconButton.kt : zone tap carrée, icône centrée, coins
 * `shapes.small` (= radius-md), fond bgButton optionnel. Baseline web **compacte** : 34px / icône
 * 20px (volontairement plus petit que l'Android 40/24, jugé trop massif côté web). Surcharger
 * `size`/`iconSize` pour les cas particuliers (ex. boutons denses 30/18).
 *
 * Hauteur pilotable par la variable CSS `--aib-button-height` (héritée d'un ancêtre, ex.
 * `EntityRowTrailing` pour des boutons de row pleine hauteur) ; à défaut, hauteur = `size`
 * (carré par défaut, comportement inchangé pour tous les autres usages).
 */
@Component({
  selector: 'app-action-icon-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <button
      type="button"
      class="aib"
      [class.aib--bg]="hasBackground()"
      [style.width.px]="size()"
      [style.height]="heightStyle()"
      [style.background]="hasBackground() ? backgroundColor() : 'transparent'"
      [disabled]="disabled()"
      (click)="clicked.emit()"
    >
      <app-icon [name]="icon()" [size]="iconSize()" [color]="tint()" />
    </button>
  `,
  styles: [
    `
      .aib {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        padding: 0;
        border: none;
        border-radius: var(--radius-md);
        background: transparent;
        cursor: pointer;
        transition: filter 0.15s ease;
      }
      .aib--bg:hover:not(:disabled) {
        filter: brightness(1.1);
      }
      .aib:disabled {
        opacity: 0.4;
        cursor: default;
      }
    `,
  ],
})
export class ActionIconButton {
  /** Clé d'icône dans APP_ICONS. */
  readonly icon = input('');
  readonly disabled = input(false);
  readonly hasBackground = input(true);
  readonly backgroundColor = input('var(--app-bg-button)');
  readonly tint = input('var(--app-text-primary)');
  /** Taille de la zone tap. Défaut web compact 34px (Android = 40px). */
  readonly size = input(34);
  /** Taille de l'icône. Défaut web compact 20px (Android = 24px). */
  readonly iconSize = input(20);
  readonly clicked = output<void>();

  /** Hauteur effective : `--aib-button-height` si un ancêtre la pose, sinon `size` (carré). */
  protected readonly heightStyle = computed(() => `var(--aib-button-height, ${this.size()}px)`);
}
