import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  input,
  output,
  viewChild,
} from '@angular/core';

/**
 * Sélecteur numérique horizontal — miroir de HorizontalNumberPicker.kt : rangée scrollable de
 * cases (itemSize), sélectionnée = fond primaryAction (texte +2 gras). Si `targetMin/targetMax`
 * fournis : valeurs hors plage recommandée en rouge atténué. Clic = sélection + scroll au centre.
 */
@Component({
  selector: 'app-horizontal-number-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (label()) {
      <span class="hnp__label">{{ label() }}</span>
    }
    <div #scroll class="hnp" [style.height.px]="itemSize() + 16">
      @for (v of values(); track v) {
        <button
          class="hnp__box"
          [style.width.px]="itemSize()"
          [style.height.px]="itemSize()"
          [style.background]="boxBg(v)"
          (click)="choose(v)"
        >
          <span
            class="hnp__num"
            [class.hnp__num--selected]="v === selected()"
            [style.font-size.px]="v === selected() ? fontSize() + 2 : fontSize()"
            >{{ v }}</span
          >
        </button>
      }
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        width: 100%;
      }
      .hnp__label {
        display: block;
        color: var(--app-text-tertiary);
        font-size: var(--font-size-caption);
        padding-left: 2px;
        margin-bottom: var(--space-1);
      }
      .hnp {
        display: flex;
        gap: 6px;
        width: 100%;
        overflow-x: auto;
        scrollbar-width: none;
        -ms-overflow-style: none;
        align-items: center;
      }
      .hnp::-webkit-scrollbar {
        display: none;
      }
      .hnp__box {
        flex: 0 0 auto;
        display: flex;
        align-items: center;
        justify-content: center;
        border: none;
        border-radius: var(--radius-md);
        cursor: pointer;
        padding: 0;
        appearance: none;
        -webkit-appearance: none;
      }
      .hnp__num {
        color: var(--app-text-primary);
        font-weight: var(--font-weight-medium);
      }
      .hnp__num--selected {
        font-weight: var(--font-weight-bold);
      }
    `,
  ],
})
export class HorizontalNumberPicker {
  readonly min = input(0);
  readonly max = input(20);
  readonly selected = input(0);
  readonly targetMin = input<number | null>(null);
  readonly targetMax = input<number | null>(null);
  readonly itemSize = input(40);
  readonly fontSize = input(16);
  readonly label = input('');
  readonly scrollOnSelect = input(true);
  readonly selectedChange = output<number>();

  protected readonly values = computed(() => {
    const out: number[] = [];
    for (let v = this.min(); v <= this.max(); v++) out.push(v);
    return out;
  });

  private readonly scrollEl = viewChild.required<ElementRef<HTMLDivElement>>('scroll');

  constructor() {
    afterNextRender(() => {
      if (this.scrollOnSelect()) this.scrollToValue(this.selected());
    });
  }

  protected boxBg(v: number): string {
    if (v === this.selected()) return 'var(--app-primary-action)';
    const tMin = this.targetMin();
    const tMax = this.targetMax();
    if (tMin === null || tMax === null) return 'var(--app-bg-recessed)';
    if (v >= tMin && v <= tMax) return 'var(--app-bg-recessed)';
    return 'color-mix(in srgb, var(--c-red-medium) 50%, transparent)';
  }

  protected choose(v: number): void {
    this.selectedChange.emit(v);
    if (this.scrollOnSelect()) this.scrollToValue(v);
  }

  private scrollToValue(v: number): void {
    const el = this.scrollEl().nativeElement;
    const boxFull = this.itemSize() + 6;
    const target = (v - this.min()) * boxFull - (el.clientWidth - this.itemSize()) / 2;
    el.scrollTo({ left: Math.max(0, target), behavior: 'smooth' });
  }
}
