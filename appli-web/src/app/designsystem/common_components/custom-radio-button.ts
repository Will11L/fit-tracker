import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/** RadioButton stylé app — miroir de CustomRadioButton.kt (ring blueMedium, fill primaryAction sélectionné). */
@Component({
  selector: 'app-custom-radio-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      role="radio"
      class="radio"
      [class.radio--on]="selected()"
      [attr.aria-checked]="selected()"
      [disabled]="disabled()"
      (click)="clicked.emit()"
    >
      @if (selected()) {
        <span class="radio__dot"></span>
      }
    </button>
  `,
  styles: [
    `
      .radio {
        width: 22px;
        height: 22px;
        border-radius: 50%;
        border: 2px solid var(--c-blue-medium);
        background: transparent;
        cursor: pointer;
        padding: 0;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        vertical-align: middle;
        appearance: none;
        -webkit-appearance: none;
        transition: border-color 0.15s ease;
      }
      .radio--on {
        border-color: var(--app-primary-action);
      }
      .radio__dot {
        width: 11px;
        height: 11px;
        border-radius: 50%;
        background: var(--app-primary-action);
      }
      .radio:disabled {
        opacity: 0.4;
        cursor: default;
      }
    `,
  ],
})
export class CustomRadioButton {
  readonly selected = input(false);
  readonly disabled = input(false);
  readonly clicked = output<void>();
}
