import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Spacer horizontal de largeur paramétrable (default 6px), remplit la hauteur du parent.
 * Miroir de CustomSpacer.kt (ex-CustomVerticalDivider, renommé côté Android car c'est un spacer
 * transparent, pas un divider).
 */
@Component({
  selector: 'app-custom-spacer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '',
  host: {
    '[style.display]': '"inline-block"',
    '[style.alignSelf]': '"stretch"',
    '[style.width.px]': 'width()',
  },
})
export class CustomSpacer {
  readonly width = input(6);
}
