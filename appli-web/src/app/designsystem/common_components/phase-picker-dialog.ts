import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/**
 * Dialog de choix de phase — miroir de PhasePickerDialog.kt : titre + 3 boutons pleine largeur
 * (WARMUP / TRAINING / POST_TRAINING). Émet le code wire UPPER_CASE. Visible quand `open`.
 * (Labels FR en dur ; codes wire conformes politique 11.)
 */
@Component({
  selector: 'app-phase-picker-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (open()) {
      <div class="dlg__scrim" (click)="dismiss.emit()">
        <div class="dlg__card" (click)="$event.stopPropagation()">
          <h3 class="dlg__title">{{ title() }}</h3>
          <div class="ppd__list">
            @for (p of phases; track p.value) {
              <button type="button" class="ppd__btn" (click)="phaseSelected.emit(p.value)">{{ p.label }}</button>
            }
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
        background: var(--app-bg-screen);
      }
      .dlg__title {
        margin: 0 0 var(--space-4);
        font-size: var(--font-size-subtitle);
        font-weight: 600;
        color: var(--app-text-primary);
      }
      .ppd__list {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      .ppd__btn {
        width: 100%;
        height: 44px;
        border: none;
        border-radius: var(--radius-md);
        background: var(--app-bg-recessed);
        color: var(--app-text-primary);
        font-family: var(--font-family-base);
        font-size: 14px;
        cursor: pointer;
        appearance: none;
        -webkit-appearance: none;
      }
      .ppd__btn:hover {
        background: color-mix(in srgb, var(--app-text-primary) 6%, var(--app-bg-recessed));
      }
    `,
  ],
})
export class PhasePickerDialog {
  readonly open = input(false);
  readonly title = input("Phase de l'exercice");
  readonly phaseSelected = output<string>();
  readonly dismiss = output<void>();

  protected readonly phases = [
    { value: 'WARMUP', label: 'Échauffement' },
    { value: 'TRAINING', label: 'Entraînement' },
    { value: 'POST_TRAINING', label: 'Post-entraînement' },
  ];
}
