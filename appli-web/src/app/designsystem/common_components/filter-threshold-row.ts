import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

/** Opérateur d'un seuil de filtre : « au moins » (≥, `gte`) ou « au plus » (≤, `lte`). */
export type FilterThresholdOp = 'gte' | 'lte';

/**
 * Ligne de seuil d'un panneau de filtres (FilterPanel) : libellé + bascule ≥/≤ + champ numérique
 * avec boutons −/+ (pas configurable par ligne). Extraite du Catalogue d'aliments pour mutualiser le
 * langage visuel des seuils (macros, micros…). Le champ se distingue du cadre (fond second-blue)
 * car le panneau est de même teinte ; `value` reste une saisie texte brute (parsée par la page).
 */
@Component({
  selector: 'app-filter-threshold-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ftr">
      <span class="ftr__label" [style.flex-basis]="labelWidth() || null">{{ label() }}</span>
      <div class="ftr__ops" role="group" [attr.aria-label]="'Opérateur ' + label()">
        <button
          type="button"
          class="ftr__op"
          [class.ftr__op--on]="op() === 'gte'"
          (click)="op.set('gte')"
          aria-label="Au moins"
        >
          ≥
        </button>
        <button
          type="button"
          class="ftr__op"
          [class.ftr__op--on]="op() === 'lte'"
          (click)="op.set('lte')"
          aria-label="Au plus"
        >
          ≤
        </button>
      </div>
      <!-- Champ saisissable + boutons + (haut) / − (bas) empilés à droite → stepper étroit (gain de
           place : 2 lignes de filtres tiennent côte à côte). Fond second-blue. -->
      <div class="ftr__stepper">
        <input
          class="ftr__input"
          type="number"
          inputmode="decimal"
          [placeholder]="placeholder()"
          [value]="value()"
          (input)="value.set($any($event.target).value)"
        />
        <div class="ftr__steps">
          <button type="button" class="ftr__step" (click)="bump(1)" aria-label="Augmenter">+</button>
          <button type="button" class="ftr__step" (click)="bump(-1)" aria-label="Diminuer">−</button>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .ftr {
        display: flex;
        align-items: center;
        /* Écart régulier nom ↔ ≥/≤ ↔ stepper, identique sur chaque ligne (un poil d'air, space-2). */
        gap: var(--space-2);
      }
      /* Largeur naturelle par défaut (macros) ; avec une largeur fixe (micros, pour aligner les ≥/≤), le
         texte est aligné à DROITE → le nom colle aux boutons malgré la boîte plus large (le vide passe à
         gauche). Sans largeur fixe, text-align: right est sans effet (boîte = contenu). nowrap = 1 ligne. */
      .ftr__label {
        flex-shrink: 0;
        white-space: nowrap;
        text-align: right;
        color: var(--app-text-secondary);
        font-size: 13px;
      }
      /* Bascule ≥ / ≤ : même langage visuel que SegmentedIconButton (sélection primaryAction). */
      .ftr__ops {
        display: inline-flex;
        gap: 4px;
        flex-shrink: 0;
      }
      .ftr__op {
        width: 34px;
        height: 34px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: 6px;
        border: 1px solid color-mix(in srgb, var(--app-text-secondary) 60%, transparent);
        background: transparent;
        color: var(--c-light-gray-blue);
        font-family: var(--font-family-base);
        font-size: 16px;
        cursor: pointer;
        appearance: none;
        -webkit-appearance: none;
      }
      .ftr__op--on {
        background: var(--c-first-blue);
        border-color: var(--c-first-blue);
        color: var(--app-text-primary);
      }
      /* Stepper [saisie][+/− empilés] : fond second-blue → ressort sur le cadre thirdBlue. */
      .ftr__stepper {
        display: inline-flex;
        align-items: stretch;
        /* Largeur compacte (boutons +/− empilés, pas côte à côte) → 2 lignes de filtres côte à côte. */
        flex: 0 1 84px;
        min-width: 0;
        height: 34px;
        background: var(--c-second-blue);
        border-radius: var(--radius-md);
        overflow: hidden;
      }
      /* Boutons + (haut) / − (bas) empilés en colonne étroite à droite du champ. */
      .ftr__steps {
        display: flex;
        flex-direction: column;
        flex-shrink: 0;
        width: 26px;
      }
      .ftr__step {
        flex: 1;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border: none;
        /* Pavés + / − pleins : fond first-blue, glyphe on-accent (blanc) → contraste sur le fond. */
        background: var(--c-first-blue);
        color: var(--app-on-accent);
        font-family: var(--font-family-base);
        font-size: 14px;
        line-height: 1;
        cursor: pointer;
        appearance: none;
        -webkit-appearance: none;
      }
      /* Léger filet entre + et −. */
      .ftr__step + .ftr__step {
        border-top: 1px solid color-mix(in srgb, var(--app-text-primary) 12%, transparent);
      }
      .ftr__step:hover {
        /* Survol : fond primaryAction (couleur du bouton primaire) ; le glyphe blanc contraste toujours. */
        background: var(--app-primary-action);
      }
      .ftr__input {
        flex: 1;
        min-width: 0;
        width: 100%;
        border: none;
        background: transparent;
        color: var(--app-text-primary);
        text-align: center;
        font-family: var(--font-family-base);
        font-size: var(--font-size-body);
        /* Hauteur de ligne = hauteur du stepper → valeur ET placeholder (plus petit) centrés verticalement. */
        line-height: 34px;
        padding: 0 2px;
        outline: none;
        /* Masque les spinners natifs (on a nos propres boutons −/+). */
        -moz-appearance: textfield;
        appearance: textfield;
      }
      .ftr__input::-webkit-outer-spin-button,
      .ftr__input::-webkit-inner-spin-button {
        -webkit-appearance: none;
        margin: 0;
      }
      .ftr__input::placeholder {
        color: var(--app-text-tertiary);
        /* Placeholder en plus petit que la valeur saisie (moins encombrant quand le champ est vide). */
        font-size: 9px;
      }
    `,
  ],
})
export class FilterThresholdRow {
  /** Libellé du nutriment / critère (colonne de gauche). */
  readonly label = input('');
  /** Largeur fixe optionnelle du libellé (ex. '3rem') → aligne les ≥/≤ d'une ligne à l'autre quand les
   *  abréviations varient beaucoup (micros : « K » vs « Vit B12 »). Vide = largeur naturelle. */
  readonly labelWidth = input('');
  /** Placeholder du champ de valeur (ex. « g / 100 g », « kcal / 100 g »). */
  readonly placeholder = input('');
  /** Pas des boutons −/+ (adapté par ligne : protéines 1, kcal 10, sodium 50…). */
  readonly step = input(1);
  /** Opérateur du seuil (two-way). */
  readonly op = model<FilterThresholdOp>('gte');
  /** Valeur brute saisie (two-way, texte — parsée par la page). */
  readonly value = model('');

  /** Incrémente/décrémente la valeur du pas (borné à ≥ 0 ; champ vide = 0). */
  protected bump(dir: number): void {
    const cur = parseFloat(this.value()) || 0;
    const next = Math.max(0, cur + dir * this.step());
    this.value.set(String(next));
  }
}
