import type { BottomNavItemData } from '@designsystem/common_components/bottom-nav-bar';
import { HEALTH_SECTIONS } from '@features/health/health-sections';

/**
 * Mode de navigation par domaine (UPPER_CASE, cf. politique 11) — pilote la barre basse.
 * SPORT = entraînement, NUTRITION = journal alimentaire, HEALTH = données santé (lecture seule).
 */
export type NavMode = 'SPORT' | 'NUTRITION' | 'HEALTH';

/** Valeur réservée du dernier slot de la barre basse : bouton bascule de mode (pas une route). */
export const MODE_TOGGLE_VALUE = 'mode-toggle';

/** Valeur réservée du 1er slot : bouton Menu (ouvre/replie le drawer ; pas une route). */
export const MENU_VALUE = 'menu';

/**
 * Préfixe des valeurs de la barre basse qui désignent une SECTION du hub Santé (mode Santé) et non une
 * route : `health#<slug>`. Le clic ne navigue pas vers une page mais centre la colonne correspondante
 * (cf. `AppShell.onBottomNav`). Aligné sur le fragment de deep-link `/health#<slug>`.
 */
export const HEALTH_SECTION_PREFIX = 'health#';

/** Valeur de barre basse / d'item drawer pour une section Santé (`health#poids`). */
export function healthSectionValue(slug: string): string {
  return HEALTH_SECTION_PREFIX + slug;
}

/** Slug de section Santé porté par une valeur `health#<slug>`, ou null si ce n'en est pas une. */
export function healthSectionSlug(value: string): string | null {
  return value.startsWith(HEALTH_SECTION_PREFIX)
    ? value.slice(HEALTH_SECTION_PREFIX.length)
    : null;
}

const STORAGE_KEY = 'web.navMode';

