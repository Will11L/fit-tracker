import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';

/** Un item de BottomNavBar : valeur (clé), icône (ligature), label (a11y). */
export interface BottomNavItemData {
  value: string;
  icon: string;
  label: string;
  /** Teinte d'icône au repos (non sélectionné) — sert à colorer le bouton bascule de mode. */
  iconColor?: string;
  /** Fond de la pill quand CET item est actif (prime sur `accentColor` du bar) — ex. couleur de
   *  section du hub Santé. Icône blanche par-dessus (`accentText`). */
  activeColor?: string;
}

/**
 * Barre de navigation basse — miroir de BottomNavBar.kt / Figma O1, adaptée web : barre flottante
 * qui **hugge son contenu** (pas pleine largeur) sur fond bg-bottom-nav. Items icône seule ;
 * l'item sélectionné porte une pill (selected-fill) + icône blanche un peu plus grande, les autres
 * en textTertiary. Émet `select` (la valeur). Le positionnement (fixed en bas, centré) est laissé
 * à l'appelant — le composant n'impose que sa largeur = contenu.
 * (Le cluster de statut Android sur l'item « Menu » n'est pas repris : composant générique.)
 */
@Component({
  selector: 'app-bottom-nav-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <nav class="bnb">
      @for (item of items(); track item.value) {
        <button
          type="button"
          class="bnb__item"
          [class.bnb__item--active]="item.value === selected()"
          [style.background]="
            item.value === selected() ? (item.activeColor ?? accentColor()) : null
          "
          [attr.aria-label]="item.label"
          [attr.aria-current]="item.value === selected() ? 'page' : null"
          (click)="select.emit(item.value)"
        >
          <app-icon
            [name]="item.icon"
            [size]="item.value === selected() ? 26 : 22"
            [color]="
              item.value === selected()
                ? accentText()
                : (item.iconColor ?? 'var(--app-text-tertiary)')
            "
          />
        </button>
      }
    </nav>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
      }
      .bnb {
        display: inline-flex;
        align-items: center;
        gap: var(--space-1);
        box-sizing: border-box;
        background: var(--app-bg-bottom-nav);
        border-radius: 16px;
        padding: 6px 8px;
        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.35);
      }
      .bnb__item {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 48px;
        height: 44px;
        flex-shrink: 0;
        border: none;
        background: transparent;
        border-radius: 10px;
        cursor: pointer;
        padding: 0;
      }
      .bnb__item--active {
        background: var(--app-selected-fill);
      }
    `,
  ],
})
export class BottomNavBar {
  readonly items = input<BottomNavItemData[]>([]);
  readonly selected = input('');
  /** Fond de la pill active (accent de domaine) ; défaut = teinte de sélection standard. */
  readonly accentColor = input('var(--app-selected-fill)');
  /** Couleur de l'icône active, à choisir pour contraster avec `accentColor`. */
  readonly accentText = input('var(--app-text-on-selected)');
  readonly select = output<string>();
}
