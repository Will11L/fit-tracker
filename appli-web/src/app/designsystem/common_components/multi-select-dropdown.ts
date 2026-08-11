import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';

/**
 * Dropdown sélection multiple — miroir de MultiSelectDropdown.kt : TextField M3 *filled* avec
 * label flottant intégré dans la zone cliquable (petit, en haut du champ ; centré à taille
 * normale quand vide), valeurs jointes par ", ", soulignement primaryAction quand le menu est
 * ouvert. Menu collé bord à bord au champ, chaque option se coche/décoche (✓ si sélectionnée).
 * Émet la nouvelle liste à chaque toggle (menu reste ouvert).
 */
@Component({
  selector: 'app-multi-select-dropdown',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <div class="msd">
      <button
        class="msd__field"
        [class.msd__field--open]="expanded()"
        [class.msd__field--rest]="selectedItems().length === 0"
        (click)="expanded.set(!expanded())"
      >
        <span class="msd__col">
          @if (label()) {
            <span class="msd__label">{{ label() }}</span>
          }
          <span class="msd__value">{{ displayText() }}</span>
        </span>
        <app-icon
          [name]="expanded() ? 'keyboard_arrow_up' : 'keyboard_arrow_down'"
          [size]="20"
          [color]="expanded() ? 'var(--app-primary-action)' : 'var(--app-text-tertiary)'"
        />
      </button>
      @if (expanded()) {
        <div class="msd__backdrop" (click)="expanded.set(false)"></div>
        <div class="msd__menu">
          @for (opt of options(); track opt) {
            <button class="msd__option" (click)="toggle(opt)">
              <span class="msd__opt-text" [style.color]="isSelected(opt) ? 'var(--app-primary-action)' : 'var(--app-text-primary)'">
                {{ opt }}
              </span>
              @if (isSelected(opt)) {
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
      .msd {
        position: relative;
      }
      .msd__field {
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
        overflow: hidden;
      }
      .msd__field--open {
        border-bottom-color: var(--app-primary-action);
      }
      .msd__col {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 6px;
        min-width: 0;
        text-align: left;
      }
      .msd__label {
        font-size: var(--font-size-caption);
        color: var(--app-text-tertiary);
        transition: font-size 0.15s ease;
      }
      /* État au repos sans valeur : label centré à taille normale (M3 filled). */
      .msd__field--rest .msd__label {
        font-size: 14px;
      }
      .msd__value {
        color: var(--app-primary-action);
        font-size: 14px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 100%;
      }
      .msd__backdrop {
        position: fixed;
        inset: 0;
        z-index: 10;
      }
      .msd__menu {
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
      .msd__option {
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
      .msd__option:hover {
        background: color-mix(in srgb, var(--app-text-primary) 6%, transparent);
      }
      .msd__opt-text {
        font-size: 14px;
      }
    `,
  ],
})
export class MultiSelectDropdown {
  readonly label = input('');
  readonly options = input<string[]>([]);
  readonly selectedItems = input<string[]>([]);
  readonly selectionChange = output<string[]>();

  protected readonly expanded = signal(false);

  protected displayText(): string {
    return this.selectedItems().join(', ');
  }

  protected isSelected(opt: string): boolean {
    return this.selectedItems().includes(opt);
  }

  protected toggle(opt: string): void {
    const current = this.selectedItems();
    const next = current.includes(opt) ? current.filter((o) => o !== opt) : [...current, opt];
    this.selectionChange.emit(next);
  }
}
