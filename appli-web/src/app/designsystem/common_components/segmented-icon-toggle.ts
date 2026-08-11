import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { SegmentedIconButton } from '@designsystem/common_components/segmented-icon-button';

/** Un segment de SegmentedIconToggle (= SegmentItem.kt). `value` = clé string (l'enum Android côté wire). */
export interface SegmentItem {
  value: string;
  icon: string;
  description: string;
}

/**
 * Toggle segmenté — miroir de SegmentedIconToggle.kt : rangée de SegmentedIconButton (gap 6px),
 * un seul segment sélectionné. (Android est générique `<T>` ; côté web on utilise une clé `string`.)
 */
@Component({
  selector: 'app-segmented-icon-toggle',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SegmentedIconButton],
  template: `
    <div class="sit">
      @for (item of items(); track item.value) {
        <app-segmented-icon-button
          [selected]="item.value === selected()"
          [icon]="item.icon"
          [description]="item.description"
          [width]="width()"
          [iconSize]="iconSize()"
          [unselectedBorderColor]="unselectedBorderColor()"
          (clicked)="select.emit(item.value)"
        />
      }
    </div>
  `,
  styles: [
    `
      .sit {
        display: flex;
        align-items: center;
        gap: 6px;
      }
    `,
  ],
})
export class SegmentedIconToggle {
  readonly items = input<SegmentItem[]>([]);
  readonly selected = input('');
  /** Côté du carré de chaque segment (largeur = hauteur). Défaut 34px (footprint ActionIconButton). */
  readonly width = input(34);
  readonly iconSize = input(18);
  readonly unselectedBorderColor = input('color-mix(in srgb, var(--app-text-secondary) 60%, transparent)');
  readonly select = output<string>();
}
