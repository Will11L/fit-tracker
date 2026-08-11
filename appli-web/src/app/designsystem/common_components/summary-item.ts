import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';

/**
 * Cellule de résumé — miroir de SummaryItem.kt : icône tintée + (value SemiBold / label)
 * sur fond bgRecessed (radius 8). `compact` = variante resserrée (icône 24px, value 13px)
 * sur UNE ligne, dans l'ordre icône · label · value, pour les rangées à 3+ cellules ; sinon
 * standard (icône 28px, value 14px, value/label empilés sur 2 lignes).
 */
@Component({
  selector: 'app-summary-item',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <div class="si" [class.si--compact]="compact()" [style.background]="background() || null">
      @if (icon()) {
        @if (compact()) {
          <div class="si__icon-box"><app-icon [name]="icon()" [size]="24" [color]="iconTint()" /></div>
        } @else {
          <div class="si__icon-box"><app-icon [name]="icon()" [size]="28" [color]="iconTint()" /></div>
        }
      }
      <div class="si__texts">
        <span class="si__value"><span class="si__value-x" [style.color]="iconTint()">{{ valueX() }}</span>{{ valueRest() }}</span>
        @if (label()) {
          <span class="si__label">{{ label() }}</span>
        }
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
      }
      .si {
        display: flex;
        align-items: center;
        width: 100%;
        box-sizing: border-box;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-2) var(--space-3);
      }
      .si--compact {
        padding: 10px var(--space-3);
        gap: var(--space-2);
        /* Contenu centré quand la cellule est étirée (SummaryRow spread) ; no-op en hug. */
        justify-content: center;
      }
      .si__icon-box {
        flex: 0.4;
        display: flex;
        justify-content: center;
      }
      .si__texts {
        display: flex;
        flex-direction: column;
        min-width: 0;
      }
      .si:not(.si--compact) .si__texts {
        flex: 0.5;
        margin-left: 5px;
        padding-left: var(--space-2);
      }
      .si--compact .si__icon-box {
        flex: 0 0 auto;
      }
      /* Compact : value + label sur UNE seule ligne (essai vs le 2-lignes d'Android). */
      .si--compact .si__texts {
        flex: 0 1 auto;
        flex-direction: row;
        align-items: baseline;
        gap: 5px;
      }
      .si__value {
        color: var(--app-text-primary);
        font-weight: 600;
      }
      .si:not(.si--compact) .si__value {
        font-size: var(--font-size-body);
      }
      .si--compact .si__value {
        font-size: 13px;
        line-height: 20px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .si__label {
        color: var(--app-text-tertiary);
        font-size: var(--font-size-caption);
      }
      .si--compact .si__label {
        /* Ordre compact : icône · label (nom) · value → le label passe avant la value. */
        order: -1;
        /* Même taille que la value (x/y). */
        font-size: 13px;
        line-height: 20px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    `,
  ],
})
export class SummaryItem {
  readonly icon = input('');
  readonly value = input('');
  readonly label = input('');
  readonly iconTint = input('');
  readonly compact = input(false);
  /** Fond de la cellule (token CSS) ; vide = défaut bgRecessed. Ex. secondBlue quand la
   *  rangée vit déjà dans un cadre recessed (card Fréquence des Stats). */
  readonly background = input('');

  /** Value « x/y » : x (avant le « / ») coloré à la teinte de l'icône, le reste « /y » normal.
   *  Sans « / », x = '' (span vide) et le reste = la value entière. */
  protected readonly valueX = computed(() => {
    const v = this.value();
    const i = v.indexOf('/');
    return i > 0 ? v.slice(0, i) : '';
  });
  protected readonly valueRest = computed(() => {
    const v = this.value();
    const i = v.indexOf('/');
    return i > 0 ? v.slice(i) : v;
  });
}
