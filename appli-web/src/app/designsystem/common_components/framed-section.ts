import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TitledDivider } from '@designsystem/common_components/titled-divider';

/**
 * Section encadrée : cadre thirdBlue (fond recessed + coins arrondis) dont l'EN-TÊTE est un
 * `app-titled-divider`, suivi du contenu projeté. Pendant « titré » de `app-list-frame`.
 *
 * - **défaut** : padding partout, pour du contenu padé (tuiles, champs, barres…).
 * - **`flush`** : rows bord à bord (= `app-list-frame` titré) — pas de padding horizontal sur le
 *   corps, `overflow: hidden` pour que les coins arrondis et le liseré de sélection des
 *   `app-list-row` épousent le bord du cadre. Seul l'en-tête garde un retrait horizontal.
 * - **sans `title` (ni `icon`)** : cadre nu (pas d'en-tête titled-divider) — juste la boîte
 *   thirdBlue autour du contenu projeté.
 *
 * Usage : `<app-framed-section title="Jours" [flush]="true"> <app-list-row …/>… </app-framed-section>`
 */
@Component({
  selector: 'app-framed-section',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TitledDivider],
  host: {
    '[class.fs--flush]': 'flush()',
    '[class.fs--fill]': 'fill()',
  },
  template: `
    @if (title() || icon()) {
      <div class="fs__head"><app-titled-divider [title]="title()" [icon]="icon()" /></div>
    }
    <div class="fs__body"><ng-content /></div>
  `,
  styles: [
    `
      :host {
        display: block;
        min-width: 0;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        box-sizing: border-box;
      }
      /* Défaut : padding uniforme 16px sur tous les côtés (contenu padé = cadre). */
      :host(:not(.fs--flush)) {
        padding: 16px;
      }
      /* Flush : rows bord à bord ; overflow hidden clippe coins/anneaux au bord du cadre ;
         l'en-tête garde le padding haut + un retrait horizontal. */
      :host(.fs--flush) {
        padding-top: var(--space-2);
        overflow: hidden;
      }
      :host(.fs--flush) .fs__head {
        /* Retrait horizontal = padding canonique 16px des cadres non-flush. */
        padding: 0 16px;
      }
      .fs__body {
        display: flex;
        flex-direction: column;
        min-width: 0;
      }
      /* Espace divider→contenu = espace cadre→divider (le haut du divider a le padding du cadre
         + le sien) : on remet le padding du cadre sous le divider pour symétriser. Seulement quand
         il y a un en-tête : le cadre nu (sans titre) garde le padding haut du host. */
      :host:has(.fs__head) .fs__body {
        padding-top: var(--space-2);
      }
      /* Mode « fill » : le cadre devient une colonne flex et son corps prend toute la hauteur
         disponible (host étiré par le parent) → le contenu peut se répartir (ex. donut en haut,
         légende en bas via justify-content: space-between). */
      :host(.fs--fill) {
        display: flex;
        flex-direction: column;
      }
      :host(.fs--fill) .fs__body {
        flex: 1 1 auto;
        min-height: 0;
      }
    `,
  ],
})
export class FramedSection {
  readonly title = input('');
  /** Clé d'icône optionnelle affichée après le titre (passée au titled-divider). */
  readonly icon = input('');
  /** Mode list-frame titré : rows bord à bord, coins/anneaux clippés au cadre. */
  readonly flush = input(false);
  /** Cadre en colonne flex dont le corps remplit la hauteur (host étiré par le parent) → permet de
   *  répartir le contenu verticalement (ex. légende poussée en bas via justify-content). */
  readonly fill = input(false);
}
