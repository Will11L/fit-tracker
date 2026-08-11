import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';

/**
 * Ligne d'option — miroir de OptionRow.kt : fond bgRecessed (radius 8), label à gauche
 * (textPrimary 14px) + ActionIconButton à droite, padding 10/12, SpaceBetween.
 */
@Component({
  selector: 'app-option-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ActionIconButton],
  template: `
    <div class="or">
      <span class="or__label">{{ label() }}</span>
      <app-action-icon-button
        [icon]="icon()"
        tint="var(--app-text-primary)"
        [hasBackground]="hasBackground()"
        [backgroundColor]="backgroundColor()"
        (clicked)="clicked.emit()"
      />
    </div>
  `,
  styles: [
    `
      .or {
        display: flex;
        align-items: center;
        justify-content: space-between;
        width: 100%;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: 10px var(--space-3);
      }
      .or__label {
        flex: 1;
        color: var(--app-text-primary);
        font-size: var(--font-size-body);
      }
    `,
  ],
})
export class OptionRow {
  readonly label = input('');
  readonly icon = input('');
  readonly hasBackground = input(true);
  readonly backgroundColor = input('var(--app-bg-button)');
  readonly clicked = output<void>();
}
