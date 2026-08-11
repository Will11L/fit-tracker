import {
  HEALTH_SECTION_PREFIX,
  MENU_VALUE,
  MODE_TOGGLE_VALUE,
  accentColorForMode,
  accentTextForMode,
  bottomNavForMode,
  bottomNavSelectedValue,
  healthSectionSlug,
  healthSectionValue,
  homeRouteForMode,
  longestMatchingValue,
  modeForUrl,
  nextMode,
  readNavMode,
  sectionForMode,
  sectionForUrl,
  writeNavMode,
  type DrawerSectionRef,
} from './nav-mode';
import { HEALTH_SECTIONS } from '@features/health/health-sections';

describe('Navigation par domaines — logique de mode', () => {
  describe('modeForUrl (le mode suit la page)', () => {
    it('toute URL sous /nutrition* → NUTRITION', () => {
      expect(modeForUrl('/nutrition')).toBe('NUTRITION');
      expect(modeForUrl('/nutrition/goals')).toBe('NUTRITION');
      expect(modeForUrl('/nutrition/stats?x=1')).toBe('NUTRITION');
    });

    it('toute URL sous /health* → HEALTH', () => {
      expect(modeForUrl('/health')).toBe('HEALTH');
      expect(modeForUrl('/health/steps?x=1')).toBe('HEALTH');
    });

    it('le reste → SPORT', () => {
      expect(modeForUrl('/home')).toBe('SPORT');
      expect(modeForUrl('/calendar')).toBe('SPORT');
      expect(modeForUrl('/settings')).toBe('SPORT');
      expect(modeForUrl('/')).toBe('SPORT');
    });

    it('la page Séance autonome (/seance) reste en mode SPORT', () => {
      expect(modeForUrl('/seance')).toBe('SPORT');
    });

    it('ne confond pas un préfixe homographe (/nutritionix)', () => {
      expect(modeForUrl('/nutritionix')).toBe('SPORT');
    });
  });

  describe('homeRouteForMode / nextMode', () => {
    it('accueil de mode', () => {
      expect(homeRouteForMode('SPORT')).toBe('/home');
      expect(homeRouteForMode('NUTRITION')).toBe('/nutrition');
      expect(homeRouteForMode('HEALTH')).toBe('/health');
    });
    it('cycle : Sport → Nutrition → Santé → Sport', () => {
      expect(nextMode('SPORT')).toBe('NUTRITION');
      expect(nextMode('NUTRITION')).toBe('HEALTH');
      expect(nextMode('HEALTH')).toBe('SPORT');
    });
  });

  describe('bottomNavForMode', () => {
    it('1er slot = Menu, dernier slot = bascule (icône = mode courant)', () => {
      const sport = bottomNavForMode('SPORT');
      expect(sport[0].value).toBe(MENU_VALUE);
      expect(sport[sport.length - 1].value).toBe(MODE_TOGGLE_VALUE);
      expect(sport[sport.length - 1].icon).toBe('fitness_center');

      const nutri = bottomNavForMode('NUTRITION');
      expect(nutri[0].value).toBe(MENU_VALUE);
      expect(nutri[nutri.length - 1].value).toBe(MODE_TOGGLE_VALUE);
      expect(nutri[nutri.length - 1].icon).toBe('restaurant');

      const health = bottomNavForMode('HEALTH');
      expect(health[0].value).toBe(MENU_VALUE);
      expect(health[health.length - 1].value).toBe(MODE_TOGGLE_VALUE);
      expect(health[health.length - 1].icon).toBe('ecg_heart');
    });

    it('items SANTÉ : Menu · 1 bouton par section · bascule', () => {
      expect(bottomNavForMode('HEALTH').map((i) => i.value)).toEqual([
        MENU_VALUE,
        ...HEALTH_SECTIONS.map((s) => healthSectionValue(s.slug)),
        MODE_TOGGLE_VALUE,
      ]);
    });

    it('items SANTÉ : chaque bouton porte l’icône + le libellé de sa section', () => {
      const health = bottomNavForMode('HEALTH');
      const sections = health.slice(1, -1); // hors Menu / bascule
      expect(sections.map((i) => i.icon)).toEqual(HEALTH_SECTIONS.map((s) => s.icon));
      expect(sections.map((i) => i.label)).toEqual(HEALTH_SECTIONS.map((s) => s.title));
    });

    it('items SPORT : Menu · Accueil · Calendrier · Chrono · Stats · bascule', () => {
      expect(bottomNavForMode('SPORT').map((i) => i.value)).toEqual([
        MENU_VALUE,
        'home',
        'calendar',
        'chrono',
        'stats',
        MODE_TOGGLE_VALUE,
      ]);
    });

    it('items NUTRITION : Menu · Journal · Objectifs · Catalogue · Stats nutrition · bascule', () => {
      expect(bottomNavForMode('NUTRITION').map((i) => i.value)).toEqual([
        MENU_VALUE,
        'nutrition',
        'nutrition/goals',
        'nutrition/foods',
        'nutrition/stats',
        MODE_TOGGLE_VALUE,
      ]);
    });
  });

  describe('bottomNavSelectedValue (item actif dérivé de l’URL)', () => {
    it('mode SPORT : matche l’item par route', () => {
      const items = bottomNavForMode('SPORT');
      expect(bottomNavSelectedValue('/home', items)).toBe('home');
      expect(bottomNavSelectedValue('/stats', items)).toBe('stats');
      expect(bottomNavSelectedValue('/session/abc', items)).toBe('');
    });

    it('ni Menu ni la bascule ne sont jamais actifs', () => {
      const items = bottomNavForMode('SPORT');
      const sel = bottomNavSelectedValue('/home', items);
      expect(sel).not.toBe(MODE_TOGGLE_VALUE);
      expect(sel).not.toBe(MENU_VALUE);
    });

    it('mode NUTRITION : le chemin le plus spécifique gagne', () => {
      const items = bottomNavForMode('NUTRITION');
      expect(bottomNavSelectedValue('/nutrition', items)).toBe('nutrition');
      expect(bottomNavSelectedValue('/nutrition/goals', items)).toBe('nutrition/goals');
      expect(bottomNavSelectedValue('/nutrition/stats', items)).toBe('nutrition/stats');
    });
  });

  describe('persistance localStorage (readNavMode / writeNavMode)', () => {
    beforeEach(() => localStorage.clear());

    it('défaut SPORT quand rien n’est persisté', () => {
      expect(readNavMode()).toBe('SPORT');
    });

    it('roundtrip : ce qui est écrit est relu', () => {
      writeNavMode('NUTRITION');
      expect(readNavMode()).toBe('NUTRITION');
      writeNavMode('SPORT');
      expect(readNavMode()).toBe('SPORT');
    });

    it('valeur persistée invalide → fallback SPORT', () => {
      localStorage.setItem('web.navMode', 'GARBAGE');
      expect(readNavMode()).toBe('SPORT');
    });
  });

  describe('accent couleur par mode', () => {
    it('Nutrition = dark orange (pill), Santé = vert, Sport = accent standard', () => {
      expect(accentColorForMode('NUTRITION')).toBe('var(--c-dark-orange)');
      expect(accentColorForMode('HEALTH')).toBe('var(--c-medium-green)');
      expect(accentColorForMode('SPORT')).toBe('var(--app-selected-fill)');
    });
    it('icône active en blanc dans tous les modes (accents foncés)', () => {
      expect(accentTextForMode('NUTRITION')).toBe('var(--app-text-on-selected)');
      expect(accentTextForMode('SPORT')).toBe('var(--app-text-on-selected)');
      expect(accentTextForMode('HEALTH')).toBe('var(--app-text-on-selected)');
    });
    it('icône bascule (dernier slot) : orange-medium en Nutrition, vert en Santé, bleu en Sport', () => {
      const nutri = bottomNavForMode('NUTRITION');
      const sport = bottomNavForMode('SPORT');
      const health = bottomNavForMode('HEALTH');
      expect(nutri[nutri.length - 1].iconColor).toBe('var(--c-orange-medium)');
      expect(sport[sport.length - 1].iconColor).toBe('var(--app-primary-action)');
      expect(health[health.length - 1].iconColor).toBe('var(--c-light-green)');
    });
  });

  describe('sectionForMode (reset de l’accordéon à la bascule de mode)', () => {
    it('chaque mode pointe sa section du drawer', () => {
      expect(sectionForMode('SPORT')).toBe('Sport');
      expect(sectionForMode('NUTRITION')).toBe('Nutrition');
      expect(sectionForMode('HEALTH')).toBe('Santé');
    });
  });

  describe('sectionForUrl (accordéon : quelle section ouvrir)', () => {
    const sections: DrawerSectionRef[] = [
      { title: 'Général', paths: ['home', 'notifications', 'routines', 'quotes'] },
      { title: 'Sport', paths: ['seance', 'calendar', 'planning', 'stats', 'chrono'] },
      { title: 'Nutrition', paths: ['nutrition', 'nutrition/goals', 'nutrition/stats'] },
      { title: 'Compte et Paramètres', paths: ['profile', 'settings', 'sync'] },
    ];

    it('ouvre la section qui contient la route courante', () => {
      expect(sectionForUrl('/home', sections)).toBe('Général');
      expect(sectionForUrl('/chrono', sections)).toBe('Sport');
      expect(sectionForUrl('/nutrition', sections)).toBe('Nutrition');
      expect(sectionForUrl('/settings', sections)).toBe('Compte et Paramètres');
    });

    it('la page Séance (/seance) est rattachée à la section Sport', () => {
      expect(sectionForUrl('/seance', sections)).toBe('Sport');
    });

    it('le chemin le plus spécifique gagne (sous-route nutrition)', () => {
      expect(sectionForUrl('/nutrition/goals', sections)).toBe('Nutrition');
      expect(sectionForUrl('/nutrition/stats?x=1', sections)).toBe('Nutrition');
    });

    it('ignore la query/le fragment', () => {
      expect(sectionForUrl('/stats?range=30', sections)).toBe('Sport');
    });

    it('route hors drawer → repli sur la 1ʳᵉ section (jamais tout fermé)', () => {
      expect(sectionForUrl('/session/abc', sections)).toBe('Général');
      expect(sectionForUrl('/unknown', sections)).toBe('Général');
    });

    it('liste de sections vide → chaîne vide', () => {
      expect(sectionForUrl('/home', [])).toBe('');
    });
  });

  describe('sections Santé (barre basse mode Santé : valeur `health#<slug>`)', () => {
    it('healthSectionValue préfixe le slug', () => {
      expect(healthSectionValue('poids')).toBe(HEALTH_SECTION_PREFIX + 'poids');
      expect(healthSectionValue('pas')).toBe('health#pas');
    });

    it('healthSectionSlug extrait le slug d’une valeur de section, null sinon', () => {
      expect(healthSectionSlug('health#poids')).toBe('poids');
      expect(healthSectionSlug('health#pas')).toBe('pas');
      expect(healthSectionSlug('home')).toBeNull();
      expect(healthSectionSlug('health')).toBeNull();
      expect(healthSectionSlug('nutrition/goals')).toBeNull();
    });

    it('roundtrip value ↔ slug pour toutes les sections', () => {
      for (const s of HEALTH_SECTIONS) {
        expect(healthSectionSlug(healthSectionValue(s.slug))).toBe(s.slug);
      }
    });
  });

  describe('longestMatchingValue', () => {
    it('renvoie le segment le plus long qui matche', () => {
      expect(longestMatchingValue('/nutrition/goals', ['nutrition', 'nutrition/goals'])).toBe(
        'nutrition/goals',
      );
      expect(longestMatchingValue('/nutrition', ['nutrition', 'nutrition/goals'])).toBe('nutrition');
      expect(longestMatchingValue('/unknown', ['home', 'stats'])).toBe('');
    });
  });
});
