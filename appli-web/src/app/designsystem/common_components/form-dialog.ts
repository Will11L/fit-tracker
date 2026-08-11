import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { DialogPrimaryButton, DialogSecondaryButton } from '@designsystem/common_components/dialog-buttons';

/**
 * Dialog de formulaire — miroir de FormDialog.kt (AlertDialog M3) : titre + zone de champs
 * (slot, colonne espacée 12px, scrollable optionnel) + confirm (désactivable) / dismiss.
 * Visible quand `open`. Les champs sont projetés via `<ng-content>`.
 * `disabledReason` (miroir de DialogValidationReason.kt) : message d'aide affiché sous le
 * contenu quand le bouton confirm est désactivé — hauteur réservée 1 ligne (pas de saut du
 * dialog), glissement vertical + fondu à l'apparition. Rendu seulement si fourni au callsite.
 */
@Component({
  selector: 'app-form-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DialogPrimaryButton, DialogSecondaryButton],
  template: `
    @if (open()) {
      <div class="dlg__scrim" (click)="dismiss.emit()">
        <div class="dlg__card" (click)="$event.stopPropagation()">
          <h3 class="dlg__title">{{ title() }}</h3>
          <div class="dlg__content" [class.dlg__content--scroll]="scrollable()">
            <ng-content />
          </div>
          @if (disabledReason()) {
            <p class="dlg__reason" [class.dlg__reason--visible]="!confirmEnabled()">
              {{ disabledReason() }}
            </p>
          }
          <div class="dlg__actions">
            <app-dialog-secondary-button (clicked)="dismiss.emit()">{{ dismissText() }}</app-dialog-secondary-button>
            <app-dialog-primary-button [disabled]="!confirmEnabled()" (clicked)="confirm.emit()">{{ confirmText() }}</app-dialog-primary-button>
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
        max-width: 360px;
        box-sizing: border-box;
        border-radius: 16px;
        padding: var(--space-5);
        background: var(--app-bg-screen);
      }
      .dlg__title {
        margin: 0 0 var(--space-4);
        font-size: var(--font-size-subtitle);
        font-weight: 600;
        color: var(--app-primary-action);
      }
      .dlg__content {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        margin-bottom: var(--space-5);
      }
      .dlg__content--scroll {
        max-height: 50vh;
        overflow-y: auto;
      }
      /* Miroir DialogValidationReason : 1 ligne réservée, fondu + glissement (translateY). */
      .dlg__reason {
        margin: calc(-1 * var(--space-3)) 0 var(--space-4);
        font-size: 12px;
        line-height: 1.3;
        color: var(--app-snackbar-error);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        opacity: 0;
        transform: translateY(10px);
        transition:
          opacity 200ms linear,
          transform 200ms linear;
      }
      .dlg__reason--visible {
        opacity: 1;
        transform: translateY(0);
      }
      .dlg__actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--space-3);
      }
    `,
  ],
})
export class FormDialog {
  readonly open = input(false);
  readonly title = input('');
  readonly confirmText = input('');
  readonly confirmEnabled = input(true);
  readonly disabledReason = input('');
  readonly dismissText = input('Annuler');
  readonly scrollable = input(false);
  readonly confirm = output<void>();
  readonly dismiss = output<void>();
}
