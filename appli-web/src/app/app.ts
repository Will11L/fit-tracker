import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ThemeService } from '@designsystem/theme/theme.service';
import { WebSocketService } from '@core/sync/ws.service';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  // Instanciés au boot : ThemeService applique le thème persisté ; WebSocketService
  // gère le temps réel (connecte/coupe selon l'état d'authentification).
  private readonly theme = inject(ThemeService);
  private readonly ws = inject(WebSocketService);
}
