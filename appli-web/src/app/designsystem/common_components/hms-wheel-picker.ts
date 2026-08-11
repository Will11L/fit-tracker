import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { WheelPicker } from '@designsystem/common_components/wheel-picker';

/**
 * Sélecteur H:M:S — miroir de HmsWheelPicker.kt : aperçu "HH:MM:SS" (accentText) + 3 WheelPicker
 * (heures 0-23, minutes/secondes 0-59) séparées par ":" + indice de swipe.
 * (Labels i18n Android → libellés FR en dur ici ; Transloco viendra plus tard.)
 */
@Component({
  selector: 'app-hms-wheel-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WheelPicker],
  template: `
    <div class="hms">
      <div class="hms__preview">{{ pad2(hours()) }}:{{ pad2(minutes()) }}:{{ pad2(seconds()) }}</div>

      <div class="hms__row">
        <div class="hms__col">
          <span class="hms__label">Heures</span>
          <app-wheel-picker [min]="0" [max]="23" [selected]="hours()" (selectedChange)="hoursChange.emit($event)" />
        </div>
        <span class="hms__sep">:</span>
        <div class="hms__col">
          <span class="hms__label">Minutes</span>
          <app-wheel-picker [min]="0" [max]="59" [selected]="minutes()" (selectedChange)="minutesChange.emit($event)" />
        </div>
        <span class="hms__sep">:</span>
        <div class="hms__col">
          <span class="hms__label">Secondes</span>
          <app-wheel-picker [min]="0" [max]="59" [selected]="seconds()" (selectedChange)="secondsChange.emit($event)" />
        </div>
      </div>

      <p class="hms__hint">Glissez pour ajuster</p>
    </div>
  `,
  styles: [
    `
      .hms {
        display: flex;
        flex-direction: column;
        gap: var(--space-4);
        width: 100%;
      }
      .hms__preview {
        display: flex;
        align-items: center;
        justify-content: center;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: 14px 0;
        color: var(--app-accent-text);
        font-size: var(--font-size-title);
        font-weight: 600;
      }
      .hms__row {
        display: flex;
        align-items: flex-end;
        gap: var(--space-3);
      }
      .hms__col {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        align-items: stretch;
        gap: var(--space-2);
      }
      .hms__label {
        text-align: center;
        color: color-mix(in srgb, var(--app-text-primary) 70%, transparent);
        font-size: var(--font-size-caption);
      }
      .hms__sep {
        display: flex;
        align-items: center;
        /* hauteur du wheel (WheelPicker: itemHeight 40 × visibleItems 5) -> ':' centré sur la bande */
        height: 200px;
        color: color-mix(in srgb, var(--app-text-primary) 70%, transparent);
        font-size: var(--font-size-title);
      }
      .hms__hint {
        margin: 0;
        text-align: center;
        color: color-mix(in srgb, var(--app-text-primary) 55%, transparent);
        font-size: var(--font-size-body);
      }
    `,
  ],
})
export class HmsWheelPicker {
  readonly hours = input(0);
  readonly minutes = input(0);
  readonly seconds = input(0);
  readonly hoursChange = output<number>();
  readonly minutesChange = output<number>();
  readonly secondsChange = output<number>();

  protected pad2(v: number): string {
    return v.toString().padStart(2, '0');
  }
}
