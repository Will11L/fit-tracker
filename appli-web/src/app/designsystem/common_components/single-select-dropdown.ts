import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';

/**
 * Dropdown sélection unique — miroir de SingleSelectDropdown.kt : TextField M3 *filled* avec
 * label flottant intégré dans la zone cliquable (petit, en haut du champ ; centré à taille
 * normale quand vide), valeur primaryAction + chevron, soulignement primaryAction quand le
 * menu est ouvert. Menu collé bord à bord au champ, ✓ sur l'option sélectionnée et options
 * désactivées (suffixe + couleur tertiary, non cliquables).
 */
@Component({
  selector: 'app-single-select-dropdown',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <div class="ssd">
      <button
        class="ssd__field"
        [class.ssd__field--open]="expanded()"
        [class.ssd__field--rest]="!selected()"
        (click)="expanded.set(!expanded())"
      >
        <span class="ssd__col">
          @if (label()) {
            <span class="ssd__label">{{ label() }}</span>
          }
          <span class="ssd__value">{{ selected() }}</span>
        </span>
        <app-icon
          [name]="expanded() ? 'keyboard_arrow_up' : 'keyboard_arrow_down'"
          [size]="20"
          [color]="expanded() ? 'var(--app-primary-action)' : 'var(--app-text-tertiary)'"
        />
      </button>
      @if (expanded()) {
        <div class="ssd__backdrop" (click)="expanded.set(false)"></div>
        <div class="ssd__menu">
          @for (opt of options(); track opt) {
            <button
              class="ssd__option"
              [disabled]="isDisabled(opt)"
              (click)="choose(opt)"
            >
              <span class="ssd__opt-text" [style.color]="optionColor(opt)">
                {{ isDisabled(opt) ? opt + disabledSuffix() : opt }}
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
      .ssd {
        position: relative;
      }
      .ssd__field {
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
      .ssd__field--open {
        border-bottom-color: var(--app-primary-action);
      }
      .ssd__col {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 6px;
        min-width: 0;
        text-align: left;
      }
      .ssd__label {
        font-size: var(--font-size-caption);
        color: var(--app-text-tertiary);
        transition: font-size 0.15s ease;
      }
      /* État au repos sans valeur : label centré à taille normale (M3 filled). */
      .ssd__field--rest .ssd__label {
        font-size: 14px;
      }
      .ssd__value {
        color: var(--app-primary-action);
        font-size: 14px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 100%;
      }
      .ssd__backdrop {
        position: fixed;
        inset: 0;
        z-index: 10;
      }
      .ssd__menu {
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
      .ssd__option {
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
      .ssd__option:disabled {
        cursor: default;
      }
      .ssd__option:not(:disabled):hover {
        background: color-mix(in srgb, var(--app-text-primary) 6%, transparent);
      }
      .ssd__opt-text {
        font-size: 14px;
      }
    `,
  ],
})
export class SingleSelectDropdown {
  readonly label = input('');
  readonly selected = input('');
  readonly options = input<string[]>([]);
  readonly disabledOptions = input<string[]>([]);
  readonly disabledSuffix = input(' (current)');
  readonly select = output<string>();

  protected readonly expanded = signal(false);

  protected isDisabled(opt: string): boolean {
    return this.disabledOptions().includes(opt);
  }

  protected optionColor(opt: string): string {
    if (this.isDisabled(opt)) return 'var(--app-text-tertiary)';
    if (opt === this.selected()) return 'var(--app-primary-action)';
    return 'var(--app-text-primary)';
  }

  protected choose(opt: string): void {
    if (this.isDisabled(opt)) return;
    this.select.emit(opt);
    this.expanded.set(false);
  }
}
