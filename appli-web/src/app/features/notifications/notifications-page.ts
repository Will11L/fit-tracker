import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { LocalNotification } from '@core/models/notification.model';
import { SyncEngine } from '@core/sync/sync-engine';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { EmptyListRow } from '@designsystem/common_components/empty-list-row';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { ConfirmationDialog } from '@designsystem/common_components/confirmation-dialog';
import { AppIcon } from '@designsystem/icons/app-icon';
import { NotificationRepository } from './notification.repository';

/**
 * Écran Notifications — miroir de NotificationsScreen.kt assemblé depuis le design system :
 * ScreenTitleBar + barre résumé inline (= NotificationsSummaryRow.kt : Lues / à sync / Non lues,
 * « Non lues » cliquable = tout marquer lu avec confirmation) + TitledDivider « Boîte de
 * réception » + cartes notif (= NotificationCard de SwipeableNotificationItem.kt : rail gauche,
 * icône par type, titre, date relative, supprimer, naviguer) + 2 ConfirmationDialog.
 * Données offline-first via NotificationRepository.
 *
 * Déviations vs Android : pas de swipe (web → boutons supprimer/naviguer toujours visibles) ;
 * liste en grille responsive (la donnée prend la largeur) au lieu d'une colonne unique.
 */
@Component({
  selector: 'app-notifications-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ScreenTitleBar,
    TitledDivider,
    EmptyListRow,
    ActionIconButton,
    ConfirmationDialog,
    AppIcon,
  ],
  template: `
    <section class="page">
      <app-screen-title-bar title="Notifications" />

      <div class="page__body">
        <!-- Barre résumé (= NotificationsSummaryRow.kt) : item « Non lues » cliquable = tout lire. -->
        <div class="summary">
          <span class="summary__item">
            <app-icon name="check" [size]="18" color="var(--c-medium-green)" />
            <span class="summary__value">{{ readCount() }}</span>
            <span class="summary__label">Lues</span>
          </span>
          <span class="summary__divider"></span>
          <span class="summary__item">
            <app-icon name="cloud_off" [size]="18" color="var(--c-yellow-medium)" />
            <span class="summary__value">{{ unsyncedCount() }}</span>
          </span>
          <span class="summary__divider"></span>
          <button
            type="button"
            class="summary__item summary__item--btn"
            [disabled]="unreadCount() === 0"
            (click)="showReadAll.set(true)"
          >
            <app-icon name="mail" [size]="18" color="var(--app-accent-text)" />
            <span class="summary__value">{{ unreadCount() }}</span>
            <span class="summary__label">Non lues</span>
          </button>
        </div>

        <app-titled-divider title="Boîte de réception" />

        @if (notifications().length === 0) {
          <app-empty-list-row text="Aucune notification" icon="notifications_off" />
        } @else {
          <div class="page__list">
            @for (n of notifications(); track n.uuid) {
              <div class="ntf" [class.ntf--read]="!!n.readAt" (click)="markRead(n)">
                <span class="ntf__rail" [style.background]="n.readAt ? 'var(--app-text-tertiary)' : 'var(--app-primary-action)'"></span>
                <div class="ntf__body">
                  <div class="ntf__top">
                    <app-icon
                      [name]="iconFor(n.type)"
                      [size]="24"
                      [color]="n.readAt ? 'var(--app-text-tertiary)' : 'var(--app-primary-action)'"
                    />
                    <span class="ntf__title" [style.color]="n.readAt ? 'var(--app-text-tertiary)' : 'var(--app-accent-text)'">{{ n.title }}</span>
                    <span class="ntf__date" [style.color]="n.readAt ? 'var(--app-text-tertiary)' : 'var(--app-text-secondary)'">{{ relTime(n.createdAt) }}</span>
                    <span class="ntf__actions" (click)="$event.stopPropagation()">
                      <app-action-icon-button
                        icon="delete_sweep"
                        backgroundColor="var(--app-btn-danger-bg)"
                        tint="var(--app-btn-danger-fg)"
                        (clicked)="toDelete.set(n)"
                      />
                      <!-- Aller à la source = action principale : fond bleu primaryAction + icône blanche. -->
                      <app-action-icon-button
                        icon="arrow_right_alt"
                        backgroundColor="var(--app-primary-action)"
                        tint="var(--app-on-accent)"
                        (clicked)="navigateTo(n)"
                      />
                    </span>
                  </div>
                  @if (n.body) {
                    <span class="ntf__text" [style.color]="n.readAt ? 'var(--app-text-tertiary)' : 'var(--app-text-secondary)'">{{ n.body }}</span>
                  }
                </div>
              </div>
            }
          </div>
        }
      </div>

      <app-confirmation-dialog
        [open]="toDelete() !== null"
        title="Confirmer la suppression"
        message="Supprimer cette notification ?"
        confirmButtonText="Supprimer"
        dismissButtonText="Annuler"
        confirmButtonColor="var(--c-red-medium)"
        (confirm)="confirmDelete()"
        (dismiss)="toDelete.set(null)"
      />

      <app-confirmation-dialog
        [open]="showReadAll()"
        title="Tout marquer comme lu"
        message="Marquer toutes les notifications non lues comme lues ?"
        confirmButtonText="Tout marquer"
        dismissButtonText="Annuler"
        confirmButtonColor="var(--c-medium-green)"
        (confirm)="confirmReadAll()"
        (dismiss)="showReadAll.set(false)"
      />
    </section>
  `,
  styles: [
    `
      /* Title bar pleine largeur (hors corps) ; le corps prend la gouttière (--page-gutter). */
      .page__body {
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-4);
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      /* Barre résumé inline (= NotificationsSummaryRow.kt : bgRecessed, SpaceAround, dividers). */
      .summary {
        display: flex;
        align-items: center;
        justify-content: space-around;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-2) var(--space-3);
      }
      .summary__item {
        display: inline-flex;
        align-items: center;
        gap: var(--space-2);
        min-width: 0;
      }
      .summary__item--btn {
        border: none;
        background: transparent;
        font: inherit;
        padding: 4px var(--space-2);
        border-radius: var(--radius-md);
        cursor: pointer;
      }
      .summary__item--btn:hover:not(:disabled) {
        background: color-mix(in srgb, var(--app-text-primary) 8%, transparent);
      }
      .summary__item--btn:disabled {
        cursor: default;
      }
      .summary__value {
        color: var(--app-text-primary);
        font-size: 13px;
        font-weight: 600;
        white-space: nowrap;
      }
      .summary__label {
        color: var(--app-text-tertiary);
        font-size: 11px;
        white-space: nowrap;
      }
      .summary__divider {
        flex: 0 0 auto;
        width: 1px;
        height: 18px;
        margin: 0 var(--space-1);
        background: var(--app-divider);
      }
      /* Grille de cartes notif responsive : voir plus d'un coup (la donnée prend la largeur). */
      .page__list {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
        gap: var(--space-2);
        align-items: start;
      }
      /* Carte (= NotificationCard) : gauche rectangle, droite arrondie 14px. */
      .ntf {
        display: flex;
        cursor: pointer;
        border-radius: 0 14px 14px 0;
        overflow: hidden;
      }
      /* Rail : non lue = rectangle primaryAction ; lue = arrondi à gauche, textTertiary. */
      .ntf__rail {
        flex: 0 0 auto;
        width: 3px;
      }
      .ntf--read .ntf__rail {
        border-radius: 14px 0 0 14px;
      }
      .ntf__body {
        flex: 1;
        min-width: 0;
        background: var(--app-bg-recessed);
        padding: var(--space-2) var(--space-2) var(--space-3) var(--space-3);
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .ntf__top {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      .ntf__title {
        flex: 1;
        min-width: 0;
        font-size: 13px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .ntf__date {
        flex: 0 0 auto;
        font-size: 12px;
        white-space: nowrap;
      }
      .ntf__actions {
        flex: 0 0 auto;
        display: inline-flex;
        align-items: center;
        gap: var(--space-1);
      }
      .ntf__text {
        font-size: var(--font-size-caption);
        padding-left: var(--space-2);
      }
    `,
  ],
})
export class NotificationsPage {
  private readonly repo = inject(NotificationRepository);
  private readonly sync = inject(SyncEngine);
  private readonly router = inject(Router);

