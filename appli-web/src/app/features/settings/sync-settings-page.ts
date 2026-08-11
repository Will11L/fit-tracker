import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { SyncEngine } from '@core/sync/sync-engine';
import { SYNCABLE_STORES, SyncRow, SyncStats, SyncableStore } from '@core/sync/syncable-store';
import { WebSocketService } from '@core/sync/ws.service';
import { SnackbarService } from '@core/snackbar/snackbar.service';
import { Router } from '@angular/router';
import { ActionIconWithTextButton } from '@designsystem/common_components/action-icon-with-text-button';
import { AppIcon } from '@designsystem/icons/app-icon';
import { DataGridPaginationBar } from '@designsystem/common_components/data-grid-pagination-bar';
import { DataTable } from '@designsystem/common_components/data-table';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';

type Op = 'sync' | 'push' | 'merge' | 'getall' | 'clear' | 'token';

/**
 * Page Sync Settings — miroir web de SyncSettingsScreen (Android), **flat master-detail** : la liste
 * des tables (à gauche) + le data grid de la table sélectionnée dans un panneau à droite (qui peut
 * s'étendre sur toute la hauteur de page). Au lieu de la sous-page Android, tout est visible d'un coup.
 * Sert à vérifier la réception réelle des données + outils de test du moteur offline <-> serveur.
 */
