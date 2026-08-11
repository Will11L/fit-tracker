import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { GoalsPage } from '../goals/goals-page';
import { CalendarPage } from './calendar-page';

/**
 * Page combinée Calendrier + Objectifs — les deux écrans ont beaucoup d'espace libre sur
 * desktop, on les compose en 2 colonnes (calendrier à gauche, objectifs à droite). Route
 * canonique /calendar (/goals redirige) ; le hub Home l'embarque aussi (mode embedded).
 * Réutilise les composants existants en mode `embedded` (pas de title bar propre) ; les
 * gouttières internes des enfants sont neutralisées via override des custom properties
 * (--page-gutter / --page-gutter-top à 0 sur les colonnes), la page fournit la sienne.
 * Empilement vertical en écran étroit (même breakpoint que planning-page / session-page).
 */
@Component({
  selector: 'app-calendar-goals-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ScreenTitleBar, CalendarPage, GoalsPage],
  template: `
    <section class="page">
      @if (!embedded()) { <app-screen-title-bar title="Calendrier & Objectifs" /> }

      <div class="page__body">
        <div class="cols">
          <div class="cols__col">
            <app-calendar-page [embedded]="true" />
          </div>
          <div class="cols__col">
            <app-goals-page [embedded]="true" />
          </div>
        </div>
      </div>
    </section>
  `,
  styles: [
    `
      /* Title bar pleine largeur (hors corps) ; corps avec gouttière (--page-gutter). */
      .page__body {
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-4);
      }
      .cols {
        display: flex;
        gap: var(--page-gutter);
        align-items: flex-start;
      }
      .cols__col {
        flex: 1 1 0;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        /* Les enfants embarqués gardent leur .page__body : on annule leurs gouttières,
           la page combinée fournit la sienne (évite les doublons de padding). */
        --page-gutter: 0px;
        --page-gutter-top: 0px;
      }
      @media (max-width: 900px) {
        .cols {
          flex-direction: column;
        }
        .cols__col {
          width: 100%;
        }
      }
    `,
  ],
})
export class CalendarGoalsPage {
  /** Mode embarqué (hub Home) : masque la title bar (le hub fournit les onglets). */
  readonly embedded = input(false);
}