  protected readonly notifications = this.repo.notifications;
  protected readonly toDelete = signal<LocalNotification | null>(null);
  protected readonly showReadAll = signal(false);

  protected readonly unreadCount = computed(() => this.notifications().filter((n) => !n.readAt).length);
  protected readonly readCount = computed(() => this.notifications().length - this.unreadCount());
  protected readonly unsyncedCount = computed(() => this.notifications().filter((n) => !n.synced).length);

  constructor() {
    void this.sync.syncAll().catch(() => undefined);
  }

  protected markRead(n: LocalNotification): void {
    if (!n.readAt) void this.repo.markAsRead(n.uuid);
  }

  protected confirmDelete(): void {
    const n = this.toDelete();
    if (n) void this.repo.remove(n.uuid);
    this.toDelete.set(null);
  }

  protected confirmReadAll(): void {
    void this.repo.markAllAsRead();
    this.showReadAll.set(false);
  }

  /**
   * Navigation depuis la notif (= NotificationNavigationMapper.kt, adapté aux routes web) :
   * marque lue puis route vers l'écran cible. CHAT → home (pas de conversations côté web).
   */
  protected navigateTo(n: LocalNotification): void {
    this.markRead(n);
    void this.router.navigateByUrl(this.routeFor(n.type));
  }

