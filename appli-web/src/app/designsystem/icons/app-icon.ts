import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Icône Material Symbols Outlined (police) — même type d'icône outlined que l'app Android.
 * `name` = ligature Material Symbols (ex. 'add', 'delete', 'cloud_done'). Police chargée dans index.html.
 * Pendant web de `Icon(painterResource(...), tint=...)` côté Compose.
 */
@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="ms" [style.font-size.px]="size()" [style.color]="color()">{{ name() }}</span>`,
  styles: [
    `
      :host {
        display: inline-flex;
        line-height: 0;
      }
      .ms {
        font-family: 'Material Symbols Outlined';
        font-weight: normal;
        font-style: normal;
        line-height: 1;
        letter-spacing: normal;
        white-space: nowrap;
        direction: ltr;
        font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
        -webkit-font-feature-settings: 'liga';
        font-feature-settings: 'liga';
        -webkit-font-smoothing: antialiased;
        user-select: none;
      }
    `,
  ],
})
export class AppIcon {
  /** Ligature Material Symbols (= nom de l'icône). */
  readonly name = input('');
  readonly size = input(24);
  readonly color = input('currentColor');
}
