import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { WebSocketService } from '@core/sync/ws.service';
import { SyncEngine } from '@core/sync/sync-engine';
import { ThemeService } from '@designsystem/theme/theme.service';
import { SnackbarService } from '@core/snackbar/snackbar.service';
import { routes } from '../app.routes';
import { TodaySessionPage } from '@features/session/today-session-page';
import { SessionPage } from '@features/session/session-page';
import { AppShell } from './app-shell';

/**
 * Verrouille le comportement OBSERVABLE de la tâche « Réorganisation du drawer (4 sections) + page
 * Séance + Déconnexion en bas ». La logique pure de navigation (sectionForUrl, modeForUrl…) est déjà
 * couverte par nav-mode.spec — mais avec un tableau `sections` codé en dur dans le test. Ici on lit la
 * VRAIE structure `AppShell.sections` (ce que voit l'utilisateur) et on la croise avec app.routes,
 * pour empêcher une dérive silencieuse (item renommé/réordonné, route morte, déconnexion remise dans
 * une section). On instancie le composant dans un contexte d'injection avec deps stubées, sans monter
 * le template (pattern recipes-page.component.spec).
 */

interface NavItemProbe {
  label: string;
  icon: string;
  path: string;
}
interface NavSectionProbe {
  title: string;
  icon: string;
  items: NavItemProbe[];
}
interface ShellProbe {
  sections: NavSectionProbe[];
  logout: () => void;
  toggleMode: () => void;
  openSections: () => ReadonlySet<string>;
  mode: () => string;
}

function makeShell(): ShellProbe {
  // jsdom n'implémente pas matchMedia (utilisé dans le constructeur d'AppShell).
  window.matchMedia ??= ((query: string) =>
    ({
      matches: false,
      media: query,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      addListener: () => undefined,
      removeListener: () => undefined,
      onchange: null,
      dispatchEvent: () => false,
    }) as MediaQueryList) as typeof window.matchMedia;

  TestBed.configureTestingModule({
    providers: [
      // isAuthenticated()=false → le constructeur ne déclenche pas loadMe().
      {
        provide: AuthService,
        useValue: { isAuthenticated: () => false, currentUser: () => null, logout: () => undefined },
      },
      { provide: WebSocketService, useValue: { connected: signal(false) } },
      { provide: SyncEngine, useValue: { allStats: () => of([]), syncAll: () => Promise.resolve() } },
      { provide: ThemeService, useValue: { mode: signal('light'), toggle: () => undefined } },
      { provide: SnackbarService, useValue: { items: signal([]) } },
      {
        provide: Router,
        useValue: { url: '/home', events: of(), navigateByUrl: () => Promise.resolve(true) },
      },
    ],
  });
  const shell = TestBed.runInInjectionContext(() => new AppShell());
  return shell as unknown as ShellProbe;
}

/** Chemins de route déclarés sous le shell (enfants du parent ''). */
function declaredRoutePaths(): string[] {
  const shellRoute = routes.find((r) => r.path === '');
  return (shellRoute?.children ?? []).map((c) => c.path ?? '');
}

