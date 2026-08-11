import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';

/** Case à cocher — miroir de CustomCheckbox.kt : checked = fond mediumGreen + check blanc, unchecked = outline. */
@Component({
  selector: 'app-custom-checkbox',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <button
      type="button"
      role="checkbox"
      class="cb"
      [class.cb--checked]="checked()"
      [attr.aria-checked]="checked()"
      [disabled]="disabled()"
      (click)="checked.set(!checked())"
    >
      @if (checked()) {
        <app-icon name="check" [size]="20" color="var(--c-medium-green)" />
      }
    </button>
  `,
  styles: [
    `
      .cb {
        width: 22px;
        height: 22px;
        border-radius: var(--radius-sm);
        border: 2px solid var(--app-divider);
        background: transparent;
        cursor: pointer;
        padding: 0;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        vertical-align: middle;
        appearance: none;
        -webkit-appearance: none;
        transition: background-color 0.15s ease, border-color 0.15s ease;
      }
      .cb--checked {
        background: transparent;
        border-color: var(--c-medium-green);
      }
      .cb:disabled {
        opacity: 0.5;
        cursor: default;
      }
    `,
  ],
})
export class CustomCheckbox {
  readonly checked = model(false);
  readonly disabled = input(false);
}
