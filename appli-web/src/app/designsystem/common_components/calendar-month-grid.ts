import { ChangeDetectionStrategy, Component, computed, contentChild, input, TemplateRef } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';

/**
 * Grille mensuelle 7 colonnes — miroir de CalendarMonthGrid.kt : cellules vides en tête
 * (`firstDayOffset`) puis 1 cellule par jour. La cellule concrète est fournie par le consommateur
 * via un `<ng-template let-iso>` (équivalent du slot `dayCell(date)` Compose ; l'ISO du jour est
 * passé en `$implicit`). La taille des cellules vient du grid CSS (1fr), pas d'un calcul.
 */
@Component({
  selector: 'app-calendar-month-grid',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet],
  template: `
    <div class="cmg">
      @for (iso of cells(); track $index) {
        @if (iso) {
          <ng-container [ngTemplateOutlet]="dayCell()" [ngTemplateOutletContext]="{ $implicit: iso }" />
        } @else {
          <span class="cmg__blank"></span>
        }
      }
    </div>
  `,
  styles: [
    `
      .cmg {
        display: grid;
        grid-template-columns: repeat(7, 1fr);
        gap: 6px;
        width: 100%;
      }
      .cmg__blank {
        aspect-ratio: 1;
      }
    `,
  ],
})
export class CalendarMonthGrid {
  readonly year = input.required<number>();
  /** Mois 0-11. */
  readonly month = input.required<number>();
  readonly firstDayOffset = input(0);
  readonly dayCell = contentChild.required<TemplateRef<{ $implicit: string }>>(TemplateRef);

  protected readonly cells = computed<(string | null)[]>(() => {
    const y = this.year();
    const m = this.month();
    const daysInMonth = new Date(y, m + 1, 0).getDate();
    const out: (string | null)[] = [];
    for (let i = 0; i < this.firstDayOffset(); i++) out.push(null);
    for (let d = 1; d <= daysInMonth; d++) out.push(`${y}-${this.pad(m + 1)}-${this.pad(d)}`);
    return out;
  });

  private pad(n: number): string {
    return n.toString().padStart(2, '0');
  }
}