describe('AppShell — drawer réorganisé (5 sections + Séance + déconnexion en bas)', () => {
  it('expose exactement 5 sections, dans l’ordre cible (Santé ajoutée 2026-07-06)', () => {
    const shell = makeShell();
    expect(shell.sections.map((s) => s.title)).toEqual([
      'Général',
      'Sport',
      'Nutrition',
      'Santé',
      'Compte et Paramètres',
    ]);
  });

  it('Général regroupe Accueil/Notifications/Routines/Citations (Routines + Citations déplacées ici)', () => {
    const general = makeShell().sections.find((s) => s.title === 'Général')!;
    expect(general.items.map((i) => i.path)).toEqual(['home', 'notifications', 'routines', 'quotes']);
  });

  it('Sport contient l’item Séance (→ /seance) et le renommage Planning → « Programme » (route inchangée)', () => {
    const sport = makeShell().sections.find((s) => s.title === 'Sport')!;
    const seance = sport.items.find((i) => i.path === 'seance');
    expect(seance).toBeDefined();
    expect(seance!.label).toBe('Séance');

    const programme = sport.items.find((i) => i.path === 'planning');
    expect(programme).toBeDefined();
    // Libellé renommé en « Programme », mais la route reste 'planning'.
    expect(programme!.label).toBe('Programme');
    // Plus aucun item ne s’appelle « Planning ».
    expect(sport.items.some((i) => i.label === 'Planning')).toBe(false);
  });

  it('Nutrition : l’item stats est renommé « Statistiques » (plus « Statistiques nutrition »), route nutrition/stats', () => {
    const nutrition = makeShell().sections.find((s) => s.title === 'Nutrition')!;
    const stats = nutrition.items.find((i) => i.path === 'nutrition/stats');
    expect(stats).toBeDefined();
    expect(stats!.label).toBe('Statistiques');
    expect(nutrition.items.some((i) => i.label === 'Statistiques nutrition')).toBe(false);
  });

  it('Compte et Paramètres accueille Profil (déplacé depuis l’ancien emplacement)', () => {
    const compte = makeShell().sections.find((s) => s.title === 'Compte et Paramètres')!;
    expect(compte.items.some((i) => i.path === 'profile' && i.label === 'Profil')).toBe(true);
  });

  it('la Déconnexion est SORTIE des sections (action de pied de drawer, pas un item de section)', () => {
    const shell = makeShell();
    const allItems = shell.sections.flatMap((s) => s.items);
    expect(allItems.some((i) => i.path === 'logout' || i.label.toLowerCase().includes('déconnex'))).toBe(
      false,
    );
    // Reste une action exposée par le composant (déclenchée par le footer).
    expect(typeof shell.logout).toBe('function');
  });

  it('chaque item du drawer pointe vers une route déclarée (pas de route morte)', () => {
    const paths = makeShell()
      .sections.flatMap((s) => s.items)
      .map((i) => i.path);
    const declared = new Set(declaredRoutePaths());
    const orphans = paths.filter((p) => !declared.has(p));
    expect(orphans).toEqual([]);
  });
});

describe('AppShell — bascule de mode = reset de l’accordéon du drawer', () => {
  beforeEach(() => localStorage.clear());

  it('toggleMode ouvre LA section du mode cible et referme toutes les autres', () => {
    const shell = makeShell();
    // URL stub '/home' → section initiale « Général » ouverte.
    expect(shell.openSections().has('Général')).toBe(true);

    // Cycle SPORT → NUTRITION : reset sur la seule section « Nutrition ».
    shell.toggleMode();
    expect(shell.mode()).toBe('NUTRITION');
    expect([...shell.openSections()]).toEqual(['Nutrition']);

    // Cycle NUTRITION → HEALTH : reset sur la seule section « Santé ».
    shell.toggleMode();
    expect(shell.mode()).toBe('HEALTH');
    expect([...shell.openSections()]).toEqual(['Santé']);

    // Cycle HEALTH → SPORT : reset sur la seule section « Sport ».
    shell.toggleMode();
    expect(shell.mode()).toBe('SPORT');
    expect([...shell.openSections()]).toEqual(['Sport']);
  });
});

describe('Routing — page Séance autonome + deep-link séance conservé', () => {
  it('/seance charge TodaySessionPage (contenu factorisé, réutilisé par l’Accueil)', async () => {
    const route = (routes.find((r) => r.path === '')?.children ?? []).find((c) => c.path === 'seance');
    expect(route?.loadComponent).toBeDefined();
    const cmp = await route!.loadComponent!();
    expect(cmp).toBe(TodaySessionPage);
  });

  it('le deep-link session/:uuid est conservé et charge SessionPage', async () => {
    const route = (routes.find((r) => r.path === '')?.children ?? []).find(
      (c) => c.path === 'session/:uuid',
    );
    expect(route?.loadComponent).toBeDefined();
    const cmp = await route!.loadComponent!();
    expect(cmp).toBe(SessionPage);
  });
});
