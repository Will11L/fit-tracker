import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  signal,
} from '@angular/core';
import { ringGeometry } from './progress-ring';

/** Un anneau de la pile concentrique : progression 0..1, couleur (token), épaisseur. */
export interface ConcentricRing {
  /** Progression 0..1 (bornée par la géométrie). */
  progress: number;
  /** Couleur du trait rempli (token, jamais de M3 brut). */
  color: string;
  /** Épaisseur du trait (px). */
  width: number;
  /** Étiquette optionnelle (ligne de rappel radiale + texte « en étoile »). Vide/absent = pas d'étiquette. */
  label?: string;
}

/** Géométrie calculée d'un anneau de la pile (rayon décroissant du plus extérieur au plus intérieur). */
export interface RingView {
  radius: number;
  circumference: number;
  offset: number;
  color: string;
  width: number;
  label?: string;
}

/**
 * Géométrie pure de la pile d'anneaux — testable sans DOM. On part du bord (le 1er anneau occupe son
 * épaisseur) puis on descend vers le centre en retranchant épaisseur + gap à chaque anneau suivant.
 * `ringGeometry` (partagé avec ProgressRing) fournit le dasharray pour un anneau de diamètre donné.
 * S'arrête si le diamètre devient ≤ 0 (plus de place au centre).
 */
export function concentricRingViews(size: number, gap: number, rings: ConcentricRing[]): RingView[] {
  const views: RingView[] = [];
  let outer = size; // diamètre extérieur du prochain anneau
  for (const ring of rings) {
    if (outer <= 0) break;
    const geo = ringGeometry(outer, ring.width, ring.progress);
    views.push({
      radius: geo.radius,
      circumference: geo.circumference,
      offset: geo.offset,
      color: ring.color,
      width: ring.width,
      label: ring.label,
    });
    outer -= 2 * (ring.width + gap);
  }
  return views;
}

/** Géométrie d'une étiquette « en étoile » : ligne de rappel (polyline) + position/ancre du texte. */
export interface RingLabelView {
  /** Points de la polyline « x,y x,y x,y » : point sur l'anneau → coude radial → segment horizontal. */
  points: string;
  textX: number;
  textY: number;
  anchor: 'start' | 'end';
  color: string;
  text: string;
  /** Lignes du libellé (split sur '\n') — rendues en <tspan> empilés, centrés sur textY. */
  lines: string[];
}

/**
 * Géométrie des étiquettes « en étoile » (façon libellés du donut) — pure, testable sans DOM. Les
 * anneaux étiquetés sont répartis sur des angles réguliers (départ en haut, sens horaire) ; la ligne
 * part du point sur SON anneau, sort radialement juste au-delà de l'anneau extérieur, puis un court
 * segment horizontal vers le texte (ancré à gauche/droite selon le côté).
 */
export function concentricLabelViews(
  cx: number,
  cy: number,
  outerRadius: number,
  ringViews: RingView[],
): RingLabelView[] {
  const labeled = ringViews.filter((v) => v.label != null && v.label !== '');
  const n = labeled.length;
  // Longueur radiale du trait au-delà de l'anneau extérieur avant le coude : un peu d'éloignement
  // pour que les étiquettes respirent par rapport aux anneaux (les marges du viewBox suivent).
  const LEADER_OUT = 14;
  const HSEG = 10;
  const TEXT_GAP = 3;
  return labeled.map((v, k) => {
    const angle = -Math.PI / 2 + (k * 2 * Math.PI) / n;
    const cos = Math.cos(angle);
    const sin = Math.sin(angle);
    const ax = cx + v.radius * cos;
    const ay = cy + v.radius * sin;
    const ex = cx + (outerRadius + LEADER_OUT) * cos;
    const ey = cy + (outerRadius + LEADER_OUT) * sin;
    const dir = cos >= 0 ? 1 : -1;
    const hx = ex + dir * HSEG;
    return {
      points: `${ax},${ay} ${ex},${ey} ${hx},${ey}`,
      textX: hx + dir * TEXT_GAP,
      textY: ey,
      anchor: dir > 0 ? 'start' : 'end',
      color: v.color,
      text: v.label as string,
      lines: (v.label as string).split('\n'),
    };
  });
}

