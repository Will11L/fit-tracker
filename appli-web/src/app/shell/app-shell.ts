import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  HostListener,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map, startWith } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { WebSocketService } from '@core/sync/ws.service';
import { SyncEngine } from '@core/sync/sync-engine';
import { SyncStats } from '@core/sync/syncable-store';
import { ThemeService } from '@designsystem/theme/theme.service';
import { AppIcon } from '@designsystem/icons/app-icon';
import { DrawerSection } from '@designsystem/common_components/drawer-section';
import { DrawerItem } from '@designsystem/common_components/drawer-item';
import { DrawerFooter } from '@designsystem/common_components/drawer-footer';
import { RevealIn } from '@designsystem/common_components/reveal-in';
import { BottomNavBar, type BottomNavItemData } from '@designsystem/common_components/bottom-nav-bar';
import { AppSnackbarHost } from '@designsystem/common_components/app-snackbar-host';
import { SnackbarService } from '@core/snackbar/snackbar.service';
import {
  MENU_VALUE,
  MODE_TOGGLE_VALUE,
  type NavMode,
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
import { HealthNavService } from '@features/health/health-nav.service';
import { HEALTH_SECTIONS } from '@features/health/health-sections';

interface NavItem {
  label: string;
  icon: string;
  path: string;
  /**
   * Section du hub Santé ciblée (drawer Santé). Si défini, l'item ne navigue pas vers `/path` mais
   * ouvre `/health` et centre la colonne `slug` (via [HealthNavService]) ; son état actif suit le
   * scroll du rail plutôt que l'URL.
   */
  slug?: string;
}
interface NavSection {
  title: string;
  /** Icône représentant la section, affichée en tête de groupe en mode rail (titre texte ne rentre pas). */
  icon: string;
  items: NavItem[];
}

/**
 * Coquille applicative — drawer calqué sur le NavigationDrawer Figma/Android (cf. `.figma-refs/specs/drawer.md`).
 * Recomposé avec les composants DS : DrawerSection (titre + dividers) > DrawerItem (icône + label + actif),
 * et DrawerFooter (état sync + icônes) en bas. Navigation programmatique (l'actif est dérivé de l'URL courante).
 * Adapté desktop (sidebar persistante ; off-canvas + scrim sous 900px).
 *
 * Divergences assumées vs le drawer Android (features web ≠ Android) :
 * - Activité : pas de Conversations/Tâches dédiées ; ajoute Objectifs/Matériel/Chrono (features web).
 * - DrawerFooter web : pas de timestamp "dernière sync" → on affiche l'état (synchronisé / N à synchroniser),
 *   + un toggle de thème (web-only) au-dessus du footer.
 * - Design System (showcase) placé dans Compte & paramètres (le Figma le met sous Admin gated).
 */

/** Position libre persistée de la bottom nav (localStorage). null = position par défaut (bas-centre). */
function readNavPos(): { left: number; top: number } | null {
  try {
    const s = localStorage.getItem('bottomNavPos');
    return s ? (JSON.parse(s) as { left: number; top: number }) : null;
  } catch {
    return null;
  }
}
function writeNavPos(pos: { left: number; top: number } | null): void {
  try {
    if (pos) localStorage.setItem('bottomNavPos', JSON.stringify(pos));
  } catch {
    /* localStorage indispo : on ignore (position non persistée). */
  }
}

@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, AppIcon, DrawerSection, DrawerItem, DrawerFooter, BottomNavBar, AppSnackbarHost, RevealIn],
  template: `
    <div class="shell" [class.shell--open]="sidebarOpen()" [class.shell--collapsed]="effectiveCollapsed()">
      <aside class="sidebar">
        <nav class="nav">
          @for (s of sections; track s.title) {
            <app-drawer-section
              [title]="s.title"
              [icon]="s.icon"
              [collapsed]="effectiveCollapsed()"
              [collapsible]="true"
              [open]="openSections().has(s.title)"
              (headerClick)="toggleSection(s.title)"
            >
              @for (item of s.items; track item.label) {
                <app-drawer-item
                  [icon]="item.icon"
                  [label]="item.label"
                  [active]="isActive(item)"
                  [collapsed]="effectiveCollapsed()"
                  (clicked)="onItem(item)"
                />
              }
            </app-drawer-section>
          }
        </nav>

        <div class="footer">
          @if (effectiveCollapsed()) {
            <!-- rail (haut->bas) : déconnexion · thème · réseau · sync · WS. -->
            <div class="footer__rail">
              <button type="button" class="footer__btn" (click)="logout()" aria-label="Déconnexion">
                <app-icon name="logout" [size]="20" color="var(--app-text-secondary)" />
              </button>
              <button type="button" class="footer__btn" (click)="theme.toggle()" aria-label="Thème">
                <app-icon name="dark_mode" [size]="20" color="var(--app-text-secondary)" />
              </button>
              <app-icon
                [name]="online() ? 'wifi' : 'wifi_off'"
                [size]="20"
                [color]="online() ? 'var(--app-snackbar-success)' : 'var(--app-snackbar-error)'"
              />
              <button type="button" class="footer__btn" (click)="triggerSync()" aria-label="Synchroniser">
                <app-icon
                  [name]="pending() > 0 ? 'cloud_upload' : 'cloud_done'"
                  [size]="20"
                  [color]="pending() > 0 ? 'var(--app-snackbar-warning)' : 'var(--app-primary-action)'"
                />
                @if (pending() > 0) {
                  <span class="footer__badge">{{ pending() }}</span>
                }
              </button>
              <app-icon
                name="router"
                [size]="20"
                [color]="wsConnected() ? 'var(--app-snackbar-success)' : 'var(--app-snackbar-warning)'"
              />
            </div>
          } @else {
            <!-- Déplié (haut->bas) : déconnexion · thème · état sync. -->
            <button type="button" class="footer__logout" (click)="logout()">
              <app-icon name="logout" [size]="18" color="var(--app-text-secondary)" />
              <span>Déconnexion</span>
            </button>
            <button type="button" class="footer__theme" (click)="theme.toggle()">
              <app-icon name="dark_mode" [size]="18" color="var(--app-text-secondary)" />
              <span>Thème : {{ theme.mode() }}</span>
            </button>
            <app-drawer-footer [text]="pending() > 0 ? pending() + ' à synchroniser' : 'Synchronisé'">
              <app-icon
                trailing
                [name]="online() ? 'wifi' : 'wifi_off'"
                [size]="20"
                [color]="online() ? 'var(--app-snackbar-success)' : 'var(--app-snackbar-error)'"
              />
              <button trailing type="button" class="footer__btn" (click)="triggerSync()" aria-label="Synchroniser">
                <app-icon
                  [name]="pending() > 0 ? 'cloud_upload' : 'cloud_done'"
                  [size]="20"
                  [color]="pending() > 0 ? 'var(--app-snackbar-warning)' : 'var(--app-primary-action)'"
                />
                @if (pending() > 0) {
                  <span class="footer__badge">{{ pending() }}</span>
                }
              </button>
              <app-icon
                trailing
                name="router"
                [size]="20"
                [color]="wsConnected() ? 'var(--app-snackbar-success)' : 'var(--app-snackbar-warning)'"
              />
            </app-drawer-footer>
          }
        </div>
      </aside>

      <div class="content">
        <header class="topbar">
          <button type="button" class="burger" (click)="toggle()" aria-label="Menu">☰</button>
          <span class="topbar__title">Fit Tracker</span>
        </header>
        <!-- Contenu de route : entre en slide-down + fade au 1er affichage, puis fondu seul à chaque
             navigation (clé = URL courante) → transition douce entre pages, sans sursaut. -->
        <main class="outlet" [appRevealIn]="currentUrl()"><router-outlet /></main>
      </div>

      <div class="scrim" (click)="close()"></div>

      <app-bottom-nav-bar
        class="bottomnav"
        [class.bottomnav--free]="navPos() !== null"
        [style.left.px]="navPos()?.left"
        [style.top.px]="navPos()?.top"
        [items]="bottomNav()"
        [selected]="bottomNavSelected()"
        [accentColor]="navAccentColor()"
        [accentText]="navAccentText()"
        (select)="onBottomNav($event)"
        (pointerdown)="onNavDown($event)"
      />

      <!-- Snackbars app-wide (SnackbarService) : empilés en bas, au-dessus de la bottom nav. -->
      <div class="snackbars">
        <app-snackbar-host
          [snackbars]="snackbar.items()"
          (actionClick)="snackbar.runAction($event)"
          (secondaryActionClick)="snackbar.runSecondaryAction($event)"
        />
      </div>
    </div>
  `,
  styles: [
    `
      .shell {
        display: flex;
        /* Coquille à hauteur d'écran FIXE : seul le contenu (.outlet) défile, jamais la fenêtre. Sinon
           une page master/détail (liste longue OU panneau détail haut) crée une barre de scroll de
           fenêtre — visible même quand une colonne est courte, avec de l'espace vide à côté. 100dvh
           tient compte de la barre d'URL mobile (repli 100vh). */
        height: 100vh;
        height: 100dvh;
        overflow: hidden;
      }
      .sidebar {
        width: 300px;
        flex-shrink: 0;
        background: var(--app-bg-screen);
        display: flex;
        flex-direction: column;
        /* Drawer indépendant : collé au viewport (100vh), ne s'étire pas avec un contenu long.
           Seul le contenu scrolle ; la nav interne scrolle si trop d'items (footer épinglé en bas). */
        position: sticky;
        top: 0;
        align-self: flex-start;
        height: 100vh;
        transition: width 0.2s ease;
      }
      /* Rail plié (desktop) : largeur réduite (icônes seules). */
      .shell--collapsed .sidebar {
        width: 56px;
      }
      /* En rail, le footer montre les 3 icônes de statut empilées (bas->haut : WS, sync, réseau). */
      .footer__rail {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--space-3);
        padding: var(--space-3) 0;
      }
      .nav {
        flex: 1;
        overflow-y: auto;
        /* L'espace restant sous la dernière section prend le fond bgRecessed -> continu. */
        background: var(--app-bg-recessed);
      }
      /* Séparateur entre les sections (DrawerSection porte déjà son propre top-divider sous le titre). */
      app-drawer-section + app-drawer-section {
        border-top: 1.5px solid var(--app-divider-strong);
      }
      .footer {
        display: flex;
        flex-direction: column;
        background: var(--app-bg-recessed);
      }
      .footer__theme,
      .footer__logout {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        background: none;
        border: none;
        color: var(--app-text-secondary);
        cursor: pointer;
        padding: var(--space-2) var(--space-4);
        font-size: var(--font-size-caption);
        font-family: var(--font-family-base);
      }
      /* « light » / « dark » capitalisé ; le libellé Déconnexion reste tel quel. */
      .footer__theme {
        text-transform: capitalize;
      }
      .footer__btn {
        position: relative;
        background: none;
        border: none;
        cursor: pointer;
        padding: 2px;
        display: inline-flex;
      }
      .footer__badge {
        position: absolute;
        top: -4px;
        right: -6px;
        min-width: 15px;
        height: 15px;
        padding: 0 3px;
        box-sizing: border-box;
        border-radius: var(--radius-pill);
        background: var(--app-snackbar-error);
        color: #fff;
        font-size: 10px;
        line-height: 15px;
        text-align: center;
      }
      .content {
        flex: 1;
        display: flex;
        flex-direction: column;
        min-width: 0;
        /* min-height: 0 → autorise l'enfant scrollable (.outlet) à se contraindre à la hauteur dispo
           au lieu de pousser la coquille (sans ça, flex empêche le scroll interne). */
        min-height: 0;
      }
      .topbar {
        display: none;
        align-items: center;
        gap: var(--space-3);
        padding: var(--space-3) var(--space-4);
        border-bottom: 1px solid var(--app-divider-strong);
        background: var(--app-bg-recessed);
      }
      .burger {
        background: none;
        border: none;
        color: var(--app-text-primary);
        font-size: 22px;
        cursor: pointer;
      }
      .topbar__title {
        color: var(--app-accent-text);
        font-weight: var(--font-weight-bold);
      }
      .outlet {
        flex: 1;
        min-width: 0;
        /* Conteneur de scroll de l'application : la page défile ICI, pas la fenêtre. Page courte →
           aucune barre ; page haute → barre confinée au contenu (sidebar + nav flottante restent fixes). */
        min-height: 0;
        overflow-y: auto;
        /* Pas de gouttière ici : les barres de titre pleine largeur (ScreenTitleBar) doivent aller border-to-border.
           La gouttière (--page-gutter) est appliquée au CORPS de chaque page. + dégagement bas pour la barre flottante. */
        padding-bottom: 88px;
      }
      /* Barre de nav basse flottante, centrée horizontalement en bas de l'écran. */
      .bottomnav {
        position: fixed;
        bottom: var(--space-4);
        left: 50%;
        transform: translateX(-50%);
        z-index: 30;
        cursor: grab;
      }
      /* Déplacée par l'utilisateur : position libre (left/top), plus de centrage bas. */
      .bottomnav--free {
        bottom: auto;
        transform: none;
      }
      /* Snackbars : fixés en bas-centre, au-dessus de la barre flottante. */
      .snackbars {
        position: fixed;
        bottom: 92px;
        left: 50%;
        transform: translateX(-50%);
        width: min(460px, 92vw);
        z-index: 40;
      }
      .scrim {
        display: none;
      }

      @media (max-width: 900px) {
        .sidebar {
          position: fixed;
          top: 0;
          left: 0;
          bottom: 0;
          z-index: 20;
          transform: translateX(-100%);
          transition: transform 0.2s ease;
        }
        .shell--open .sidebar {
          transform: translateX(0);
        }
        .topbar {
          display: flex;
        }
        .shell--open .scrim {
          display: block;
          position: fixed;
          inset: 0;
          background: rgba(0, 0, 0, 0.5);
          z-index: 10;
        }
      }
    `,
  ],
})
export class AppShell {
  private readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  private readonly ws = inject(WebSocketService);
  private readonly sync = inject(SyncEngine);
  private readonly router = inject(Router);
  protected readonly snackbar = inject(SnackbarService);
  /** Pont hub Santé : slug actif (colonne centrée) + demande de centrage barre basse / drawer. */
  private readonly healthNav = inject(HealthNavService);

