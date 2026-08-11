import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { AppBottomSheet } from '@designsystem/common_components/app-bottom-sheet';
import { OptionRow } from '@designsystem/common_components/option-row';
import { TitledDivider } from '@designsystem/common_components/titled-divider';

/** Une action de OptionsBottomSheet (= SheetAction.kt). `color` = fond du bouton de la row. */
export interface SheetAction {
  label: string;
  icon: string;
  color: string;
}

/**
 * Bottom sheet d'actions — miroir de OptionsBottomSheet.kt : AppBottomSheet contenant un
 * TitledDivider(titre) + une OptionRow par action. Émet `actionSelected` (le label) au clic.
 */
@Component({
  selector: 'app-options-bottom-sheet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppBottomSheet, OptionRow, TitledDivider],
  template: `
    <app-bottom-sheet [open]="open()" (dismissRequest)="dismissRequest.emit()">
      <div class="obs">
        <app-titled-divider [title]="title()" />
        @for (action of actions(); track action.label) {
          <app-option-row
            [label]="action.label"
            [icon]="action.icon"
            [backgroundColor]="action.color"
            (clicked)="actionSelected.emit(action.label)"
          />
        }
      </div>
    </app-bottom-sheet>
  `,
  styles: [
    `
      .obs {
        display: flex;
        flex-direction: column;
        gap: 10px;
        padding: 0 var(--space-4) var(--space-3);
      }
    `,
  ],
})
export class OptionsBottomSheet {
  readonly open = input(false);
  readonly title = input('');
  readonly actions = input<SheetAction[]>([]);
  readonly dismissRequest = output<void>();
  readonly actionSelected = output<string>();
}
