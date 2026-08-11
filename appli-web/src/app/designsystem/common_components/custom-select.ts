import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';

/**
 * Select custom — miroir de CustomSelect.kt : bouton (fond colorMap[selected] ?? backgroundColor)
 * + chevron, menu déroulant collé bord à bord (option sélectionnée = bordée + gras). Label
 * optionnel intégré dans la zone cliquable (style M3 filled : petit, au-dessus de la valeur —
 * le bouton passe alors à 56px).
 */
@Component({
  selector: 'app-custom-select',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <div class="cs">
      <button class="cs__trigger" [style.background]="currentBackground()" [style.height.px]="effectiveHeight()" (click)="expanded.set(!expanded())">
        <span class="cs__col">
          @if (label()) {
            <span class="cs__label">{{ label() }}</span>
          }
          <span class="cs__value" [style.color]="textColor()" [style.font-size.px]="textSize()">{{ selected() }}</span>
        </span>
        <app-icon
          [name]="expanded() ? 'keyboard_arrow_up' : 'keyboard_arrow_down'"
          [size]="20"
          [color]="expanded() ? 'var(--app-primary-action)' : 'var(--app-text-tertiary)'"
        />
      </button>
      @if (expanded()) {
        <div class="cs__backdrop" (click)="expanded.set(false)"></div>
        <div class="cs__menu" [class.cs__menu--up]="menuUp()" [style.background]="menuBackgroundColor()">
          @for (opt of options(); track opt) {
            <button
              class="cs__option"
              [class.cs__option--selected]="opt === selected()"
              [style.border-color]="opt === selected() ? textColor() : 'transparent'"
              [style.color]="opt === selected() ? textColor() : 'var(--app-text-primary)'"
              [style.font-size.px]="textSize()"
              (click)="choose(opt)"
            >
              {{ opt }}
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
      .cs {
        position: relative;
      }
      .cs__trigger {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-2);
        width: 100%;
        box-sizing: border-box;
        border: none;
        border-radius: var(--radius-md);
        padding: 0 var(--space-3);
        cursor: pointer;
        font-family: var(--font-family-base);
        appearance: none;
        -webkit-appearance: none;
      }
      .cs__col {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 6px;
        min-width: 0;
        text-align: left;
      }
      .cs__label {
        font-size: var(--font-size-caption);
        color: var(--app-text-tertiary);
      }
      .cs__value {
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 100%;
      }
      .cs__backdrop {
        position: fixed;
        inset: 0;
        z-index: 10;
      }
      .cs__menu {
        position: absolute;
        top: 100%;
        left: 0;
        right: 0;
        z-index: 11;
        border-radius: var(--radius-md);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
        overflow: hidden;
        box-sizing: border-box;
      }
      /* Ouverture vers le haut (ex. barre de pagination collée en bas). */
      .cs__menu--up {
        top: auto;
        bottom: 100%;
      }
      .cs__option {
        display: block;
        width: 100%;
        box-sizing: border-box;
        text-align: left;
        background: transparent;
        border: 1px solid transparent;
        border-radius: var(--radius-md);
        padding: 10px var(--space-3);
        cursor: pointer;
        font-family: var(--font-family-base);
        appearance: none;
        -webkit-appearance: none;
      }
      .cs__option--selected {
        font-weight: var(--font-weight-bold);
      }
      .cs__option:hover {
        background: color-mix(in srgb, var(--app-text-primary) 6%, transparent);
      }
    `,
  ],
})
export class CustomSelect {
  readonly selected = input('');
  readonly options = input<string[]>([]);
  readonly label = input('');
  readonly colorMap = input<Record<string, string>>({});
  readonly textSize = input(14);
  /** Hauteur du trigger (px). Défaut 48 (formulaires) ; barre de pagination passe 40 (compact). */
  readonly triggerHeight = input(48);
  readonly backgroundColor = input('var(--app-bg-recessed)');
  readonly textColor = input('var(--app-primary-action)');
  readonly menuBackgroundColor = input('var(--app-bg-recessed)');
  /** Ouvre le menu vers le haut (utile quand le select est en bas de l'écran). */
  readonly menuUp = input(false);
  readonly select = output<string>();

  protected readonly expanded = signal(false);
  protected readonly currentBackground = computed(
    () => this.colorMap()[this.selected()] ?? this.backgroundColor(),
  );
  /** Avec label intégré, le trigger passe à 56px (M3 filled) ; sinon triggerHeight. */
  protected readonly effectiveHeight = computed(() =>
    this.label() ? Math.max(this.triggerHeight(), 56) : this.triggerHeight(),
  );

  protected choose(opt: string): void {
    this.select.emit(opt);
    this.expanded.set(false);
  }
}
