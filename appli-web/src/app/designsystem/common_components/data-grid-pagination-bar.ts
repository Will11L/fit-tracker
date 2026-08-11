import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { CustomSelect } from '@designsystem/common_components/custom-select';

/**
 * Barre de pagination data-grid — miroir de DataGridPaginationBar.kt :
 * [◀] Page X / Y [▶] · · · « A-B / Z » (texte coloré, sans fond) + CustomSelect compact (page size).
 * Prev/Next désactivés aux extrémités ; "A-B / Z" calculé en interne.
 */
@Component({
  selector: 'app-data-grid-pagination-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ActionIconButton, CustomSelect],
  template: `
    <div class="dgp">
      <app-action-icon-button
        icon="chevron_left"
        backgroundColor="var(--app-bg-button)"
        [tint]="currentPage() > 0 ? 'var(--app-primary-action)' : 'var(--app-text-tertiary)'"
        (clicked)="onPrev()"
      />
      <span class="dgp__page">Page {{ currentPage() + 1 }} / {{ totalPages() }}</span>
      <app-action-icon-button
        icon="chevron_right"
        backgroundColor="var(--app-bg-button)"
        [tint]="currentPage() < totalPages() - 1 ? 'var(--app-primary-action)' : 'var(--app-text-tertiary)'"
        (clicked)="onNext()"
      />

      <span class="dgp__spacer"></span>

      <span class="dgp__range">{{ fromIdx() }}-{{ toIdx() }} / {{ totalCount() }}</span>
      <div class="dgp__select">
        <app-custom-select
          [selected]="pageSize().toString()"
          [options]="pageSizeStrs()"
          backgroundColor="var(--app-bg-button)"
          menuBackgroundColor="var(--app-bg-button)"
          textColor="var(--app-text-primary)"
          [textSize]="12"
          [triggerHeight]="40"
          (select)="pageSizeChange.emit(+$event)"
        />
      </div>
    </div>
  `,
  styles: [
    `
      .dgp {
        display: flex;
        align-items: center;
        gap: 10px;
        width: 100%;
        box-sizing: border-box;
        background: var(--app-bg-recessed);
        padding: 6px var(--space-3);
        border-radius: var(--radius-md);
      }
      .dgp__page {
        margin: 0 4px;
        color: var(--app-text-primary);
        font-size: var(--font-size-caption);
        font-weight: var(--font-weight-medium);
        white-space: nowrap;
      }
      .dgp__spacer {
        flex: 1;
      }
      .dgp__range {
        color: var(--app-primary-action);
        font-size: var(--font-size-caption);
        font-weight: var(--font-weight-medium);
        white-space: nowrap;
      }
      .dgp__select {
        width: 80px;
      }
    `,
  ],
})
export class DataGridPaginationBar {
  readonly totalCount = input(0);
  readonly pageSize = input(50);
  readonly currentPage = input(0);
  readonly pageSizeOptions = input<number[]>([25, 50, 100]);
  readonly prev = output<void>();
  readonly next = output<void>();
  readonly pageSizeChange = output<number>();

  protected readonly totalPages = computed(() =>
    this.totalCount() === 0 ? 1 : Math.ceil(this.totalCount() / this.pageSize()),
  );
  protected readonly fromIdx = computed(() =>
    this.totalCount() === 0 ? 0 : this.currentPage() * this.pageSize() + 1,
  );
  protected readonly toIdx = computed(() =>
    Math.min((this.currentPage() + 1) * this.pageSize(), this.totalCount()),
  );
  protected readonly pageSizeStrs = computed(() => this.pageSizeOptions().map((n) => n.toString()));

  protected onPrev(): void {
    if (this.currentPage() > 0) this.prev.emit();
  }
  protected onNext(): void {
    if (this.currentPage() < this.totalPages() - 1) this.next.emit();
  }
}
