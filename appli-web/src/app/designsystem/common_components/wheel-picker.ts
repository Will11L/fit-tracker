import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';

/**
 * Roue de sélection verticale — miroir de WheelPicker.kt : liste scrollable snap-to-center
 * (hauteur = itemHeight × visibleItems), valeur centrale surlignée, bande centrale translucide.
 * Commit (selectedChange) à l'arrêt du scroll. Valeurs formatées "%02d".
 *
 * Déviation : Android boucle à l'infini (wrap-around) ; ici liste finie (le wrap était
 * surtout cosmétique). Le snap est géré par CSS scroll-snap.
 */
@Component({
  selector: 'app-wheel-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="wp" [style.height.px]="itemHeight() * visibleItems()">
      <div #scroll class="wp__scroll" [style.padding-block.px]="padPx()" (scroll)="onScroll()">
        @for (v of values(); track v) {
          <div class="wp__item" [style.height.px]="itemHeight()" [class.wp__item--center]="v === centeredValue()">
            {{ pad2(v) }}
          </div>
        }
      </div>
      <div class="wp__band" [style.height.px]="itemHeight()"></div>
    </div>
  `,
  styles: [
    `
      .wp {
        position: relative;
        width: 100%;
        border-radius: var(--radius-md);
        /* Fente « creusée » : fond recessed (thirdBlue) plein → bien marqué dans le thème. */
        background: var(--app-bg-recessed);
        overflow: hidden;
      }
      .wp__scroll {
        height: 100%;
        overflow-y: scroll;
        scroll-snap-type: y mandatory;
        scrollbar-width: none;
        -ms-overflow-style: none;
        /* Dégradé de profondeur (roue physique) : valeurs nettes au centre, estompées vers les bords. */
        -webkit-mask-image: linear-gradient(to bottom, transparent, #000 30%, #000 70%, transparent);
        mask-image: linear-gradient(to bottom, transparent, #000 30%, #000 70%, transparent);
      }
      .wp__scroll::-webkit-scrollbar {
        display: none;
      }
      .wp__item {
        display: flex;
        align-items: center;
        justify-content: center;
        scroll-snap-align: center;
        /* Valeurs non sélectionnées en gris-bleu (au lieu d'un gris neutre). */
        color: var(--c-gray-blue);
        font-size: var(--font-size-body);
      }
      .wp__item--center {
        /* Valeur sélectionnée dans le bleu primaire de l'app (comme les autres sélections). */
        color: var(--app-primary-action);
        font-size: var(--font-size-subtitle);
        font-weight: 600;
      }
      .wp__band {
        position: absolute;
        left: 0;
        right: 0;
        top: 50%;
        transform: translateY(-50%);
        box-sizing: border-box;
        /* Fente de sélection teintée bleu + filets bleus haut/bas (façon picker iOS, dans le thème). */
        background: color-mix(in srgb, var(--app-primary-action) 14%, transparent);
        border-top: 1px solid color-mix(in srgb, var(--app-primary-action) 35%, transparent);
        border-bottom: 1px solid color-mix(in srgb, var(--app-primary-action) 35%, transparent);
        pointer-events: none;
      }
    `,
  ],
})
export class WheelPicker {
  readonly min = input(0);
  readonly max = input(59);
  readonly selected = input(0);
  readonly itemHeight = input(40);
  readonly visibleItems = input(5);
  readonly selectedChange = output<number>();

  protected readonly values = computed(() => {
    const out: number[] = [];
    for (let v = this.min(); v <= this.max(); v++) out.push(v);
    return out;
  });
  protected readonly padPx = computed(() => this.itemHeight() * Math.floor(this.visibleItems() / 2));
  protected readonly centeredValue = signal(0);

  private readonly scrollEl = viewChild.required<ElementRef<HTMLDivElement>>('scroll');
  private commitTimer: ReturnType<typeof setTimeout> | null = null;
  private lastSelected = this.selected();

  constructor() {
    afterNextRender(() => this.scrollToSelected());
    // Re-positionne la roue quand `selected` change de l'extérieur (réouverture d'un picker sur une
    // autre valeur, reset…) — pas pendant le scroll utilisateur (la valeur émise est déjà affichée).
    effect(() => {
      const sel = this.selected();
      if (sel === this.lastSelected) return;
      this.lastSelected = sel;
      this.scrollToSelected();
    });
  }

  /** Centre la roue sur `selected` ; le scroll programmatique recale juste l'affichage (pas de ré-émission). */
  private scrollToSelected(): void {
    this.centeredValue.set(this.selected());
    this.scrollEl().nativeElement.scrollTop = (this.selected() - this.min()) * this.itemHeight();
  }

  protected pad2(v: number): string {
    return v.toString().padStart(2, '0');
  }

  protected onScroll(): void {
    const el = this.scrollEl().nativeElement;
    const vals = this.values();
    const idx = Math.max(0, Math.min(Math.round(el.scrollTop / this.itemHeight()), vals.length - 1));
    const v = vals[idx];
    if (v !== this.centeredValue()) this.centeredValue.set(v);
    if (this.commitTimer) clearTimeout(this.commitTimer);
    this.commitTimer = setTimeout(() => {
      if (v !== this.selected()) this.selectedChange.emit(v);
    }, 140);
  }
}
