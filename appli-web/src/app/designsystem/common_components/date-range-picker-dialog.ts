import { ChangeDetectionStrategy, Component, effect, input, output, signal } from '@angular/core';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { DialogPrimaryButton, DialogSecondaryButton } from '@designsystem/common_components/dialog-buttons';
import { CalendarMonthGrid } from '@designsystem/common_components/calendar-month-grid';

const FR_MONTHS = [
  'Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
  'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre',
];
const FR_WEEKDAYS = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];

/**
 * Sélecteur de plage de dates — deux calendriers côte à côte (Début | Fin), thémé app (mêmes
 * cellules que CustomDatePickerDialog, grille via CalendarMonthGrid). Chaque calendrier a sa propre
 * navigation mois ; les jours entre les deux bornes sont surlignés. `confirm` émet `{ start, end }`
 * en ISO « YYYY-MM-DD » (réordonné si l'utilisateur a inversé). Empilé en colonne sur écran étroit.
 */
@Component({
  selector: 'app-date-range-picker-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ActionIconButton, DialogPrimaryButton, DialogSecondaryButton, CalendarMonthGrid],
  template: `
    @if (open()) {
      <div class="dlg__scrim" (click)="dismiss.emit()">
        <div class="dlg__card" (click)="$event.stopPropagation()">
          <h3 class="dlg__title">{{ title() }}</h3>

          <div class="ranges">
            <!-- Calendrier de début. -->
            <div class="cal">
              <span class="cal__caption">Début</span>
              <div class="cal__header">
                <app-action-icon-button icon="chevron_left" backgroundColor="var(--c-first-blue)" (clicked)="nav('start', -1)" />
                <span class="cal__monthlabel">{{ label(sYear(), sMonth()) }}</span>
                <app-action-icon-button icon="chevron_right" backgroundColor="var(--c-first-blue)" (clicked)="nav('start', 1)" />
              </div>
              <div class="cal__weekdays">
                @for (w of weekdays; track w) {
                  <span class="cal__wd">{{ w }}</span>
                }
              </div>
              <app-calendar-month-grid [year]="sYear()" [month]="sMonth()" [firstDayOffset]="offset(sYear(), sMonth())">
                <ng-template let-iso>
                  <button
                    class="cal__day"
                    [class.cal__day--sel]="iso === sIso() || iso === eIso()"
                    [class.cal__day--inrange]="inRange(iso)"
                    [class.cal__day--today]="iso === todayIso && iso !== sIso() && iso !== eIso()"
                    (click)="sIso.set(iso)"
                  >
                    {{ dayOf(iso) }}
                  </button>
                </ng-template>
              </app-calendar-month-grid>
            </div>

            <!-- Calendrier de fin. -->
            <div class="cal">
              <span class="cal__caption">Fin</span>
              <div class="cal__header">
                <app-action-icon-button icon="chevron_left" backgroundColor="var(--c-first-blue)" (clicked)="nav('end', -1)" />
                <span class="cal__monthlabel">{{ label(eYear(), eMonth()) }}</span>
                <app-action-icon-button icon="chevron_right" backgroundColor="var(--c-first-blue)" (clicked)="nav('end', 1)" />
              </div>
              <div class="cal__weekdays">
                @for (w of weekdays; track w) {
                  <span class="cal__wd">{{ w }}</span>
                }
              </div>
              <app-calendar-month-grid [year]="eYear()" [month]="eMonth()" [firstDayOffset]="offset(eYear(), eMonth())">
                <ng-template let-iso>
                  <button
                    class="cal__day"
                    [class.cal__day--sel]="iso === sIso() || iso === eIso()"
                    [class.cal__day--inrange]="inRange(iso)"
                    [class.cal__day--today]="iso === todayIso && iso !== sIso() && iso !== eIso()"
                    (click)="eIso.set(iso)"
                  >
                    {{ dayOf(iso) }}
                  </button>
                </ng-template>
              </app-calendar-month-grid>
            </div>
          </div>

          <div class="dlg__actions">
            <app-dialog-secondary-button (clicked)="dismiss.emit()">Annuler</app-dialog-secondary-button>
            <app-dialog-primary-button (clicked)="emitConfirm()">Enregistrer</app-dialog-primary-button>
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
        max-width: 680px;
        max-height: calc(100vh - 2 * var(--space-4));
        overflow-y: auto;
        box-sizing: border-box;
        border-radius: 16px;
        padding: var(--space-5);
        background: var(--app-bg-screen);
      }
      .dlg__title {
        margin: 0 0 var(--space-3);
        font-size: var(--font-size-subtitle);
        font-weight: 600;
        color: var(--app-primary-action);
      }
      /* Deux calendriers côte à côte ; empilés en colonne quand la largeur manque. */
      .ranges {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
        gap: var(--space-4);
        margin-bottom: var(--space-5);
      }
      .cal {
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-3) var(--space-2);
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
      }
      .cal__caption {
        text-align: center;
        color: var(--app-accent-text);
        font-size: var(--font-size-caption);
        font-weight: var(--font-weight-medium);
      }
      .cal__header {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }
      .cal__monthlabel {
        display: inline-flex;
        align-items: center;
        height: 40px;
        background: var(--c-first-blue);
        border-radius: var(--radius-md);
        padding: 0 var(--space-3);
        color: var(--app-text-tertiary);
        font-size: var(--font-size-body);
        font-weight: var(--font-weight-medium);
        text-transform: capitalize;
      }
      .cal__weekdays {
        display: grid;
        grid-template-columns: repeat(7, 1fr);
      }
      .cal__wd {
        text-align: center;
        font-size: var(--font-size-caption);
        color: var(--c-light-gray-blue);
      }
      .cal__day {
        aspect-ratio: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        background: transparent;
        border: none;
        border-radius: 50%;
        cursor: pointer;
        font-family: var(--font-family-base);
        font-size: var(--font-size-body);
        color: var(--app-text-tertiary);
      }
      /* Jours strictement entre les deux bornes : surlignage léger (la plage est lisible d'un calendrier à l'autre). */
      .cal__day--inrange {
        background: color-mix(in srgb, var(--app-primary-action) 20%, transparent);
        border-radius: 8px;
      }
      .cal__day--sel {
        background: var(--app-primary-action);
        color: var(--app-text-primary);
        font-weight: var(--font-weight-bold);
        border-radius: 50%;
      }
      .cal__day--today {
        border: 1.5px solid var(--app-primary-action);
        border-radius: var(--radius-md);
      }
      .dlg__actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--space-3);
      }
    `,
  ],
})
export class DateRangePickerDialog {
  readonly open = input(false);
  readonly title = input('Sélectionner une période');
  readonly initialStart = input<string | null>(null);
  readonly initialEnd = input<string | null>(null);
  readonly confirm = output<{ start: string; end: string }>();
  readonly dismiss = output<void>();

