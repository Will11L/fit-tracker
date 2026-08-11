import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

/** Interrupteur — miroir de CustomSwitch (track + thumb, on = primaryAction). */
@Component({
  selector: 'app-custom-switch',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      role="switch"
      class="sw"
      [class.sw--on]="checked()"
      [attr.aria-checked]="checked()"
      [disabled]="disabled()"
      (click)="checked.set(!checked())"
    >
      <span class="sw__thumb"></span>
    </button>
  `,
  styles: [
    `
      .sw {
        position: relative;
        box-sizing: border-box;
        width: 44px;
        height: 24px;
        border-radius: var(--radius-pill);
        border: 1.5px solid var(--c-blue-medium);
        background-color: var(--app-bg-recessed);
        cursor: pointer;
        padding: 0;
        display: inline-block;
        transition: background-color 0.15s ease, border-color 0.15s ease;
      }
      .sw--on {
        background-color: var(--app-primary-action);
        border-color: var(--app-primary-action);
      }
      .sw:disabled {
        opacity: 0.5;
        cursor: default;
      }
      .sw__thumb {
        position: absolute;
        top: 50%;
        left: 6px;
        transform: translateY(-50%);
        width: 12px;
        height: 12px;
        border-radius: 50%;
        background: #ffffff;
        transition: left 0.15s ease, width 0.15s ease, height 0.15s ease;
      }
      .sw--on .sw__thumb {
        left: 23px;
        width: 18px;
        height: 18px;
      }
    `,
  ],
})
export class CustomSwitch {
  readonly checked = model(false);
  readonly disabled = input(false);
}