@Component({
  selector: 'app-sync-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ActionIconWithTextButton, AppIcon, DataGridPaginationBar, DataTable, TitledDivider, ActionIconButton, ScreenTitleBar],
  template: `
    <section class="sync">
      <app-screen-title-bar title="Synchronisation" />

      <div class="sync__body">
        <!-- Outils sync : miroir des 9 boutons de SyncSettingsScreen.kt, regroupés par couleur (4 bleus + rouge / 4 verts). -->
        <app-titled-divider title="Outils de synchronisation" />
        <div class="tools">
          <!-- Ligne 1 : 4 boutons bleus (récupérer / pousser / synchroniser / fusionner) + bouton rouge destructif. -->
          <div class="tools__row">
            <app-action-icon-with-text-button icon="cloud_download" text="Tout récupérer" [fullWidth]="true" [disabled]="busy()" (clicked)="run('getall')" />
            <app-action-icon-with-text-button icon="cloud_upload" text="Tout pousser" [fullWidth]="true" [disabled]="busy()" (clicked)="run('push')" />
            <app-action-icon-with-text-button icon="cloud_sync" text="Tout synchroniser" [fullWidth]="true" [disabled]="busy()" (clicked)="run('sync')" />
            <app-action-icon-with-text-button icon="cloud_download" text="Fusionner" [fullWidth]="true" [disabled]="busy()" (clicked)="run('merge')" />
            <app-action-icon-with-text-button
              icon="delete_sweep"
              text="Vider la base"
              [fullWidth]="true"
              [disabled]="busy()"
              backgroundColor="var(--app-btn-danger-bg)"
              tint="var(--app-btn-danger-fg)"
              textColor="var(--app-btn-danger-fg)"
              (clicked)="run('clear')"
            />
          </div>
          <!-- Ligne 2 : 4 boutons verts (reconnexion / token / WS / état unsynced) — fond vert/rouge selon l'état. -->
          <div class="tools__row">
            <app-action-icon-with-text-button icon="refresh" text="Se reconnecter" [fullWidth]="true" [backgroundColor]="tokenColor()" (clicked)="relogin()" />
            <app-action-icon-with-text-button icon="check_circle" text="Vérifier le token" [fullWidth]="true" [disabled]="busy()" [backgroundColor]="tokenColor()" (clicked)="run('token')" />
            <app-action-icon-with-text-button
              [icon]="wsConnected() ? 'cloud_done' : 'cloud_alert'"
              [text]="wsConnected() ? 'WS OK' : 'Relancer WS'"
              [fullWidth]="true"
              [clickable]="!wsConnected()"
              [backgroundColor]="wsConnected() ? 'var(--c-medium-green)' : 'var(--c-red-medium)'"
              (clicked)="restartWs()"
            />
            <app-action-icon-with-text-button
              [icon]="hasUnsynced() ? 'cloud_alert' : 'cloud_done'"
              [text]="hasUnsynced() ? 'Données non synchronisées !' : 'Tout est synchronisé'"
              [fullWidth]="true"
              [backgroundColor]="hasUnsynced() ? 'var(--c-red-medium)' : 'var(--c-medium-green)'"
              (clicked)="checkUnsynced()"
            />
          </div>
        </div>

        <div class="split">
          <!-- Master : liste des tables -->
          <div class="split__list">
            <app-titled-divider title="Tables" />
            @if (stats().length === 0) {
              <p class="muted">Aucune entité enregistrée.</p>
            } @else {
              <ul class="list">
                @for (s of stats(); track s.name) {
                  <li>
                    <div class="row">
                      <span class="row__namebox">{{ s.name }}</span>
                      <span class="row__sync"><app-icon [name]="syncIcon(s)" [size]="20" [color]="colorOf(s)" /></span>
                      <span class="row__spacer"></span>
                      <span class="pill" [style.color]="colorOf(s)" [style.background]="pillBg(s)">{{ s.synced }}/{{ s.total }}</span>
                      <!-- Seule la flèche sélectionne la table (pattern Planning) ; fond primaryAction si affichée. -->
                      <app-action-icon-button
                        icon="arrow_right_alt"
                        [size]="44"
                        tint="var(--app-text-primary)"
                        [backgroundColor]="selected() === s.name ? 'var(--app-primary-action)' : 'var(--c-blue-medium)'"
                        (clicked)="select(s.name)"
                      />
                    </div>
                  </li>
                }
              </ul>
            }
          </div>

          <!-- Detail : data grid de la table sélectionnée -->
          <div class="split__detail">
            @if (selected(); as t) {
              <app-titled-divider class="detail__head" [title]="t + ' — ' + rows().length + ' ligne(s)'" />
              @if (rows().length === 0) {
                <p class="muted">Aucune ligne — table vide localement.</p>
              } @else {
                <app-data-grid-pagination-bar
                  [totalCount]="rows().length"
                  [pageSize]="pageSize()"
                  [currentPage]="pageIndex()"
                  [pageSizeOptions]="[25, 50, 100]"
                  (prev)="pageIndex.set(pageIndex() - 1)"
                  (next)="pageIndex.set(pageIndex() + 1)"
                  (pageSizeChange)="pageSize.set($event); pageIndex.set(0)"
                />
                <app-data-table [columns]="columns()" [rows]="pageRows()" />
              }
            } @else {
              <p class="muted detail__empty">Sélectionne une table à gauche pour voir ses lignes.</p>
            }
          </div>
        </div>
      </div>
    </section>
  `,
  styles: [
    `
      .sync {
        width: 100%;
        box-sizing: border-box;
        display: flex;
        flex-direction: column;
      }
      /* Corps : gouttière horizontale via la variable globale ; la title bar reste pleine largeur. */
      .sync__body {
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-6);
        display: flex;
        flex-direction: column;
        gap: var(--space-4);
      }
      .muted {
        margin: 0;
        color: var(--app-text-secondary);
      }
      /* Outils sync : 2 lignes de boutons pleine largeur regroupés par couleur (4 bleus + rouge / 4 verts). */
      .tools {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      /* Wrap quand la fenêtre rétrécit ; min-width par bouton pour éviter d'écraser les libellés. */
      .tools__row {
        display: flex;
        flex-wrap: wrap;
        gap: var(--space-3);
      }
      .tools__row app-action-icon-with-text-button {
        min-width: 150px;
      }
      /* Master-detail : liste à gauche, data grid à droite. */
      .split {
        display: flex;
        gap: var(--page-gutter);
        align-items: flex-start;
      }
      .split__list {
        width: 400px;
        flex-shrink: 0;
      }
      .split__detail {
        flex: 1;
        min-width: 0;
      }
      .detail__head {
        display: block;
        margin-bottom: var(--space-2);
      }
      .detail__empty {
        padding: var(--space-5) 0;
      }
      @media (max-width: 900px) {
        .split {
          flex-direction: column;
        }
        .split__list {
          width: 100%;
        }
      }
      .list {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
      }
      .row {
        display: flex;
        align-items: center;
        gap: var(--space-3);
        width: 100%;
        height: 44px;
        box-sizing: border-box;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
      }
      /* Name box : moitié de la row, fond bgSurface (boxBlue), nom centré. */
      .row__namebox {
        width: 50%;
        box-sizing: border-box;
        padding: 0 12px;
        background: var(--app-bg-surface);
        border-radius: var(--radius-md);
        color: var(--app-text-primary);
        font-size: 14px;
        font-weight: var(--font-weight-medium);
        line-height: 44px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .row__sync {
        flex-shrink: 0;
        width: 20px;
        display: flex;
        justify-content: center;
      }
      .row__spacer {
        flex: 1;
      }
      .pill {
        flex-shrink: 0;
        font-size: 12px;
        font-weight: var(--font-weight-medium);
        padding: 4px 8px;
        border-radius: 8px;
      }
      /* La data-table (composant DS app-data-table) respire sous la barre de pagination. */
      app-data-table {
        margin-top: var(--space-2);
      }
    `,
  ],
})
export class SyncSettingsPage {
  private readonly sync = inject(SyncEngine);
  private readonly auth = inject(AuthService);
  private readonly wsSvc = inject(WebSocketService);
  private readonly stores = inject(SYNCABLE_STORES);
  private readonly snackbar = inject(SnackbarService);
  private readonly router = inject(Router);

