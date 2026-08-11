import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/**
 * Rangée d'onglets — miroir de TabRowCustom.kt : Row pleine largeur (fond bgBottomNav,
 * SpaceEvenly), chaque onglet en flex:1, hauteur paramétrable. Fond/texte dépendent de
 * sélectionné + `isSubRow` (sous-rangée = teintes atténuées).
 */
@Component({
  selector: 'app-tab-row-custom',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="trc">
      @for (label of items(); track $index; let i = $index) {
        <button class="trc__tab" [style.height.px]="height()" [style.background]="tabBg(i)" (click)="tabSelected.emit(i)">
          <span class="trc__label" [class.trc__label--selected]="i === selectedIndex()" [style.color]="tabColor(i)">{{ label }}</span>
        </button>
      }
    </div>
  `,
  styles: [
    `
      .trc {
        display: flex;
        width: 100%;
        background: var(--app-bg-bottom-nav);
      }
      .trc__tab {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        border: none;
        padding: 0;
        cursor: pointer;
        font-family: var(--font-family-base);
        appearance: none;
        -webkit-appearance: none;
      }
      .trc__label {
        font-size: var(--font-size-body);
      }
      .trc__label--selected {
        font-weight: 600;
      }
    `,
  ],
})
export class TabRowCustom {
  readonly items = input<string[]>([]);
  readonly selectedIndex = input(0);
  readonly height = input(44);
  readonly isSubRow = input(false);
  readonly tabSelected = output<number>();

  protected tabBg(i: number): string {
    const selected = i === this.selectedIndex();
    const sub = this.isSubRow();
    if (selected && !sub) return 'var(--app-selected-fill)';
    if (selected && sub) return 'color-mix(in srgb, var(--app-selected-fill) 75%, transparent)';
    if (!selected && !sub) return 'var(--app-bg-bottom-nav)';
    return 'color-mix(in srgb, var(--app-bg-bottom-nav) 50%, transparent)';
  }

  protected tabColor(i: number): string {
    if (i === this.selectedIndex()) return 'var(--app-text-on-selected)';
    return this.isSubRow()
      ? 'color-mix(in srgb, var(--app-text-tertiary) 80%, transparent)'
      : 'var(--app-text-tertiary)';
  }
}