  private routeFor(type: string): string {
    switch (type?.toUpperCase()) {
      case 'TIMER_DONE':
        return '/chrono';
      case 'WORKOUT_REMINDER':
        return '/planning';
      case 'TASK_REMINDER':
      case 'ROUTINE_PERIOD_START':
      case 'ROUTINE_PERIOD_END':
        return '/routines';
      case 'SYNC_ERROR':
        return '/sync';
      case 'EXERCISE':
        return '/exercises';
      default:
        return '/home'; // SYNC_DONE, CHAT, UNKNOWN — fallback robuste (cf. mapper Android)
    }
  }

  /** Icône par type wire UPPER_CASE (= notificationTypeIcon de notificationUtils.kt). */
  protected iconFor(type: string): string {
    switch (type?.toUpperCase()) {
      case 'TIMER_DONE':
        return 'timer';
      case 'WORKOUT_REMINDER':
      case 'EXERCISE':
        return 'fitness_center';
      case 'TASK_REMINDER':
        return 'list_alt';
      case 'ROUTINE_PERIOD_START':
      case 'ROUTINE_PERIOD_END':
        return 'av_timer';
      case 'SYNC_DONE':
        return 'cloud_done';
      case 'SYNC_ERROR':
        return 'cloud_off';
      case 'CHAT':
        return 'chat';
      default:
        return 'question_mark';
    }
  }

  /** Date relative (= CustomDateUtils.formatRelativeTime, libellés FR rel_time_*). */
  protected relTime(iso: string | null): string {
    if (!iso) return '';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return '';
    const min = Math.floor((Date.now() - d.getTime()) / 60000);
    if (min < 1) return "à l'instant";
    if (min < 60) return `il y a ${min} min`;
    const h = Math.floor(min / 60);
    if (h < 24) return `il y a ${h} h`;
    const j = Math.floor(h / 24);
    if (j === 1) return 'hier';
    if (j < 7) return `il y a ${j} j`;
    return d.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short' });
  }
}
