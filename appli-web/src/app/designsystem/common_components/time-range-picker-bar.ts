import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';

/**
 * Barre de plage horaire — miroir de TimeRangePickerBar.kt : label + bouton ◀ + double-slider
 * (start/end, snap au pas) avec bulles HH:MM au-dessus des thumbs + bouton ▶. Émet (start, end).
 *
 * Déviation : RangeSlider M3 → 2 `<input type=range>` superposés (track + segment actif en divs).
 * Limite connue : quand les 2 thumbs se superposent, en saisir un peut être délicat.
 */
@Component({
  selector: 'app-time-range-picker-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ActionIconButton],
  template: `
    <div class="trp">
      <span class="trp__label">{{ label() }}</span>
      <div class="trp__row">
        <app-action-icon-button icon="chevron_left" backgroundColor="var(--app-bg-recessed)" (clicked)="decStart()" />

        <div class="trp__slider">
          <span class="trp__bubble" [style.left.%]="startPct()">{{ fmt(safeStart()) }}</span>
          <span class="trp__bubble" [style.left.%]="endPct()">{{ fmt(safeEnd()) }}</span>
          <div class="trp__track"></div>
          <div class="trp__active" [style.left.%]="startPct()" [style.right.%]="100 - endPct()"></div>
          <input
            type="range"
            class="trp__input"
            [min]="minMinutes()"
            [max]="maxMinutes()"
            [step]="stepMinutes()"
            [value]="safeStart()"
            (input)="onStart($any($event.target).value)"
          />
          <input
            type="range"
            class="trp__input"
            [min]="minMinutes()"
            [max]="maxMinutes()"
            [step]="stepMinutes()"
            [value]="safeEnd()"
            (input)="onEnd($any($event.target).value)"
          />
        </div>

        <app-action-icon-button icon="chevron_right" backgroundColor="var(--app-bg-recessed)" (clicked)="incEnd()" />
      </div>
    </div>
  `,
  styles: [
    `
      .trp {
        display: flex;
        flex-direction: column;
        gap: 10px;
        width: 100%;
      }
      .trp__label {
        color: var(--app-primary-action);
        font-size: var(--font-size-body);
      }
      .trp__row {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      .trp__slider {
        position: relative;
        flex: 1;
        height: 44px;
      }
      .trp__track {
        position: absolute;
        top: 28px;
        left: 0;
        right: 0;
        height: 4px;
        border-radius: 2px;
        background: var(--app-bg-recessed);
      }
      .trp__active {
        position: absolute;
        top: 28px;
        height: 4px;
        border-radius: 2px;
        background: var(--app-primary-action);
      }
      .trp__input {
        position: absolute;
        top: 18px;
        left: 0;
        width: 100%;
        height: 24px;
        margin: 0;
        background: transparent;
        pointer-events: none;
        -webkit-appearance: none;
        appearance: none;
      }
      .trp__input::-webkit-slider-runnable-track {
        background: transparent;
      }
      .trp__input::-moz-range-track {
        background: transparent;
      }
      .trp__input::-webkit-slider-thumb {
        -webkit-appearance: none;
        pointer-events: auto;
        width: 16px;
        height: 16px;
        border-radius: 50%;
        background: var(--app-primary-action);
        border: 2px solid var(--app-bg-screen);
        cursor: pointer;
      }
      .trp__input::-moz-range-thumb {
        pointer-events: auto;
        width: 16px;
        height: 16px;
        border-radius: 50%;
        background: var(--app-primary-action);
        border: 2px solid var(--app-bg-screen);
        cursor: pointer;
      }
      .trp__bubble {
        position: absolute;
        top: 0;
        transform: translateX(-50%);
        color: var(--app-primary-action);
        font-size: var(--font-size-caption);
        white-space: nowrap;
      }
    `,
  ],
})
export class TimeRangePickerBar {
  readonly minMinutes = input(0);
  readonly maxMinutes = input(1439);
  readonly stepMinutes = input(5);
  readonly startMinutes = input(0);
  readonly endMinutes = input(60);
  readonly label = input('Time');
  readonly rangeChange = output<{ start: number; end: number }>();

  protected readonly safeStart = computed(() =>
    Math.min(Math.max(this.startMinutes(), this.minMinutes()), this.maxMinutes()),
  );
  protected readonly safeEnd = computed(() =>
    Math.max(Math.min(this.endMinutes(), this.maxMinutes()), this.safeStart()),
  );
  protected readonly startPct = computed(() => this.pct(this.safeStart()));
  protected readonly endPct = computed(() => this.pct(this.safeEnd()));

  private pct(m: number): number {
    return ((m - this.minMinutes()) / (this.maxMinutes() - this.minMinutes())) * 100;
  }

  private snap(x: number): number {
    const clamped = Math.min(Math.max(x, this.minMinutes()), this.maxMinutes());
    const k = Math.round((clamped - this.minMinutes()) / this.stepMinutes());
    return Math.min(Math.max(this.minMinutes() + k * this.stepMinutes(), this.minMinutes()), this.maxMinutes());
  }

  protected fmt(m: number): string {
    const hh = Math.floor(m / 60).toString().padStart(2, '0');
    const mm = (m % 60).toString().padStart(2, '0');
    return `${hh}:${mm}`;
  }

  protected onStart(raw: string): void {
    const s = this.snap(Number(raw));
    this.rangeChange.emit({ start: Math.min(s, this.safeEnd()), end: this.safeEnd() });
  }

  protected onEnd(raw: string): void {
    const e = this.snap(Number(raw));
    this.rangeChange.emit({ start: this.safeStart(), end: Math.max(e, this.safeStart()) });
  }

  protected decStart(): void {
    const s = this.snap(this.safeStart() - this.stepMinutes());
    this.rangeChange.emit({ start: s, end: Math.max(this.safeEnd(), s) });
  }

  protected incEnd(): void {
    const e = this.snap(this.safeEnd() + this.stepMinutes());
    this.rangeChange.emit({ start: Math.min(this.safeStart(), e), end: e });
  }
}
