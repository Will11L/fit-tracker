import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { WheelPicker } from '@designsystem/common_components/wheel-picker';

function pad2(v: number): string {
  return v.toString().padStart(2, '0');
}

/**
 * Sélecteur d'heure « HH:MM » au style de l'app — 2 roues Heures (0-23) / Minutes (0-59), façon
 * HmsWheelPicker sans les secondes. Remplace l'`<input type="time">` natif. `value` en two-way au
 * format « HH:MM » ; vide `''` = aucune heure (les roues partent alors à midi, le bouton « Effacer »
 * remet à vide). Pensé pour une heure *indicative* facultative (ex. période du journal nutrition).
 */
@Component({
  selector: 'app-custom-hour-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WheelPicker],
  template: `
    <div class="chp">
      @if (label()) {
        <span class="chp__title">{{ label() }}</span>
      }
      <div class="chp__preview" [class.chp__preview--unset]="!isSet()">
        {{ isSet() ? pad2(hours()) + ':' + pad2(minutes()) : 'Aucune heure' }}
      </div>

      <div class="chp__row">
        <div class="chp__col">
          <span class="chp__label">Heures</span>
          <app-wheel-picker [min]="0" [max]="23" [selected]="hours()" (selectedChange)="setHours($event)" />
        </div>
        <span class="chp__sep">:</span>
        <div class="chp__col">
          <span class="chp__label">Minutes</span>
          <app-wheel-picker [min]="0" [max]="59" [selected]="minutes()" (selectedChange)="setMinutes($event)" />
        </div>
      </div>

      <div class="chp__footer">
        <span class="chp__hint">Glissez pour ajuster</span>
        @if (isSet()) {
          <button type="button" class="chp__clear" (click)="value.set('')">Effacer l'heure</button>
        }
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .chp {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        width: 100%;
      }
      .chp__title {
        font-size: var(--font-size-caption);
        color: var(--app-text-tertiary);
      }
      /* Aperçu « HH:MM » (accentText) ; état vide = libellé discret « Aucune heure ». */
      .chp__preview {
        display: flex;
        align-items: center;
        justify-content: center;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: 12px 0;
        color: var(--app-accent-text);
        font-size: var(--font-size-title);
        font-weight: 600;
        font-variant-numeric: tabular-nums;
      }
      .chp__preview--unset {
        color: var(--app-text-tertiary);
        font-weight: normal;
        font-size: var(--font-size-body);
        font-style: italic;
      }
      .chp__row {
        display: flex;
        align-items: flex-end;
        justify-content: center;
        gap: var(--space-3);
      }
      .chp__col {
        flex: 1;
        min-width: 0;
        max-width: 130px;
        display: flex;
        flex-direction: column;
        align-items: stretch;
        gap: var(--space-2);
      }
      .chp__label {
        text-align: center;
        color: color-mix(in srgb, var(--app-text-primary) 70%, transparent);
        font-size: var(--font-size-caption);
      }
      .chp__sep {
        display: flex;
        align-items: center;
        /* hauteur du wheel (WheelPicker: itemHeight 40 × visibleItems 5 = 200) → ':' centré sur la bande. */
        height: 200px;
        color: color-mix(in srgb, var(--app-text-primary) 70%, transparent);
        font-size: var(--font-size-title);
      }
      .chp__footer {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: var(--space-3);
      }
      .chp__hint {
        color: color-mix(in srgb, var(--app-text-primary) 55%, transparent);
        font-size: var(--font-size-body);
      }
      .chp__clear {
        background: none;
        border: none;
        padding: 0;
        cursor: pointer;
        color: var(--app-accent-text);
        font-family: var(--font-family-base);
        font-size: var(--font-size-caption);
        text-decoration: underline;
      }
    `,
  ],
})
export class CustomHourPicker {
  /** Libellé optionnel affiché au-dessus de l'aperçu (ex. « Heure indicative »). */
  readonly label = input('');
  /** Heure « HH:MM » (two-way). Vide `''` = aucune heure (facultatif). */
  readonly value = model('');
  protected readonly pad2 = pad2;

  /** Heure/minute dérivées de `value` ; défaut d'affichage à midi (12:00) si vide/invalide. */
  private readonly parsed = computed(() => {
    const m = /^(\d{1,2}):(\d{2})$/.exec(this.value().trim());
    if (!m) return { h: 12, min: 0, set: false };
    return {
      h: Math.min(23, Math.max(0, Number(m[1]))),
      min: Math.min(59, Math.max(0, Number(m[2]))),
      set: true,
    };
  });
  protected readonly hours = computed(() => this.parsed().h);
  protected readonly minutes = computed(() => this.parsed().min);
  protected readonly isSet = computed(() => this.parsed().set);

  protected setHours(h: number): void {
    this.value.set(`${pad2(h)}:${pad2(this.minutes())}`);
  }
  protected setMinutes(min: number): void {
    this.value.set(`${pad2(this.hours())}:${pad2(min)}`);
  }
}
