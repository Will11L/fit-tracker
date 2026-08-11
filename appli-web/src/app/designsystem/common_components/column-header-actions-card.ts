import { ChangeDetectionStrategy, Component, input, model, output } from '@angular/core';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { StyledSearchField } from '@designsystem/common_components/styled-search-field';
import { TitledDivider } from '@designsystem/common_components/titled-divider';

/** Sens de tri (= SortDir.kt côté Android). */
export type SortDir = 'ASC' | 'DESC' | 'NONE';

/**
 * Popup d'actions sur un header de colonne data-grid — miroir de ColumnHeaderActionsCard.kt :
 * carte (260px, fond bgScreen) = TitledDivider(nom colonne) + 3 ActionIconButton (↑ASC ↓DESC ↺clear)
 * + StyledSearchField de filtre.
 */
@Component({
  selector: 'app-column-header-actions-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ActionIconButton, StyledSearchField, TitledDivider],
  template: `
    <div class="chac">
      <app-titled-divider [title]="columnName()" />

      <div class="chac__actions">
        <app-action-icon-button
          icon="arrow_upward"
          [backgroundColor]="sortDir() === 'ASC' ? 'var(--app-primary-action)' : 'var(--app-bg-recessed)'"
          [tint]="sortDir() === 'ASC' ? 'var(--app-text-primary)' : 'var(--app-text-secondary)'"
          (clicked)="setSort.emit('ASC')"
        />
        <app-action-icon-button
          icon="arrow_downward"
          [backgroundColor]="sortDir() === 'DESC' ? 'var(--app-primary-action)' : 'var(--app-bg-recessed)'"
          [tint]="sortDir() === 'DESC' ? 'var(--app-text-primary)' : 'var(--app-text-secondary)'"
          (clicked)="setSort.emit('DESC')"
        />
        <app-action-icon-button
          icon="refresh"
          backgroundColor="var(--app-bg-recessed)"
          [tint]="sortDir() !== 'NONE' ? 'var(--app-text-primary)' : 'var(--app-text-tertiary)'"
          (clicked)="setSort.emit('NONE')"
        />
      </div>

      <app-styled-search-field
        [value]="filterValue()"
        (valueChange)="filterValue.set($event)"
        [placeholderText]="filterPlaceholder()"
      />
    </div>
  `,
  styles: [
    `
      .chac {
        width: 260px;
        box-sizing: border-box;
        border-radius: var(--radius-md);
        background: var(--app-bg-screen);
        padding: var(--space-3);
      }
      .chac__actions {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        margin: 10px 0;
      }
    `,
  ],
})
export class ColumnHeaderActionsCard {
  readonly columnName = input('');
  readonly sortDir = input<SortDir>('NONE');
  readonly filterValue = model('');
  readonly filterPlaceholder = input('Filtrer…');
  readonly setSort = output<SortDir>();
}
