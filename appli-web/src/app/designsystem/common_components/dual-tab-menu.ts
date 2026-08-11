import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TabRowCustom } from '@designsystem/common_components/tab-row-custom';

/**
 * Menu à deux niveaux d'onglets — miroir de DualTabMenu.kt : une TabRowCustom haute (42px)
 * + si le top-tab courant a des sous-onglets (`subTabsMap`), un divider strong puis une
 * TabRowCustom sous-rangée (40px).
 */
@Component({
  selector: 'app-dual-tab-menu',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TabRowCustom],
  template: `
    <div class="dtm">
      <app-tab-row-custom
        [items]="topTabs()"
        [selectedIndex]="selectedTopIndex()"
        [height]="42"
        [isSubRow]="false"
        (tabSelected)="topTabSelected.emit($event)"
      />
      @if (subTabs().length) {
        <div class="dtm__divider"></div>
        <app-tab-row-custom
          [items]="subTabs()"
          [selectedIndex]="selectedSubIndex() ?? 0"
          [height]="40"
          [isSubRow]="true"
          (tabSelected)="subTabSelected.emit($event)"
        />
      }
    </div>
  `,
  styles: [
    `
      .dtm {
        width: 100%;
        background: var(--app-bg-bottom-nav);
      }
      .dtm__divider {
        width: 100%;
        height: 1.5px;
        background: var(--app-divider-strong);
      }
    `,
  ],
})
export class DualTabMenu {
  readonly topTabs = input<string[]>([]);
  readonly subTabsMap = input<Record<string, string[]>>({});
  readonly selectedTopIndex = input(0);
  readonly selectedSubIndex = input<number | null>(null);
  readonly topTabSelected = output<number>();
  readonly subTabSelected = output<number>();

  protected readonly subTabs = computed(() => this.subTabsMap()[this.topTabs()[this.selectedTopIndex()]] ?? []);
}