/** Normalise une URL (retire query/fragment) pour le matching de chemin. */
function pathOf(url: string): string {
  return url.split(/[?#]/)[0];
}

/** Le mode suit la page : /nutrition* → NUTRITION, /health* → HEALTH, sinon SPORT. */
export function modeForUrl(url: string): NavMode {
  const p = pathOf(url);
  if (p === '/nutrition' || p.startsWith('/nutrition/')) return 'NUTRITION';
  if (p === '/health' || p.startsWith('/health/')) return 'HEALTH';
  return 'SPORT';
}

/** Page d'accueil d'un mode (cible de la bascule manuelle). */
export function homeRouteForMode(mode: NavMode): string {
  if (mode === 'NUTRITION') return '/nutrition';
  if (mode === 'HEALTH') return '/health';
  return '/home';
}

/** Mode suivant du cycle (clic sur la bascule) : SPORT → NUTRITION → HEALTH → SPORT. */
export function nextMode(mode: NavMode): NavMode {
  if (mode === 'SPORT') return 'NUTRITION';
  if (mode === 'NUTRITION') return 'HEALTH';
  return 'SPORT';
}

/**
 * Accent couleur de la pill active de la barre basse par domaine : bleu primaire en Sport, dark
 * orange (#9D5300, identité nutrition, aligné Android `darkOrange`) en Nutrition, vert (#008444,
 * identité santé) en Santé. Pill de l'item actif + icône bascule.
 */
export function accentColorForMode(mode: NavMode): string {
  if (mode === 'NUTRITION') return 'var(--c-dark-orange)';
  if (mode === 'HEALTH') return 'var(--c-medium-green)';
  return 'var(--app-selected-fill)';
}

/** Couleur de l'icône active : blanche dans tous les modes (les accents sont assez foncés). */
export function accentTextForMode(_mode: NavMode): string {
  return 'var(--app-text-on-selected)';
}

/**
 * Items de la barre basse pour un mode donné. 1er slot = Menu (ouvre le drawer), dernier slot =
 * bascule de mode (son icône reflète le mode COURANT : haltère en sport, assiette en nutrition,
 * cœur en santé ; tap = cycle vers le mode suivant). Les raccourcis du milieu dépendent du mode.
 */
export function bottomNavForMode(mode: NavMode): BottomNavItemData[] {
  const menu: BottomNavItemData = { value: MENU_VALUE, icon: 'menu', label: 'Menu' };

  // La bascule porte l'icône + la teinte d'accent du mode COURANT (signal du domaine actif). Icônes
  // posées sur le fond sombre de la barre → teintes claires (l'accent foncé reste réservé à la pill).
  const toggles: Record<NavMode, BottomNavItemData> = {
    SPORT: {
      value: MODE_TOGGLE_VALUE,
      icon: 'fitness_center',
      label: 'Mode Sport — basculer vers Nutrition',
      iconColor: 'var(--app-primary-action)',
    },
    NUTRITION: {
      value: MODE_TOGGLE_VALUE,
      icon: 'restaurant',
      label: 'Mode Nutrition — basculer vers Santé',
      iconColor: 'var(--c-orange-medium)',
    },
    HEALTH: {
      value: MODE_TOGGLE_VALUE,
      icon: 'ecg_heart',
      label: 'Mode Santé — basculer vers Sport',
      iconColor: 'var(--c-light-green)',
    },
  };
  const toggle = toggles[mode];

  if (mode === 'NUTRITION') {
    return [
      menu,
      { value: 'nutrition', icon: 'today', label: 'Journal' },
      { value: 'nutrition/goals', icon: 'flag', label: 'Objectifs' },
      { value: 'nutrition/foods', icon: 'grocery', label: 'Catalogue' },
      { value: 'nutrition/stats', icon: 'insights', label: 'Stats nutrition' },
      toggle,
    ];
  }
  if (mode === 'HEALTH') {
    // Santé = un seul écran (rail de colonnes) → 1 bouton par section, chacun centre sa colonne
    // (valeur `health#<slug>`, pas une route ; cf. AppShell.onBottomNav). L'actif suit le scroll.
    return [
      menu,
      ...HEALTH_SECTIONS.map((s) => ({
        value: healthSectionValue(s.slug),
        icon: s.icon,
        label: s.title,
        // Actif = fond couleur de section (icône blanche par-dessus), miroir HealthIconBar Android.
        activeColor: s.color,
      })),
      toggle,
    ];
  }
  return [
    menu,
    { value: 'home', icon: 'home', label: 'Accueil' },
    { value: 'calendar', icon: 'calendar_month', label: 'Calendrier' },
    { value: 'chrono', icon: 'timer', label: 'Chrono' },
    { value: 'stats', icon: 'equalizer', label: 'Statistiques' },
    toggle,
  ];
}

/**
 * Item actif de la barre basse, dérivé de l'URL. Menu et bascule ne sont jamais « actifs ».
 * Le chemin le plus spécifique gagne (/nutrition/goals prime sur /nutrition).
 */
export function bottomNavSelectedValue(url: string, items: BottomNavItemData[]): string {
  return longestMatchingValue(
    pathOf(url),
    items
      .filter((it) => it.value !== MODE_TOGGLE_VALUE && it.value !== MENU_VALUE)
      .map((it) => it.value),
  );
}

/**
 * Parmi une liste de segments de route ('home', 'nutrition/goals', …), renvoie celui qui matche le
 * mieux l'URL (préfixe exact ou suivi d'un '/'), le plus long l'emportant. '' si aucun.
 */
export function longestMatchingValue(url: string, values: string[]): string {
  const p = pathOf(url);
  let best = '';
  for (const v of values) {
    const route = '/' + v;
    if ((p === route || p.startsWith(route + '/')) && v.length > best.length) best = v;
  }
  return best;
}

/**
 * Drawer accordéon : description minimale d'une section pour dériver laquelle ouvrir.
 * `title` = libellé de la section ; `paths` = chemins de ses items qui portent une route.
 */
export interface DrawerSectionRef {
  title: string;
  paths: string[];
}

/**
 * Section du drawer associée à un mode : cible du RESET de l'accordéon à la bascule de mode (le
 * drawer ouvre LA section du mode choisi et referme toutes les autres — seule exception au
 * comportement add-only). La navigation induite par la bascule est neutralisée côté `AppShell`
 * (drapeau one-shot) → SEULE la section du mode reste ouverte, même si la page d'atterrissage
 * appartient à une autre section (ex. Sport → Accueil ∈ « Général »). Titres = ceux de `AppShell.sections`.
 */
export function sectionForMode(mode: NavMode): string {
  if (mode === 'NUTRITION') return 'Nutrition';
  if (mode === 'HEALTH') return 'Santé';
  return 'Sport';
}

/**
 * Détermine quelle section du drawer ouvrir pour une URL donnée : celle qui contient le chemin le
 * plus spécifique matchant l'URL courante (même règle que l'item actif). Si aucune section ne matche
 * (route hors drawer), renvoie le titre de la 1ʳᵉ section comme repli par défaut (jamais tout fermé).
 * Helper pur (testable hors composant).
 */
export function sectionForUrl(url: string, sections: DrawerSectionRef[]): string {
  const allPaths = sections.flatMap((s) => s.paths);
  const best = longestMatchingValue(url, allPaths);
  if (best) {
    const owner = sections.find((s) => s.paths.includes(best));
    if (owner) return owner.title;
  }
  return sections.length > 0 ? sections[0].title : '';
}

/** Lit le mode persisté (localStorage) ; défaut SPORT si absent/invalide. */
export function readNavMode(): NavMode {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'NUTRITION' ? 'NUTRITION' : 'SPORT';
  } catch {
    return 'SPORT';
  }
}

/** Persiste le mode (localStorage). Best-effort. */
export function writeNavMode(mode: NavMode): void {
  try {
    localStorage.setItem(STORAGE_KEY, mode);
  } catch {
    // stockage indisponible : ignoré (le mode suit l'URL de toute façon)
  }
}
