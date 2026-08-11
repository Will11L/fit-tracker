import { ChangeDetectionStrategy, Component, input, model, output, signal } from '@angular/core';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { StyledSearchField } from '@designsystem/common_components/styled-search-field';

/**
 * En-tête d'écran-liste — miroir de ListSearchHeader.kt : Row [recherche (flex) + bouton sync
 * (cloud_done/cloud_off) + tri (menu ASC/DESC) + more], puis ligne « N résultats ».
 */
@Component({
  selector: 'app-list-search-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ActionIconButton, StyledSearchField],
  template: `
    <div class="lsh">
      <div class="lsh__row">
        <app-styled-search-field
          class="lsh__search"
          [value]="searchQuery()"
          (valueChange)="searchQuery.set($event)"
          [placeholderText]="searchPlaceholder()"
        />

        <app-action-icon-button
          [icon]="allSynced() ? 'cloud_done' : 'cloud_off'"
          [iconSize]="28"
          [hasBackground]="false"
          [tint]="allSynced() ? 'var(--app-primary-action)' : 'var(--c-yellow-medium)'"
          (clicked)="syncClick.emit()"
        />

        <div class="lsh__sort">
          <app-action-icon-button icon="sort" (clicked)="sortOpen.set(!sortOpen())" />
          @if (sortOpen()) {
            <div class="lsh__backdrop" (click)="sortOpen.set(false)"></div>
            <div class="lsh__menu">
              @for (opt of sortOptions(); track opt) {
                <button class="lsh__menu-item" (click)="chooseSort(opt)">{{ opt }}</button>
              }
            </div>
          }
        </div>

        <app-action-icon-button icon="more_vert" (clicked)="moreClick.emit()" />
      </div>

      <p class="lsh__count">{{ resultsCountText() }}</p>
    </div>
  `,
  styles: [
    `
      .lsh {
        width: 100%;
      }
      .lsh__row {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        width: 100%;
      }
      .lsh__search {
        flex: 1;
      }
      .lsh__sort {
        position: relative;
      }
      .lsh__backdrop {
        position: fixed;
        inset: 0;
        z-index: 10;
      }
      .lsh__menu {
        position: absolute;
        top: calc(100% + 4px);
        right: 0;
        z-index: 11;
        min-width: 160px;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
        padding: var(--space-1);
        box-sizing: border-box;
      }
      .lsh__menu-item {
        display: block;
        width: 100%;
        text-align: left;
        background: transparent;
        border: none;
        border-radius: var(--radius-md);
        padding: 10px var(--space-3);
        cursor: pointer;
        color: var(--app-text-primary);
        font-family: var(--font-family-base);
        font-size: 14px;
        appearance: none;
        -webkit-appearance: none;
      }
      .lsh__menu-item:hover {
        background: color-mix(in srgb, var(--app-text-primary) 6%, transparent);
      }
      .lsh__count {
        margin: var(--space-2) 0;
        color: var(--app-text-tertiary);
        font-size: var(--font-size-caption);
      }
    `,
  ],
})
export class ListSearchHeader {
  readonly searchQuery = model('');
  readonly searchPlaceholder = input('Search');
  readonly resultsCountText = input('');
  readonly allSynced = input(true);
  readonly sortOptions = input<string[]>(['Ascending', 'Descending']);
  readonly syncClick = output<void>();
  readonly moreClick = output<void>();
  readonly sortChange = output<string>();

  protected readonly sortOpen = signal(false);

  protected chooseSort(opt: string): void {
    this.sortChange.emit(opt);
    this.sortOpen.set(false);
  }
}
