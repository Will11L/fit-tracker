import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { DialogPrimaryButton, DialogSecondaryButton } from '@designsystem/common_components/dialog-buttons';

const FR_MONTHS = [
  'Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
  'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre',
];
const FR_WEEKDAYS = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];

/**
 * DatePicker custom thémé app — miroir de CustomDatePickerDialog.kt : calendrier maison
 * (header prev/mois-année cliquable/next ; grille mois 7 cols avec aujourd'hui bordé +
 * sélection pleine ; mode année grille 4 cols). Format wire ISO "YYYY-MM-DD". Visible quand `open`.
 * (Labels mois/jours i18n Android → FR en dur ici ; Transloco plus tard.)
 */
@Component({
  selector: 'app-custom-date-picker-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ActionIconButton, DialogPrimaryButton, DialogSecondaryButton],
  template: `
    @if (open()) {
      <div class="dlg__scrim" (click)="dismiss.emit()">
        <div class="dlg__card" (click)="$event.stopPropagation()">
          <h3 class="dlg__title">{{ title() }}</h3>

          <div class="dp">
            <div class="dp__header">
              <app-action-icon-button icon="chevron_left" backgroundColor="var(--c-first-blue)" (clicked)="prev()" />
              <button class="dp__monthlabel" (click)="yearMode.set(!yearMode())">{{ monthLabel() }}</button>
              <app-action-icon-button icon="chevron_right" backgroundColor="var(--c-first-blue)" (clicked)="next()" />
            </div>

            @if (yearMode()) {
              <div class="dp__years">
                @for (y of years(); track y) {
                  <button class="dp__year" [class.dp__year--sel]="y === viewYear()" (click)="pickYear(y)">{{ y }}</button>
                }
              </div>
            } @else {
              <div class="dp__weekdays">
                @for (w of weekdays; track w) {
                  <span class="dp__wd">{{ w }}</span>
                }
              </div>
              <div class="dp__grid">
                @for (iso of cells(); track $index) {
                  @if (iso) {
                    <button
                      class="dp__day"
                      [class.dp__day--sel]="iso === selectedIso()"
                      [class.dp__day--today]="iso === todayIso && iso !== selectedIso()"
                      (click)="selectedIso.set(iso)"
                    >
                      {{ dayOf(iso) }}
                    </button>
                  } @else {
                    <span class="dp__day"></span>
                  }
                }
              </div>
            }
          </div>

          <div class="dlg__actions">
            <app-dialog-secondary-button (clicked)="dismiss.emit()">Annuler</app-dialog-secondary-button>
            <app-dialog-primary-button (clicked)="confirm.emit(selectedIso())">Enregistrer</app-dialog-primary-button>
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
        margin: 0 0 var(--space-3);
        font-size: var(--font-size-subtitle);
        font-weight: 600;
        color: var(--app-primary-action);
      }
      .dp {
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-3) var(--space-2);
        margin-bottom: var(--space-5);
      }
      .dp__header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: var(--space-4);
      }
      /* Même style que le sélecteur de mois des calendriers (.cal__month) : fond first-blue, h40. */
      .dp__monthlabel {
        display: inline-flex;
        align-items: center;
        height: 40px;
        background: var(--c-first-blue);
        border: none;
        border-radius: var(--radius-md);
        padding: 0 var(--space-3);
        color: var(--app-text-tertiary);
        font-family: var(--font-family-base);
        font-size: var(--font-size-subtitle);
        font-weight: var(--font-weight-medium);
        text-transform: capitalize;
        cursor: pointer;
      }
      .dp__weekdays {
        display: grid;
        grid-template-columns: repeat(7, 1fr);
        margin-bottom: var(--space-3);
      }
      .dp__wd {
        text-align: center;
        font-size: var(--font-size-body);
        color: var(--c-light-gray-blue);
      }
      .dp__grid {
        display: grid;
        grid-template-columns: repeat(7, 1fr);
        gap: 6px 0;
      }
      .dp__day {
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
      .dp__day--sel {
        background: var(--app-primary-action);
        color: var(--app-text-primary);
        font-weight: var(--font-weight-bold);
      }
      .dp__day--today {
        border: 1.5px solid var(--app-primary-action);
        border-radius: var(--radius-md);
      }
      .dp__years {
        display: grid;
        grid-template-columns: repeat(4, 1fr);
        gap: var(--space-2);
        max-height: 280px;
        overflow-y: auto;
      }
      .dp__year {
        height: 40px;
        background: var(--app-bg-recessed);
        border: none;
        border-radius: var(--radius-md);
        cursor: pointer;
        color: var(--app-text-tertiary);
        font-family: var(--font-family-base);
        font-size: var(--font-size-body);
      }
      .dp__year--sel {
        background: var(--app-primary-action);
        color: var(--app-text-primary);
      }
      .dlg__actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--space-3);
      }
    `,
  ],
})
export class CustomDatePickerDialog {
  readonly open = input(false);
  readonly initialIso = input<string | null>(null);
  readonly title = input('Date');
  readonly minYear = input(1900);
  readonly maxYear = input(new Date().getFullYear());
  readonly confirm = output<string>();
  readonly dismiss = output<void>();

