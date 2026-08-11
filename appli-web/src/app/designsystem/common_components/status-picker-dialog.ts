import { ChangeDetectionStrategy, Component, effect, input, output, signal } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';
import { FormDialog } from '@designsystem/common_components/form-dialog';

/** Une option de StatusPickerDialog : code wire + label + icône + couleur. */
export interface StatusOption {
  value: string;
  label: string;
  icon: string;
  color: string;
}

/**
 * Dialog de choix de statut — miroir de StatusPickerDialog.kt : bâti sur FormDialog, chaque option
 * est une ligne sélectionnable (fond teinté par sa couleur si sélectionnée). Émet le `value` choisi.
 */
@Component({
  selector: 'app-status-picker-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon, FormDialog],
  template: `
    <app-form-dialog
      [open]="open()"
      [title]="title()"
      [confirmText]="confirmText()"
      (confirm)="confirm.emit(selectedValue())"
      (dismiss)="dismiss.emit()"
    >
      @for (o of options(); track o.value) {
        <button
          type="button"
          class="spd__row"
          [style.background]="o.value === selectedValue() ? mix(o.color) : 'var(--app-bg-recessed)'"
          (click)="selectedValue.set(o.value)"
        >
          <span [style.color]="o.value === selectedValue() ? o.color : 'var(--app-text-primary)'">{{ o.label }}</span>
          <app-icon [name]="o.icon" [size]="24" [color]="o.color" />
        </button>
      }
    </app-form-dialog>
  `,
  styles: [
    `
      .spd__row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        width: 100%;
        box-sizing: border-box;
        border: none;
        border-radius: var(--radius-md);
        padding: 10px var(--space-3);
        cursor: pointer;
        font-family: var(--font-family-base);
        font-size: var(--font-size-body);
        appearance: none;
        -webkit-appearance: none;
      }
    `,
  ],
})
export class StatusPickerDialog {
  readonly open = input(false);
  readonly title = input('');
  readonly options = input<StatusOption[]>([]);
  readonly selected = input('');
  readonly confirmText = input('Mettre à jour');
  readonly confirm = output<string>();
  readonly dismiss = output<void>();

  protected readonly selectedValue = signal('');

  constructor() {
    effect(() => this.selectedValue.set(this.selected()));
  }

  protected mix(color: string): string {
    return `color-mix(in srgb, ${color} 12%, transparent)`;
  }
}