  protected readonly weekdays = FR_WEEKDAYS;
  protected readonly todayIso = this.toIso(new Date());

  protected readonly sYear = signal(new Date().getFullYear());
  protected readonly sMonth = signal(new Date().getMonth());
  protected readonly sIso = signal('');
  protected readonly eYear = signal(new Date().getFullYear());
  protected readonly eMonth = signal(new Date().getMonth());
  protected readonly eIso = signal('');

  private wasOpen = false;

  constructor() {
    // À l'ouverture : positionner les 2 calendriers + sélections depuis initialStart/initialEnd.
    effect(() => {
      const isOpen = this.open();
      if (isOpen && !this.wasOpen) {
        const s = this.parse(this.initialStart());
        const e = this.parse(this.initialEnd());
        this.sYear.set(s.getFullYear());
        this.sMonth.set(s.getMonth());
        this.sIso.set(this.toIso(s));
        this.eYear.set(e.getFullYear());
        this.eMonth.set(e.getMonth());
        this.eIso.set(this.toIso(e));
      }
      this.wasOpen = isOpen;
    });
  }

  protected label(year: number, month: number): string {
    return `${FR_MONTHS[month]} ${year}`;
  }

  /** Décalage des cellules vides en tête (Lun=0 … Dim=6). */
  protected offset(year: number, month: number): number {
    return (new Date(year, month, 1).getDay() + 6) % 7;
  }

  protected dayOf(iso: string): string {
    return String(Number(iso.slice(8, 10)));
  }

  /** Jour strictement compris entre les deux bornes sélectionnées (ordre indifférent). */
  protected inRange(iso: string): boolean {
    const a = this.sIso();
    const b = this.eIso();
    if (!a || !b) return false;
    const lo = a < b ? a : b;
    const hi = a < b ? b : a;
    return iso > lo && iso < hi;
  }

  protected nav(which: 'start' | 'end', delta: number): void {
    const ySig = which === 'start' ? this.sYear : this.eYear;
    const mSig = which === 'start' ? this.sMonth : this.eMonth;
    let m = mSig() + delta;
    let y = ySig();
    if (m < 0) {
      m = 11;
      y--;
    } else if (m > 11) {
      m = 0;
      y++;
    }
    mSig.set(m);
    ySig.set(y);
  }

  protected emitConfirm(): void {
    let start = this.sIso();
    let end = this.eIso();
    if (end < start) [start, end] = [end, start];
    this.confirm.emit({ start, end });
  }

  private parse(iso: string | null): Date {
    if (iso) {
      const d = new Date(iso + 'T00:00:00');
      if (!isNaN(d.getTime())) return d;
    }
    return new Date();
  }

  private toIso(d: Date): string {
    const pad = (n: number) => n.toString().padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }
}
