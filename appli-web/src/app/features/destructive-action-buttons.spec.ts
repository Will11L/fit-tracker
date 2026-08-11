import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { SnackbarService } from '@core/snackbar/snackbar.service';
import { SyncEngine } from '@core/sync/sync-engine';
import { SYNCABLE_STORES } from '@core/sync/syncable-store';
import { WebSocketService } from '@core/sync/ws.service';
import { LocalNotification } from '@core/models/notification.model';
import { ProfilePage } from './profile/profile-page';
import { SyncSettingsPage } from './settings/sync-settings-page';
import { NotificationsPage } from './notifications/notifications-page';
import { NotificationRepository } from './notifications/notification.repository';

// Cœur de la tâche « [Web] Sweep app-wide — boutons d'action à fonds sémantiques (delete rouge
// partout) » : sur chaque écran sport, l'action DESTRUCTIVE (supprimer / vider) doit ressortir en
// fond rouge plein (--app-btn-danger-bg) + icône ET texte blancs (--app-btn-danger-fg), tandis que
// les actions non destructives voisines (rafraîchir, fusionner, naviguer) gardent un fond neutre.
//
// On vérifie le STYLE EFFECTIVEMENT RENDU (inline style sur le bouton / l'icône / le label), pas un
// input passé : c'est le rendu observable qui prouve « delete rouge / icône blanche ». jsdom
// conserve les valeurs var() sur background/color (déjà éprouvé par food-detail-panel.spec).
//
// Pages couvertes ici = les 3 écrans sport sans dépendance Dexie (montage léger via fakes signal,
// pattern de food-catalogue-page.spec). Le détail nutrition (food-detail-panel) est couvert par son
// propre spec ; les détails muscle/exercice réutilisent À L'IDENTIQUE les mêmes composants + tokens
// littéraux mais exigent un montage Dexie complet (non rentable pour un binding statique).

const DANGER_BG = 'var(--app-btn-danger-bg)';
const DANGER_FG = 'var(--app-btn-danger-fg)';

/** Bouton icône+texte (app-action-icon-with-text-button) portant ce libellé. */
function textBtn(el: HTMLElement, label: string): HTMLButtonElement {
  const btn = Array.from(el.querySelectorAll('button.aitb')).find(
    (b) => (b.querySelector('.aitb__label')?.textContent ?? '').trim() === label,
  ) as HTMLButtonElement | undefined;
  if (!btn) throw new Error(`Bouton « ${label} » introuvable`);
  return btn;
}
/** Bouton icône-seule (app-action-icon-button) portant cette ligature d'icône. */
function iconBtn(el: HTMLElement, icon: string): HTMLButtonElement {
  const btn = Array.from(el.querySelectorAll('app-action-icon-button button')).find(
    (b) => (b.querySelector('.ms')?.textContent ?? '').trim() === icon,
  ) as HTMLButtonElement | undefined;
  if (!btn) throw new Error(`Bouton icône « ${icon} » introuvable`);
  return btn;
}
const iconColor = (btn: HTMLElement) => (btn.querySelector('.ms') as HTMLElement).style.color;
const labelColor = (btn: HTMLElement) => (btn.querySelector('.aitb__label') as HTMLElement).style.color;

function mountProfile(): HTMLElement {
  TestBed.configureTestingModule({
    imports: [ProfilePage],
    providers: [
      // isAuthenticated=false → le constructeur ne déclenche pas loadMe() ; user null = OK (template null-safe).
      { provide: AuthService, useValue: { currentUser: signal(null), isAuthenticated: signal(false), loadMe: () => of(null) } },
      { provide: SyncEngine, useValue: { allStats: () => of([]) } },
      { provide: SnackbarService, useValue: {} },
      { provide: Router, useValue: {} },
    ],
  });
  const fixture = TestBed.createComponent(ProfilePage);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

function mountSync(): HTMLElement {
  TestBed.configureTestingModule({
    imports: [SyncSettingsPage],
    providers: [
      { provide: SyncEngine, useValue: { allStats: () => of([]) } },
      { provide: AuthService, useValue: {} },
      { provide: WebSocketService, useValue: { connected: signal(false) } },
      { provide: SnackbarService, useValue: {} },
      { provide: SYNCABLE_STORES, useValue: [] },
      { provide: Router, useValue: {} },
    ],
  });
  const fixture = TestBed.createComponent(SyncSettingsPage);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

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

function mountNotifications(list: LocalNotification[]): HTMLElement {
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

describe("Sweep app-wide — boutons destructifs à fond sémantique (delete rouge partout)", () => {
  it('Profil : « Supprimer le compte » en rouge plein + icône ET texte blancs ; « Rafraîchir » neutre', () => {
    const el = mountProfile();
    const del = textBtn(el, 'Supprimer le compte');
    expect(del.style.background).toBe(DANGER_BG);
    expect(iconColor(del)).toBe(DANGER_FG);
    expect(labelColor(del)).toBe(DANGER_FG);
    // L'action non destructive voisine ne doit PAS prendre le fond danger.
    expect(textBtn(el, 'Rafraîchir').style.background).not.toBe(DANGER_BG);
  });

  it('Réglages sync : « Vider la base » en rouge plein + icône ET texte blancs ; « Fusionner » neutre', () => {
    const el = mountSync();
    const clear = textBtn(el, 'Vider la base');
    expect(clear.style.background).toBe(DANGER_BG);
    expect(iconColor(clear)).toBe(DANGER_FG);
    expect(labelColor(clear)).toBe(DANGER_FG);
    expect(textBtn(el, 'Fusionner').style.background).not.toBe(DANGER_BG);
  });

  it('Notifications : bouton supprimer en rouge + icône blanche ; bouton navigation neutre', () => {
    const el = mountNotifications([notif()]);
    const del = iconBtn(el, 'delete_sweep');
    expect(del.style.background).toBe(DANGER_BG);
    expect(iconColor(del)).toBe(DANGER_FG);
    // La flèche de navigation reste neutre (pas de fond danger).
    expect(iconBtn(el, 'arrow_right_alt').style.background).not.toBe(DANGER_BG);
  });
});
