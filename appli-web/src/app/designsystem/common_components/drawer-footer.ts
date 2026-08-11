import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Pied du drawer — miroir de DrawerFooter.kt / Figma M15 : ligne (padding 8/16, gap 10) avec
 * un texte d'état à gauche (12px Regular, textTertiary) et un slot `[trailing]` d'icônes de statut
 * à droite (réseau / cloud / WS). Bordure haute divider-strong.
 */
@Component({
  selector: 'app-drawer-footer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="df">
      <span class="df__text">{{ text() }}</span>
      <span class="df__trailing"><ng-content select="[trailing]" /></span>
    </div>
  `,
  styles: [
    `
      .df {
        display: flex;
        align-items: center;
        gap: 10px;
        box-sizing: border-box;
        padding: var(--space-2) var(--space-4);
        border-top: 1.5px solid var(--app-divider-strong);
      }
      .df__text {
        flex: 1;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        color: var(--app-text-tertiary);
        font-size: var(--font-size-caption);
      }
      .df__trailing {
        flex-shrink: 0;
        display: inline-flex;
        align-items: center;
        gap: var(--space-2);
      }
    `,
  ],
})
export class DrawerFooter {
  readonly text = input('');
}