/**
 * N anneaux concentriques (SVG, stroke-dasharray) empilés du plus extérieur au plus intérieur —
 * primitif réutilisable du Design System. Chaque anneau a sa progression / couleur / épaisseur ;
 * un creux (trough) gris derrière chaque anneau matérialise la cible non atteinte. Centre libre
 * pour un label projeté par le consommateur (slot `<ng-content>`). Pendant multi-anneaux de
 * ProgressRing : utilisé par les cases du calendrier Nutrition (4 anneaux kcal/G/L/P).
 *
 * Étiquettes optionnelles « en étoile » : si un anneau porte un `label`, le composant passe en mode
 * fluide (occupe la largeur dispo, plafonnée + centrée) et trace une ligne de rappel radiale vers
 * chaque étiquette (façon libellés du donut). Sans `label`, rendu fixe inchangé (cases du calendrier).
 */
@Component({
  selector: 'app-concentric-rings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class.crings-host--labeled]': 'hasLabels()',
    '[class.crings-host--fit]': 'fitHeight()',
  },
  template: `
    <div class="crings" [class.crings--labeled]="hasLabels()" [style.width]="boxWidth()" [style.height]="boxHeight()">
      <svg
        [class.crings__svg--fluid]="hasLabels()"
        [class.crings__svg--fit]="fitHeight()"
        [attr.width]="hasLabels() ? null : size()"
        [attr.height]="hasLabels() ? null : size()"
        [attr.viewBox]="'0 0 ' + vbWidth() + ' ' + vbHeight()"
      >
        @for (r of ringViews(); track $index) {
          <circle
            [attr.cx]="centerX()"
            [attr.cy]="centerY()"
            [attr.r]="r.radius"
            fill="none"
            [attr.stroke]="troughColor()"
            [attr.stroke-width]="r.width"
          />
          <circle
            class="crings__fill"
            [attr.cx]="centerX()"
            [attr.cy]="centerY()"
            [attr.r]="r.radius"
            fill="none"
            [attr.stroke]="r.color"
            [attr.stroke-width]="r.width"
            stroke-linecap="round"
            [attr.stroke-dasharray]="r.circumference"
            [attr.stroke-dashoffset]="r.offset"
            [attr.transform]="'rotate(-90 ' + centerX() + ' ' + centerY() + ')'"
          />
        }
        @for (l of labelViews(); track $index) {
          <polyline class="crings__leader" [attr.points]="l.points" fill="none" [attr.stroke]="l.color" />
          <!-- Multi-lignes (valeur SOUS le nom) : tspans empilés, bloc centré sur textY. -->
          <text
            class="crings__label"
            [attr.x]="l.textX"
            [attr.y]="l.textY"
            [attr.text-anchor]="l.anchor"
            dominant-baseline="middle"
            [attr.fill]="l.color"
          >@for (line of l.lines; track $index) {<tspan
              [attr.x]="l.textX"
              [attr.dy]="$index === 0 ? (l.lines.length > 1 ? '-0.55em' : '0') : '1.1em'"
            >{{ line }}</tspan>}</text>
        }
      </svg>
      <div class="crings__center">
        <ng-content />
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
      }
      /* Mode étiqueté (« en étoile ») : le composant devient fluide (occupe la largeur dispo, plafonnée)
         et se centre, pour que les étiquettes ne soient jamais rognées quelle que soit la colonne. */
      :host(.crings-host--labeled) {
        display: block;
        width: 100%;
        max-width: 360px;
      }
      /* Mode « fitHeight » : s'étire en hauteur dans un conteneur flex → fournit une hauteur définie
         au SVG pour qu'il se réduise (au lieu de déborder) quand la place verticale est courte. */
      :host(.crings-host--fit) {
        align-self: stretch;
      }
      .crings {
        position: relative;
        display: inline-flex;
      }
      .crings--labeled {
        display: block;
        width: 100%;
      }
      .crings svg {
        display: block;
        /* Les étiquettes « en étoile » peuvent dépasser le viewBox (libellés longs) : on autorise le
           débordement pour qu'elles utilisent la place du conteneur autour des anneaux, au lieu d'être
           coupées au bord du viewBox (ex. « Glucides 4% » tronqué alors qu'il reste de la place). */
        overflow: visible;
      }
      .crings__svg--fluid {
        width: 100%;
        height: auto;
      }
      /* Mode fitHeight : le SVG remplit son conteneur (à hauteur définie) ; son contenu est mis à
         l'échelle + centré (preserveAspectRatio « meet » par défaut) → il tient toujours dans la place
         verticale, sans déborder (pas de scrollbar) et sans être décalé vers le haut. */
      .crings__svg--fit {
        height: 100%;
      }
      /* Remplissage animé 0 → valeur (apparition + changements) via les tokens de motion. */
      .crings__fill {
        transition: stroke-dashoffset var(--motion-base) var(--motion-ease);
      }
      /* a11y : aucune animation si l'utilisateur a demandé moins de mouvement. */
      @media (prefers-reduced-motion: reduce) {
        .crings__fill {
          transition: none;
        }
      }
      /* Ligne de rappel (« trait qui cible l'anneau ») : fine, légèrement atténuée, couleur du macro. */
      .crings__leader {
        stroke-width: 1;
        opacity: 0.7;
      }
      .crings__label {
        font-size: 13px;
        font-weight: 600;
        font-variant-numeric: tabular-nums;
      }
      .crings__center {
        position: absolute;
        inset: 0;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        text-align: center;
        line-height: 1.05;
        font-variant-numeric: tabular-nums;
        pointer-events: none;
      }
    `,
  ],
})
export class ConcentricRings {
  /** Côté de la boîte carrée (px) — diamètre de la pile d'anneaux. */
  readonly size = input(64);
  /** Anneaux du plus EXTÉRIEUR (index 0) au plus intérieur. */
  readonly rings = input.required<ConcentricRing[]>();
  /** Couleur du creux derrière chaque anneau (cible non atteinte). */
  readonly troughColor = input('var(--app-bg-surface)');
  /** Espace entre deux anneaux consécutifs (px). */
  readonly gap = input(2);
  /** Mode étiqueté uniquement : se réduit pour tenir dans la hauteur du conteneur (parent flex à hauteur
   *  définie) au lieu d'être piloté seulement par la largeur. Off par défaut (rétro-compatible). */
  readonly fitHeight = input(false);

