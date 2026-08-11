import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { SummaryItem } from '@designsystem/common_components/summary-item';

/** Donnée d'une cellule de SummaryRow (= SummaryItemData.kt). */
export interface SummaryItemData {
  icon: string;
  value: string;
  label: string;
  iconTint: string;
}

/**
 * Rangée de cellules de résumé — miroir de SummaryRow.kt : pleine largeur, gap 8px.
 * Standard : chaque SummaryItem en flex:1 (poids égal). `compact` passe la rangée en variante
 * compacte ET fait hugger chaque cellule à son contenu (icône + texte) — cf. Figma M4 (HUG).
 * `spread` (avec compact) redonne 1/N de la largeur à chaque cellule (parts égales, contenu
 * centré par la cellule compacte) au lieu du hug.
 */
@Component({
  selector: 'app-summary-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SummaryItem],
  template: `
    <div class="sr" [class.sr--compact]="compact()" [class.sr--spread]="spread()">
      @for (item of items(); track $index) {
        <app-summary-item
          class="sr__item"
          [icon]="item.icon"
          [value]="item.value"
          [label]="item.label"
          [iconTint]="item.iconTint"
          [compact]="compact()"
          [background]="tileBackground()"
        />
      }
    </div>
  `,
  styles: [
    `
      .sr {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        width: 100%;
      }
      .sr__item {
        flex: 1;
      }
      /* compact : la cellule hugge son contenu (icône + texte) au lieu de remplir 1/N (cf. Figma M4). */
      .sr--compact .sr__item {
        flex: 0 0 auto;
      }
      /* spread : chaque cellule prend 1/N de la largeur (parts égales, pas de trous). */
      .sr--spread .sr__item {
        flex: 1 1 0;
        min-width: 0;
      }
    `,
  ],
})
export class SummaryRow {
  readonly items = input<SummaryItemData[]>([]);
  readonly compact = input(false);
  readonly spread = input(false);
  /** Fond des cellules (token CSS) ; vide = défaut bgRecessed des SummaryItem. */
  readonly tileBackground = input('');
}
