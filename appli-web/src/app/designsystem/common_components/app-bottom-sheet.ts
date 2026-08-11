import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/**
 * Shell générique de bottom sheet — miroir de AppBottomSheet.kt (ModalBottomSheet M3) :
 * scrim + feuille ancrée en bas (fond bgScreen, coins haut arrondis, drag handle), slot de contenu.
 * Visible quand `open` ; clic sur le scrim => dismissRequest.
 */
@Component({
  selector: 'app-bottom-sheet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (open()) {
      <div class="abs__scrim" (click)="dismissRequest.emit()">
        <div
          class="abs__sheet"
          [style.background]="containerColor()"
          [style.max-width]="maxWidth()"
          (click)="$event.stopPropagation()"
        >
          <div class="abs__handle"></div>
          <ng-content />
        </div>
      </div>
    }
  `,
  styles: [
    `
      .abs__scrim {
        position: fixed;
        inset: 0;
        z-index: 100;
        background: rgba(0, 0, 0, 0.5);
        display: flex;
        align-items: flex-end;
        justify-content: center;
        /* le voile foncé arrive progressivement (background-color, pas l'opacité globale
           sinon la feuille fonderait aussi) */
        animation: abs-scrim-in 220ms ease;
      }
      .abs__sheet {
        width: 100%;
        max-height: 90vh;
        overflow-y: auto;
        border-radius: 16px 16px 0 0;
        padding-bottom: var(--space-3);
        /* la feuille glisse depuis le bas */
        animation: abs-sheet-in 280ms cubic-bezier(0.32, 0.72, 0, 1);
      }
      .abs__handle {
        width: 32px;
        height: 4px;
        border-radius: 2px;
        background: color-mix(in srgb, var(--app-text-primary) 30%, transparent);
        margin: var(--space-3) auto;
      }
      @keyframes abs-scrim-in {
        from {
          background: rgba(0, 0, 0, 0);
        }
        to {
          background: rgba(0, 0, 0, 0.5);
        }
      }
      @keyframes abs-sheet-in {
        from {
          transform: translateY(100%);
        }
        to {
          transform: translateY(0);
        }
      }
    `,
  ],
})
export class AppBottomSheet {
  readonly open = input(false);
  readonly containerColor = input('var(--app-bg-screen)');
  /** Largeur max de la feuille (défaut 640px) ; à augmenter pour les sheets riches (ex. picker). */
  readonly maxWidth = input('640px');
  readonly dismissRequest = output<void>();
}