  protected readonly viewYear = signal(new Date().getFullYear());
  protected readonly viewMonth = signal(new Date().getMonth());
  protected readonly selectedIso = signal('');
  protected readonly yearMode = signal(false);
  protected readonly weekdays = FR_WEEKDAYS;
  protected readonly todayIso = this.toIso(new Date());

  private wasOpen = false;

  constructor() {
    effect(() => {
      const isOpen = this.open();
      if (isOpen && !this.wasOpen) {
        const d = this.parseInitial();
        this.viewYear.set(d.getFullYear());
        this.viewMonth.set(d.getMonth());
        this.selectedIso.set(this.toIso(d));
        this.yearMode.set(false);
      }
      this.wasOpen = isOpen;
    });
  }

  protected readonly monthLabel = computed(() => `${FR_MONTHS[this.viewMonth()]} ${this.viewYear()}`);

  protected readonly years = computed(() => {
    const out: number[] = [];
    for (let y = this.maxYear(); y >= this.minYear(); y--) out.push(y);
    return out;
  });

  protected readonly cells = computed<(string | null)[]>(() => {
    const y = this.viewYear();
    const m = this.viewMonth();
    const firstWeekday = ((new Date(y, m, 1).getDay() + 6) % 7) + 1; // Lun=1..Dim=7
    const daysInMonth = new Date(y, m + 1, 0).getDate();
    const out: (string | null)[] = [];
    for (let i = 0; i < firstWeekday - 1; i++) out.push(null);
    for (let d = 1; d <= daysInMonth; d++) out.push(`${y}-${this.pad(m + 1)}-${this.pad(d)}`);
    return out;
  });

  protected dayOf(iso: string): string {
    return String(Number(iso.slice(8, 10)));
  }

  protected prev(): void {
    if (this.yearMode()) {
      this.yearMode.set(false);
      return;
    }
    let m = this.viewMonth() - 1;
    let y = this.viewYear();
    if (m < 0) {
      m = 11;
      y--;
    }
    this.viewMonth.set(m);
    this.viewYear.set(y);
  }

  protected next(): void {
    if (this.yearMode()) {
      this.yearMode.set(false);
      return;
    }
    let m = this.viewMonth() + 1;
    let y = this.viewYear();
    if (m > 11) {
      m = 0;
      y++;
    }
    this.viewMonth.set(m);
    this.viewYear.set(y);
  }

  protected pickYear(y: number): void {
    this.viewYear.set(y);
    this.yearMode.set(false);
  }

  private parseInitial(): Date {
    const iso = this.initialIso();
    if (iso) {
      const d = new Date(iso + 'T00:00:00');
      if (!isNaN(d.getTime())) return d;
    }
    return new Date();
  }

  private pad(n: number): string {
    return n.toString().padStart(2, '0');
  }

  private toIso(d: Date): string {
    return `${d.getFullYear()}-${this.pad(d.getMonth() + 1)}-${this.pad(d.getDate())}`;
  }
}
