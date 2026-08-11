import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { DialogPrimaryButton, DialogSecondaryButton } from '@designsystem/common_components/dialog-buttons';

/**
 * Dialog de confirmation — miroir de ConfirmationDialog.kt (AlertDialog M3) : scrim + card
 * (titre primaryAction + message + bouton confirm rouge + bouton secondaire). Visible quand `open`.
 */
@Component({
  selector: 'app-confirmation-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DialogPrimaryButton, DialogSecondaryButton],
  template: `
    @if (open()) {
      <div class="dlg__scrim" (click)="dismiss.emit()">
        <div class="dlg__card" [style.background]="containerColor()" (click)="$event.stopPropagation()">
          <h3 class="dlg__title" [style.color]="titleColor()">{{ title() }}</h3>
          <p class="dlg__message" [style.color]="messageColor()">{{ message() }}</p>
          <div class="dlg__actions">
            <app-dialog-secondary-button (clicked)="dismiss.emit()">{{ dismissButtonText() }}</app-dialog-secondary-button>
            <app-dialog-primary-button [color]="confirmButtonColor()" (clicked)="confirm.emit()">{{ confirmButtonText() }}</app-dialog-primary-button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [
    `
      .dlg__scrim {
        animation: app-scrim-in 200ms ease;
        position: fixed;
        inset: 0;
        z-index: 100;
        background: rgba(0, 0, 0, 0.5);
        display: flex;
        align-items: center;
        justify-content: center;
        padding: var(--space-4);
      }
      .dlg__card {
        animation: app-dialog-in 200ms cubic-bezier(0.2, 0.9, 0.3, 1);
        width: 100%;
        max-width: 340px;
        box-sizing: border-box;
        border-radius: 16px;
        padding: var(--space-5);
      }
      .dlg__title {
        margin: 0 0 var(--space-3);
        font-size: var(--font-size-subtitle);
        font-weight: 600;
      }
      .dlg__message {
        margin: 0 0 var(--space-5);
        font-size: var(--font-size-body);
        line-height: var(--line-height-body);
      }
      .dlg__actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--space-3);
      }
    `,
  ],
})
export class ConfirmationDialog {
  readonly open = input(false);
  readonly title = input('');
  readonly message = input('');
  readonly confirmButtonText = input('Delete');
  readonly dismissButtonText = input('Cancel');
  readonly confirmButtonColor = input('var(--c-red-medium)');
  readonly titleColor = input('var(--app-primary-action)');
  readonly messageColor = input('var(--app-text-primary)');
  readonly containerColor = input('var(--app-bg-screen)');
  readonly confirm = output<void>();
  readonly dismiss = output<void>();
}
