import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TodaySessionPage } from '@features/session/today-session-page';
import { NutritionPage } from '@features/nutrition/nutrition-page';

/**
 * Écran d'accueil — dashboard du jour à 2 colonnes (plus d'onglets DualTabMenu) :
 *  - GAUCHE : la séance du jour, contenu factorisé `TodaySessionPage` (mode embedded), partagé
 *    avec la route autonome /seance ;
 *  - DROITE : le journal nutrition du jour, `NutritionPage` en mode embedded = bandeau résumé du
 *    jour + repas, SANS le calendrier mensuel (la vue complète calendrier + jour reste sur
 *    /nutrition). Vue jour réutilisée, pas de logique dupliquée.
 *
 * Calendrier & Objectifs (/calendar) et Programme (/planning) restent accessibles via le drawer.
 * « Voir le programme » de la colonne Séance (émis par TodaySessionPage en embedded) navigue vers
 * /planning. Gouttières neutralisées comme calendar-goals-page : la page fournit sa gouttière, les
 * enfants embarqués annulent la leur (--page-gutter à 0). Empilement vertical en écran étroit
 * (Séance puis Nutrition).
 */
@Component({
  selector: 'app-home-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ScreenTitleBar, TodaySessionPage, NutritionPage],
  template: `
    <app-screen-title-bar title="Accueil" />
    <section class="dashboard">
      <div class="dashboard__col">
        <app-today-session-page [embedded]="true" (viewProgram)="goToPlanning()" />
      </div>
      <div class="dashboard__col">
        <app-nutrition-page [embedded]="true" />
      </div>
    </section>
  `,
  styles: [
    `
      .dashboard {
        display: flex;
        gap: var(--page-gutter);
        align-items: flex-start;
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-4);
        box-sizing: border-box;
        width: 100%;
      }
      .dashboard__col {
        flex: 1 1 0;
        min-width: 0;
        display: flex;
        flex-direction: column;
        /* Les enfants embarqués gardent leur .page__body : on annule leur gouttière,
           le dashboard fournit la sienne (évite les doublons de padding). */
        --page-gutter: 0px;
        --page-gutter-top: 0px;
      }
      @media (max-width: 900px) {
        .dashboard {
          flex-direction: column;
        }
        .dashboard__col {
          width: 100%;
        }
      }
    `,
  ],
})
export class HomePage {
  private readonly router = inject(Router);

  /** « Voir le programme » de la colonne Séance → page Programme (aussi dans le drawer). */
  protected goToPlanning(): void {
    void this.router.navigateByUrl('/planning');
  }
}
