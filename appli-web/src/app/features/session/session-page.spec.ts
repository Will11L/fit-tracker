import { NO_ERRORS_SCHEMA, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { SnackbarService } from '@core/snackbar/snackbar.service';
import type { LocalActualWorkout } from '@core/models/actual-workout.model';
import { SessionPage } from './session-page';

/**
 * Verrouille le comportement OBSERVABLE de la tâche « Page Séance — layout 2 colonnes (rows gauche /
 * détail droite) en autonome, vertical seulement en embarqué (Accueil) » (commit 486bd6d).
 *
 * Le seul levier de la tâche est le binding `[class.split--stacked]="embedded()"` sur le conteneur
 * master-detail `.split` :
 *   - autonome (/session, /session/:uuid, `embedded=false` par défaut) → 2 colonnes côte-à-côte
 *     (pas de modificateur `.split--stacked` ; `flex-direction: row` par défaut du `.split`) ;
 *   - embarqué (dashboard Accueil, `embedded=true`) → empilement vertical (`.split--stacked`).
 * Un test qui ne distinguerait pas les deux modes n'aurait aucune valeur : on monte la page dans les
 * deux modes et on vérifie présence/absence du modificateur. On vérifie aussi que la title bar (masquée
 * en embarqué via `@if (!embedded())`) suit le même contrat autonome/embarqué.
 *
 * `.split` n'est rendu que si la séance existe (`view().workout`), donnée alimentée par un `liveQuery`.
 * Sous le runner (Node/jsdom), la machinerie réactive de Dexie ne tourne pas : `liveQuery` n'émet
 * jamais (vérifié), d'où le fait que les specs de page existants neutralisent leurs enfants. Comme on
 * a justement besoin de rendre le corps de la page, on substitue la source de données interne par un
 * signal contrôlé (seam de test) ; les assertions, elles, portent uniquement sur le DOM rendu (le
 * comportement observable). Enfants neutralisés (imports vidés + NO_ERRORS_SCHEMA, à cause de
 * l'attribut `[appRevealIn]` sur un <div> standard, cf. nutrition-page dans home-dashboard.spec).
 */

const WORKOUT_UUID = 'w-1';
const WORKOUT: LocalActualWorkout = {
  uuid: WORKOUT_UUID,
  userId: 1,
  name: 'Push Day',
  date: '2026-06-17',
  notes: null,
  location: null,
  isDone: false,
  updatedAt: '2026-06-17T08:00:00Z',
  synced: true,
  pendingDeletion: false,
};

function dbStub(): AppDb {
  // liveQuery exécute ces queriers à la construction ; on rend juste des Promises inertes.
  const table = (rows: unknown[]) => ({ toArray: () => Promise.resolve(rows) });
  return {
    actual_workouts: table([]),
    actual_workout_exercises: table([]),
    actual_workout_sets: table([]),
    exercises: table([]),
  } as unknown as AppDb;
}

function mount(embedded: boolean): HTMLElement {
  TestBed.configureTestingModule({
    imports: [SessionPage],
    providers: [
      { provide: AppDb, useValue: dbStub() },
      { provide: SyncEngine, useValue: { syncAll: () => Promise.resolve() } },
      { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
      { provide: SnackbarService, useValue: { success: () => {}, error: () => {} } },
    ],
  });
  TestBed.overrideComponent(SessionPage, {
    set: { imports: [], schemas: [NO_ERRORS_SCHEMA] },
  });
  const fixture = TestBed.createComponent(SessionPage);
  fixture.componentRef.setInput('uuid', WORKOUT_UUID);
  fixture.componentRef.setInput('embedded', embedded);
  // Seam : remplace la source liveQuery (qui n'émet pas sous jsdom) par un signal contrôlé,
  // AVANT le premier rendu, pour que le computed `view()` voie la séance et rende `.split`.
  (fixture.componentInstance as unknown as { workouts: unknown }).workouts = signal([WORKOUT]);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('SessionPage — 2 colonnes en autonome, vertical en embarqué', () => {
  it('autonome (embedded=false) : .split rendu SANS .split--stacked (2 colonnes) + title bar présente', () => {
    const el = mount(false);

    const split = el.querySelector('.split');
    expect(split).toBeTruthy(); // garde-fou : la séance a chargé, le master-detail est rendu
    expect(split!.classList.contains('split--stacked')).toBe(false);
    // master (liste par phase) ET detail tous deux présents pour former les 2 colonnes
    expect(split!.querySelector('.split__list')).toBeTruthy();
    expect(split!.querySelector('.split__detail')).toBeTruthy();
    // autonome = page complète avec sa title bar
    expect(el.querySelector('app-screen-title-bar')).toBeTruthy();
  });

  it('embarqué (embedded=true) : .split porte .split--stacked (vertical) + title bar masquée', () => {
    const el = mount(true);

    const split = el.querySelector('.split');
    expect(split).toBeTruthy();
    expect(split!.classList.contains('split--stacked')).toBe(true);
    // embarqué dans le dashboard Accueil = pas de title bar (le hub fournit le cadre)
    expect(el.querySelector('app-screen-title-bar')).toBeNull();
  });
});