  protected readonly wsConnected = this.ws.connected;
  protected readonly sidebarOpen = signal(false);
  protected readonly online = signal(navigator.onLine);
  /** Drawer plié (rail icônes seules) — desktop uniquement. Fermé par défaut (gagne de la place). */
  protected readonly collapsed = signal(true);
  protected readonly mobile = signal(false);
  /** Pli effectif : seulement sur desktop (sur mobile le drawer reste off-canvas plein). */
  protected readonly effectiveCollapsed = computed(() => this.collapsed() && !this.mobile());

  /** URL courante (réactive) — pour dériver l'item actif du drawer + rejouer le reveal de contenu de route. */
  protected readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map(() => this.router.url),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );

  private readonly stats = toSignal(this.sync.allStats(), { initialValue: [] as SyncStats[] });
  /** Total des lignes non synchronisées (unsynced + pendingDeletion) sur toutes les entités. */
  protected readonly pending = computed(() =>
    this.stats().reduce((acc, s) => acc + s.unsynced + s.pendingDeletion, 0),
  );

  protected readonly sections: NavSection[] = [
    {
      title: 'Général',
      icon: 'dashboard',
      items: [
        { label: 'Accueil', icon: 'home', path: 'home' },
        { label: 'Notifications', icon: 'notifications', path: 'notifications' },
        { label: 'Routines', icon: 'checklist', path: 'routines' },
        { label: 'Citations', icon: 'book', path: 'quotes' },
      ],
    },
    {
      title: 'Sport',
      icon: 'fitness_center',
      items: [
        { label: 'Séance', icon: 'directions_run', path: 'seance' },
        { label: 'Calendrier & Objectifs', icon: 'calendar_month', path: 'calendar' },
        { label: 'Programme', icon: 'calendar_view_week', path: 'planning' },
        { label: 'Statistiques', icon: 'equalizer', path: 'stats' },
        { label: 'Matériel', icon: 'fitness_center', path: 'materiel' },
        { label: 'Exercices', icon: 'exercise', path: 'exercises' },
        { label: 'Muscles', icon: 'neurology', path: 'muscles' },
        { label: 'Chrono', icon: 'timer', path: 'chrono' },
      ],
    },
    {
      title: 'Nutrition',
      icon: 'restaurant',
      items: [
        { label: 'Journal', icon: 'today', path: 'nutrition' },
        { label: 'Objectifs', icon: 'flag', path: 'nutrition/goals' },
        { label: 'Statistiques', icon: 'insights', path: 'nutrition/stats' },
        { label: 'Catalogue', icon: 'grocery', path: 'nutrition/foods' },
        { label: 'Recettes & repas', icon: 'menu_book', path: 'nutrition/recipes' },
      ],
    },
    {
      title: 'Santé',
      icon: 'ecg_heart',
      // 1 item par section du hub Santé : navigue vers /health + centre la colonne (même mécanisme
      // que la barre basse). `path: 'health'` garde le rattachement de section (accordéon) et l'actif
      // est dérivé du slug (colonne centrée), pas de l'URL.
      items: HEALTH_SECTIONS.map((s) => ({
        label: s.title,
        icon: s.icon,
        path: 'health',
        slug: s.slug,
      })),
    },
    {
      title: 'Compte et Paramètres',
      icon: 'manage_accounts',
      items: [
        { label: 'Profil', icon: 'account_circle', path: 'profile' },
        { label: 'Réglages', icon: 'settings', path: 'settings' },
        { label: 'Synchro', icon: 'sync', path: 'sync' },
        { label: 'Design System', icon: 'palette', path: 'showcase' },
      ],
    },
  ];

  /** Chemins du drawer connaissant une route, pour dériver l'unique item actif (le plus spécifique). */
  private readonly drawerPaths = this.sections
    .flatMap((s) => s.items)
    .map((i) => i.path)
    .filter((p): p is string => !!p);

  /** Vue allégée des sections (titre + chemins de leurs items) pour le helper pur `sectionForUrl`. */
  private readonly sectionRefs: DrawerSectionRef[] = this.sections.map((s) => ({
    title: s.title,
    paths: s.items.map((i) => i.path).filter((p): p is string => !!p),
  }));

  /**
   * Sections dépliées du drawer (accordéon, en rail comme en déplié). Plusieurs peuvent être ouvertes
   * en même temps (toggles indépendants). À chaque navigation, on s'assure que la section de la route
   * courante est ouverte (cf. effet ci-dessous), sans refermer celles que l'utilisateur a ouvertes.
   * Le clic sur l'en-tête (titre ou icône-titre rail) bascule la section. Seule exception à
   * l'add-only : la bascule de mode (`toggleMode`) recale l'accordéon sur LA section du mode choisi.
   */
  protected readonly openSections = signal<ReadonlySet<string>>(
    new Set([sectionForUrl(this.router.url, this.sectionRefs)]),
  );

  /**
   * Drapeau one-shot : la navigation induite par `toggleMode` (vers l'accueil du mode) ne doit PAS
   * ré-ouvrir la section d'atterrissage via l'effet route→section — sinon Sport → Accueil (∈ « Général »)
   * rouvrirait Général par-dessus Sport. Armé avant la nav, consommé par le prochain passage de l'effet.
   */
  private suppressNextSectionSync = false;

  /**
   * Mode de navigation par domaine (Sport/Nutrition) qui pilote la barre basse.
   * Persisté en localStorage, mais SUIT la page : l'effet ci-dessous le recale sur l'URL courante.
   */
  protected readonly mode = signal<NavMode>(readNavMode());

  /** Barre de nav basse — items dépendants du mode ; le 1er slot est la bascule de mode. */
  protected readonly bottomNav = computed<BottomNavItemData[]>(() => bottomNavForMode(this.mode()));
  /** Item de la barre basse correspondant à la route courante ('' si aucun). */
  /** Accent couleur de la barre basse selon le mode (bleu Sport / turquoise Nutrition). */
  protected readonly navAccentColor = computed(() => accentColorForMode(this.mode()));
  protected readonly navAccentText = computed(() => accentTextForMode(this.mode()));

  protected readonly bottomNavSelected = computed(() => {
    // Mode Santé : l'actif n'est pas dérivé de l'URL mais de la colonne centrée dans le rail
    // (publiée par la page via HealthNavService) → suit le scroll manuel comme les clics.
    if (this.mode() === 'HEALTH') return healthSectionValue(this.healthNav.activeSlug());
    return bottomNavSelectedValue(this.currentUrl(), this.bottomNav());
  });

  // --- Bottom nav déplaçable (position libre persistée) : null = défaut (bas-centre). ---
  protected readonly navPos = signal<{ left: number; top: number } | null>(readNavPos());
  private navDrag: { x: number; y: number; left: number; top: number; w: number; h: number; moved: boolean } | null =
    null;
  /** Vrai juste après un déplacement → ignore le clic de nav qui suivrait (sinon il navigue). */
  private navJustDragged = false;

  protected onNavDown(e: PointerEvent): void {
    const r = (e.currentTarget as HTMLElement).getBoundingClientRect();
    this.navDrag = { x: e.clientX, y: e.clientY, left: r.left, top: r.top, w: r.width, h: r.height, moved: false };
  }

  @HostListener('document:pointermove', ['$event'])
  onNavMove(e: PointerEvent): void {
    const d = this.navDrag;
    if (!d) return;
    const dx = e.clientX - d.x;
    const dy = e.clientY - d.y;
    if (!d.moved && Math.hypot(dx, dy) < 4) return; // seuil : distingue un clic d'un déplacement
    d.moved = true;
    e.preventDefault();
    const left = Math.max(0, Math.min(window.innerWidth - d.w, d.left + dx));
    const top = Math.max(0, Math.min(window.innerHeight - d.h, d.top + dy));
    this.navPos.set({ left, top });
  }

  @HostListener('document:pointerup')
  onNavUp(): void {
    if (this.navDrag?.moved) {
      this.navJustDragged = true;
      writeNavPos(this.navPos());
      setTimeout(() => (this.navJustDragged = false), 0);
    }
    this.navDrag = null;
  }

  constructor() {
    window.addEventListener('online', () => this.online.set(true));
    window.addEventListener('offline', () => this.online.set(false));
    const mq = window.matchMedia('(max-width: 900px)');
    this.mobile.set(mq.matches);
    mq.addEventListener('change', (e) => this.mobile.set(e.matches));
    if (this.auth.isAuthenticated() && !this.auth.currentUser()) {
      this.auth.loadMe().subscribe({ error: () => undefined });
    }
    // Le mode suit la page : à chaque navigation, on le recale sur l'URL (et on persiste).
    effect(() => {
      const next = modeForUrl(this.currentUrl());
      if (next !== this.mode()) this.setMode(next);
    });
    // Accordéon drawer : à chaque navigation, s'assurer que la section de la route courante est
    // ouverte (sans toucher aux autres que l'utilisateur a ouvertes/fermées).
    // `untracked` : l'effet ne réagit qu'à l'URL, pas à openSections — sinon un clic manuel
    // (toggleSection) relancerait l'effet en boucle.
    effect(() => {
      const next = sectionForUrl(this.currentUrl(), this.sectionRefs);
      untracked(() => {
        // Après une bascule de mode : ce seul passage est neutralisé (SEULE la section du mode
        // reste ouverte). Les navigations ordinaires restent add-only.
        if (this.suppressNextSectionSync) {
          this.suppressNextSectionSync = false;
          return;
        }
        if (!this.openSections().has(next)) {
          this.openSections.set(new Set(this.openSections()).add(next));
        }
      });
    });
  }

  /** Bascule manuelle d'une section de l'accordéon : l'ouvre ou la referme (indépendamment des autres). */
  protected toggleSection(title: string): void {
    this.openSections.update((cur) => {
      const next = new Set(cur);
      if (next.has(title)) next.delete(title);
      else next.add(title);
      return next;
    });
  }

  private setMode(mode: NavMode): void {
    this.mode.set(mode);
    writeNavMode(mode);
  }

  /** Bascule manuelle du mode : cycle vers le mode suivant et navigue vers sa page d'accueil. */
  protected toggleMode(): void {
    const target = nextMode(this.mode());
    this.setMode(target);
    // Bascule de mode = SEUL cas de reset de l'accordéon du drawer : ouvre la section du mode,
    // referme toutes les autres. SEULE cette section reste ouverte : on neutralise le prochain
    // effet route→section (sinon la nav vers l'accueil du mode rouvrirait la section d'atterrissage,
    // ex. Sport → « Général » pour l'Accueil).
    this.openSections.set(new Set([sectionForMode(target)]));
    const targetUrl = homeRouteForMode(target);
    if (this.currentUrl() !== targetUrl) {
      this.suppressNextSectionSync = true;
      void this.router.navigateByUrl(targetUrl);
    }
    this.close();
  }

  protected isActive(item: NavItem): boolean {
    // Item de section Santé : actif = sur /health ET colonne centrée = sa section (suit le scroll,
    // pas l'URL — plusieurs items partagent le path 'health').
    if (item.slug !== undefined) {
      return modeForUrl(this.currentUrl()) === 'HEALTH' && this.healthNav.activeSlug() === item.slug;
    }
    if (!item.path) return false;
    // Le chemin le plus spécifique gagne (/nutrition/goals prime sur /nutrition).
    return item.path === longestMatchingValue(this.currentUrl(), this.drawerPaths);
  }

  protected onItem(item: NavItem): void {
    // Item de section Santé : ouvre /health et centre la colonne (même mécanisme que la barre basse).
    if (item.slug !== undefined) {
      this.goToHealthSection(item.slug);
      return;
    }
    void this.router.navigateByUrl('/' + item.path);
    this.close();
  }

  protected onBottomNav(value: string): void {
    if (this.navJustDragged) return; // un déplacement de la barre ne déclenche pas la navigation
    if (value === MENU_VALUE) {
      this.onMenu();
      return;
    }
    if (value === MODE_TOGGLE_VALUE) {
      this.toggleMode();
      return;
    }
    // Bouton de section Santé (`health#<slug>`) : centre la colonne au lieu de naviguer vers une route.
    const slug = healthSectionSlug(value);
    if (slug !== null) {
      this.goToHealthSection(slug);
      return;
    }
    void this.router.navigateByUrl('/' + value);
    this.close();
  }

  /**
   * Centre la colonne `slug` du hub Santé (clic barre basse / item drawer). Demande le centrage
   * immédiat (la page, si montée, réagit au signal) puis pose le fragment `/health#<slug>` pour une URL
   * deep-linkable : navigation réelle si on vient d'ailleurs, simple mise à jour du fragment (sans
   * empiler l'historique) si on est déjà sur /health.
   */
  private goToHealthSection(slug: string): void {
    this.healthNav.requestScroll(slug);
    if (modeForUrl(this.currentUrl()) === 'HEALTH') {
      void this.router.navigate([], { fragment: slug, replaceUrl: true });
    } else {
      void this.router.navigate(['/health'], { fragment: slug });
    }
    this.close();
  }

  /** Bouton Menu de la barre basse : mobile → ouvre l'off-canvas ; desktop → plie/déplie le rail. */
  protected onMenu(): void {
    if (this.mobile()) this.toggle();
    else this.collapsed.update((v) => !v);
  }

  protected toggle(): void {
    this.sidebarOpen.update((v) => !v);
  }
  protected close(): void {
    this.sidebarOpen.set(false);
  }
  protected triggerSync(): void {
    void this.sync.syncAll().catch(() => undefined);
  }
  /** Déconnexion depuis le footer du drawer (séparée des items de section). */
  protected logout(): void {
    this.close();
    this.auth.logout();
    void this.router.navigateByUrl('/login');
  }
}