  protected readonly wsConnected = this.wsSvc.connected;
  protected readonly stats = toSignal(this.sync.allStats(), { initialValue: [] as SyncStats[] });
  protected readonly busy = signal(false);

  // Validité du token (= isTokenValid Android) : pilotée par "Vérifier le token" ; vert/rouge lignes 3.
  protected readonly tokenValid = signal(true);
  protected tokenColor(): string {
    return this.tokenValid() ? 'var(--c-medium-green)' : 'var(--c-red-medium)';
  }

  // Données non syncées (= hasUnsyncedData Android), dérivées des stats live du registre.
  protected readonly hasUnsynced = computed(() => this.stats().some((s) => s.unsynced > 0 || s.pendingDeletion > 0));

  // Visionneuse de table (master-detail) : table sélectionnée + ses lignes paginées.
  protected readonly selected = signal<string | null>(null);
  protected readonly rows = signal<Record<string, unknown>[]>([]);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(25);
  protected readonly columns = computed(() => {
    const first = this.rows()[0];
    return first ? Object.keys(first) : [];
  });
  protected readonly pageRows = computed(() => {
    const start = this.pageIndex() * this.pageSize();
    return this.rows().slice(start, start + this.pageSize());
  });

  async select(name: string): Promise<void> {
    this.selected.set(name);
    this.pageIndex.set(0);
    await this.refreshSelected();
  }

  private async refreshSelected(): Promise<void> {
    const name = this.selected();
    const store = name ? this.stores.find((s: SyncableStore<SyncRow>) => s.name === name) : undefined;
    this.rows.set(store ? ((await store.getAllLocal()) as unknown as Record<string, unknown>[]) : []);
  }

  async run(op: Op): Promise<void> {
    if (this.busy()) return;
    if (op === 'clear' && !window.confirm('Vider toute la base locale ? (re-téléchargeable via Fusionner / Tout récupérer)')) {
      return;
    }
    this.busy.set(true);
    try {
      switch (op) {
        case 'sync':
          await this.sync.syncAll();
          this.snackbar.success('Synchronisation terminée.');
          break;
        case 'push':
          await this.sync.bulkPushAll();
          this.snackbar.success('Push (tout) terminé.');
          break;
        case 'merge':
          await this.sync.pullMerge();
          this.snackbar.success('Fusion depuis le serveur terminée.');
          break;
        case 'getall':
          // = "Get All" Android : pull-replace en synced=false (test du round-trip pull -> push).
          await this.sync.getAllAsUnsynced();
          this.snackbar.info('Tables récupérées (marquées non syncées).');
          break;
        case 'clear':
          await this.sync.clearAll();
          this.snackbar.success('Base locale vidée.');
          break;
        case 'token':
          await firstValueFrom(this.auth.loadMe());
          this.tokenValid.set(true);
          this.snackbar.success('Token valide ✅');
          break;
      }
      if (this.selected()) await this.refreshSelected();
    } catch (e) {
      if (op === 'token') this.tokenValid.set(false);
      this.snackbar.error('Échec : ' + (e instanceof Error ? e.message : 'erreur réseau / serveur'));
    } finally {
      this.busy.set(false);
    }
  }

  /** = bouton "Re-login" Android : invite à se reconnecter puis renvoie à l'écran de login. */
  relogin(): void {
    this.snackbar.warning('Merci de vous reconnecter pour renouveler la session.');
    this.auth.logout();
    void this.router.navigateByUrl('/login');
  }

  /** = bouton "Restart WS" Android : relance la connexion WebSocket (cliquable seulement si coupée). */
  restartWs(): void {
    if (this.wsConnected()) return;
    this.wsSvc.restart();
    this.snackbar.info('WebSocket relancé.');
  }

  /** = bouton "All synced / Unsynced data!" Android : feedback sur l'état global de sync. */
  checkUnsynced(): void {
    if (this.hasUnsynced()) this.snackbar.warning('Des données non synchronisées existent.');
    else this.snackbar.success('Tout est synchronisé ✅');
  }

  // Couleur d'état (= SyncSettingsScreen.kt) : suppr. en attente rouge / non-sync jaune /
  // vide gris-bleu / tout synced vert.
  protected colorOf(s: SyncStats): string {
    if (s.pendingDeletion > 0) return 'var(--c-red-medium)';
    if (s.unsynced > 0) return 'var(--c-yellow-medium)';
    if (s.total === 0) return 'var(--c-light-gray-blue)';
    return 'var(--c-medium-green)';
  }
  protected syncIcon(s: SyncStats): string {
    if (s.total === 0) return '';
    if (s.pendingDeletion > 0 || s.unsynced > 0) return 'cloud_off';
    return 'cloud_done';
  }
  protected pillBg(s: SyncStats): string {
    return `color-mix(in srgb, ${this.colorOf(s)} 15%, transparent)`;
  }

}
