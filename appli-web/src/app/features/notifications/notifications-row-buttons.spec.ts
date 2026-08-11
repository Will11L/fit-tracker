import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { SyncEngine } from '@core/sync/sync-engine';
import { LocalNotification } from '@core/models/notification.model';
import { NotificationsPage } from './notifications-page';
import { NotificationRepository } from './notification.repository';

// Convention « boutons de rows app-wide » — leg « bleu primaryAction + icône blanche » sur un vrai
// consommateur léger à monter (même pattern que destructive-action-buttons.spec).
// La tâche fait passer la flèche « aller à la source » d'une notif de fond neutre → fond bleu
// (--app-primary-action) + icône blanche (--app-on-accent), sans fond transparent. Le spec destructif
// ne vérifie que « pas de fond danger » sur cette flèche : la couleur bleue POSITIVE n'était pas
// couverte. On vérifie le STYLE EFFECTIVEMENT RENDU (inline style sur bouton/icône), jsdom conserve
// les valeurs var() (déjà éprouvé par food-list-row.spec / destructive-action-buttons.spec).

const PRIMARY_BG = 'var(--app-primary-action)';
const ON_ACCENT = 'var(--app-on-accent)';
const DANGER_BG = 'var(--app-btn-danger-bg)';

function iconBtn(el: HTMLElement, icon: string): HTMLButtonElement {
  const btn = Array.from(el.querySelectorAll('app-action-icon-button button')).find(
    (b) => (b.querySelector('.ms')?.textContent ?? '').trim() === icon,
  ) as HTMLButtonElement | undefined;
  if (!btn) throw new Error(`Bouton icône « ${icon} » introuvable`);
  return btn;
}
const iconColor = (btn: HTMLElement) => (btn.querySelector('.ms') as HTMLElement).style.color;

function notif(over: Partial<LocalNotification> = {}): LocalNotification {
  return {
    uuid: 'n1',
    userId: 1,
    type: 'WORKOUT_REMINDER',
    level: 'INFO',
    title: 'Séance prévue',
    body: null,
    data: null,
    dedupeKey: null,
    createdAt: '2026-06-16T08:00:00Z',
    readAt: null,
    archivedAt: null,
    updatedAt: null,
    synced: true,
    pendingDeletion: false,
    ...over,
  };
}

function mount(list: LocalNotification[]): HTMLElement {
  TestBed.configureTestingModule({
    imports: [NotificationsPage],
    providers: [
      { provide: NotificationRepository, useValue: { notifications: signal(list) } },
      { provide: SyncEngine, useValue: { syncAll: () => Promise.resolve() } },
      { provide: Router, useValue: {} },
    ],
  });
  const fixture = TestBed.createComponent(NotificationsPage);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('NotificationsPage — convention bouton de row (aller à la source = bleu)', () => {
  it('« aller à la source » : fond bleu primaryAction + icône blanche, jamais transparent', () => {
    const el = mount([notif()]);
    const arrow = iconBtn(el, 'arrow_right_alt');
    expect(arrow.style.background).toBe(PRIMARY_BG);
    expect(arrow.style.background).not.toBe('transparent');
    expect(iconColor(arrow)).toBe(ON_ACCENT);
  });

  it('les deux actions de la row portent des fonds sémantiques distincts (bleu vs rouge)', () => {
    const el = mount([notif()]);
    // delete = destructif rouge ; arrow = primaire bleu → fonds colorés différents, aucun transparent.
    expect(iconBtn(el, 'delete_sweep').style.background).toBe(DANGER_BG);
    expect(iconBtn(el, 'arrow_right_alt').style.background).toBe(PRIMARY_BG);
  });
});
