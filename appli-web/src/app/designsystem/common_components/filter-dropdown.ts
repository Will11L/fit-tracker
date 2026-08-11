import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';

/**
 * Dropdown de filtre — miroir de FilterDropdown.kt : TextField M3 *filled* avec label flottant
 * intégré dans la zone cliquable (textPrimary ; petit en haut du champ, centré à taille normale
 * quand vide), valeur primaryAction (vide si null) + chevron, soulignement primaryAction quand
 * le menu est ouvert. Menu collé bord à bord au champ, ✓ sur l'option sélectionnée. Sélection
 * unique, ferme au choix.
 */
@Component({
  selector: 'app-filter-dropdown',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <div class="fd">
      <button
        class="fd__field"
        [class.fd__field--open]="expanded()"
        [class.fd__field--rest]="!selected()"
        [class.fd__field--raised]="raised()"
        (click)="expanded.set(!expanded())"
      >
        <span class="fd__col">
          @if (label()) {
            <span class="fd__label">{{ label() }}</span>
          }
          <span class="fd__value">{{ selected() ?? '' }}</span>
        </span>
        <app-icon
          [name]="expanded() ? 'keyboard_arrow_up' : 'keyboard_arrow_down'"
          [size]="20"
          [color]="expanded() ? 'var(--app-primary-action)' : 'var(--app-text-tertiary)'"
        />
      </button>
      @if (expanded()) {
        <div class="fd__backdrop" (click)="expanded.set(false)"></div>
        <div class="fd__menu">
          @for (opt of options(); track opt) {
            <button class="fd__option" (click)="choose(opt)">
              <span class="fd__opt-text" [style.color]="opt === selected() ? 'var(--app-primary-action)' : 'var(--app-text-primary)'">
                {{ opt }}
              </span>
              @if (opt === selected()) {
                <app-icon name="check" [size]="20" color="var(--app-primary-action)" />
              }
            </button>
          }
        </div>
      }
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .fd {
        position: relative;
      }
      .fd__field {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-2);
        width: 100%;
        height: 56px;
        box-sizing: border-box;
        background: var(--app-bg-recessed);
        border: none;
        border-bottom: 2px solid transparent;
        border-radius: var(--radius-md);
        padding: 0 var(--space-3);
        cursor: pointer;
        font-family: var(--font-family-base);
        appearance: none;
        -webkit-appearance: none;
      }
      /* Variante « surélevée » (opt-in) : sur un cadre de même teinte (panneau de filtres thirdBlue),
         fond second-blue pour distinguer le champ. Défaut (false) = rendu filled inchangé. */
      .fd__field--raised {
        background: var(--c-second-blue);
      }
      .fd__field--open {
        border-bottom-color: var(--app-primary-action);
      }
      .fd__col {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 6px;
        min-width: 0;
        text-align: left;
      }
      .fd__label {
        font-size: var(--font-size-caption);
        color: var(--app-text-primary);
        transition: font-size 0.15s ease;
      }
      /* État au repos sans valeur : label centré à taille normale (M3 filled). */
      .fd__field--rest .fd__label {
        font-size: 14px;
      }
      .fd__value {
        color: var(--app-primary-action);
        font-size: 14px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 100%;
      }
      .fd__backdrop {
        position: fixed;
        inset: 0;
        z-index: 10;
      }
      .fd__menu {
        position: absolute;
        top: 100%;
        left: 0;
        right: 0;
        z-index: 11;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
        padding: var(--space-1);
        box-sizing: border-box;
      }
      .fd__option {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-2);
        width: 100%;
        box-sizing: border-box;
        background: transparent;
        border: none;
        border-radius: var(--radius-md);
        padding: 10px var(--space-3);
        cursor: pointer;
        font-family: var(--font-family-base);
        appearance: none;
        -webkit-appearance: none;
      }
      .fd__option:hover {
        background: color-mix(in srgb, var(--app-text-primary) 6%, transparent);
      }
      .fd__opt-text {
        font-size: 14px;
      }
    `,
  ],
})
export class FilterDropdown {
  readonly label = input('');
  readonly options = input<string[]>([]);
  readonly selected = input<string | null>(null);
  /** Variante « surélevée » : fond second-blue (pour ressortir sur un cadre de même teinte, ex. panneau de filtres). */
  readonly raised = input(false);
  readonly select = output<string>();

  protected readonly expanded = signal(false);

  protected choose(opt: string): void {
    this.select.emit(opt);
    this.expanded.set(false);
  }
}
