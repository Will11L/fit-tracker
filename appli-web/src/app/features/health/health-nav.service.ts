import { Injectable, signal } from '@angular/core';
import { HEALTH_SECTIONS } from './health-sections';

/**
 * Pont de navigation du hub Santé entre la coquille (barre basse + drawer) et la page `/health`.
 * Providé à la racine → une seule instance partagée.
 *
 * - `activeSlug` : slug de la section actuellement CENTRÉE dans le rail. Écrit par la page (au fil du
 *   scroll manuel), lu par la coquille pour surligner le bon bouton de la barre basse / item du drawer.
 * - `scrollTarget` : demande de CENTRAGE émise par la coquille (clic barre basse / drawer) ou par la
 *   page elle-même (deep-link au chargement). Le `nonce` garantit qu'une même cible rejoue l'effet
 *   (re-centrage si l'utilisateur a légèrement scrollé la même section). La page réagit à ce signal ;
 *   si elle n'est pas encore montée (deep-link depuis une autre page), l'effet se rejoue dès que son
 *   rail apparaît.
 */
@Injectable({ providedIn: 'root' })
export class HealthNavService {
  readonly activeSlug = signal<string>(HEALTH_SECTIONS[0].slug);
  readonly scrollTarget = signal<{ slug: string; nonce: number } | null>(null);
  private nonce = 0;

  /** Demande de centrer la colonne `slug` (barre basse / drawer / deep-link). */
  requestScroll(slug: string): void {
    // L'intention explicite fait foi tout de suite : aux bords, plusieurs colonnes
    // partagent la même position de centrage bornée (à 3 visibles : Pas et FC à 0,
    // Poids et Stress au max) et un clic vers une colonne déjà en place ne déclenche
    // AUCUN événement de scroll — sans ça, l'actif resterait sur l'ex æquo voisin.
    this.activeSlug.set(slug);
    this.scrollTarget.set({ slug, nonce: ++this.nonce });
  }
}