  /** Faux jusqu'au 1er paint : on rend les anneaux vides d'abord, puis les vraies valeurs → transition part de 0. */
  private readonly mounted = signal(false);

  /** Marge (unités viewBox) réservée au texte des étiquettes en mode « étoile » (horizontale / verticale).
   *  Élargie pour suivre le trait de rappel allongé (LEADER_OUT) → les libellés gardent leur place sans
   *  être rognés malgré l'éloignement accru. */
  private readonly LABEL_MARGIN_X = 90;
  private readonly LABEL_MARGIN_Y = 32;

  /** Vrai dès qu'au moins un anneau porte une étiquette → mode étiqueté (fluide + lignes de rappel). */
  protected readonly hasLabels = computed(() =>
    this.rings().some((r) => r.label != null && r.label !== ''),
  );

  /** viewBox : carré = taille des anneaux ; étiqueté = taille + marges pour le texte. */
  protected readonly vbWidth = computed(() =>
    this.hasLabels() ? this.size() + 2 * this.LABEL_MARGIN_X : this.size(),
  );
  protected readonly vbHeight = computed(() =>
    this.hasLabels() ? this.size() + 2 * this.LABEL_MARGIN_Y : this.size(),
  );
  protected readonly centerX = computed(() => this.vbWidth() / 2);
  protected readonly centerY = computed(() => this.vbHeight() / 2);

  /** Style de la boîte : fixe (px) sans étiquette (calendrier), fluide (100% / hauteur auto) étiqueté. */
  protected readonly boxWidth = computed(() => (this.hasLabels() ? '100%' : `${this.size()}px`));
  protected readonly boxHeight = computed(() =>
    this.hasLabels() ? (this.fitHeight() ? '100%' : 'auto') : `${this.size()}px`,
  );

  /** Géométrie de chaque anneau (rayon décroissant, dasharray) — délègue à la fonction pure. */
  protected readonly ringViews = computed<RingView[]>(() => {
    const rings = this.mounted() ? this.rings() : this.rings().map((r) => ({ ...r, progress: 0 }));
    return concentricRingViews(this.size(), this.gap(), rings);
  });

  /** Étiquettes « en étoile » (lignes de rappel + texte), seulement si des étiquettes sont fournies. */
  protected readonly labelViews = computed<RingLabelView[]>(() =>
    this.hasLabels()
      ? concentricLabelViews(this.centerX(), this.centerY(), this.size() / 2, this.ringViews())
      : [],
  );

  constructor() {
    afterNextRender(() => this.mounted.set(true));
  }
}
