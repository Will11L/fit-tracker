import { Injectable, effect, inject, signal } from '@angular/core';
import { API_BASE_URL } from '@core/api/api.config';
import { getClientId } from '@core/auth/client-id';
import { TokenStore } from '@core/auth/token-store';
import { SYNCABLE_STORES, SyncRow } from './syncable-store';

interface WsMessage {
  type?: string;
  payload?: Record<string, unknown>;
  uuid?: string;
}

const SUFFIX_LEN = '_updated'.length; // = '_deleted'.length = 8

/**
 * Temps réel — miroir de WebSocketManager (Android), mais générique : route les events
 * `<wsKey>_updated` / `<wsKey>_deleted` vers le bon store via le registre, puis applique
 * dans Dexie (les liveQuery rafraîchissent l'UI). Connecte avec le MÊME client_id que les
 * écritures REST -> le serveur n'écho jamais nos propres changements (exclude_client_id).
 *
 * Cycle de vie piloté par l'auth (effect) : connecte quand authentifié, coupe au logout.
 */
@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private readonly tokenStore = inject(TokenStore);
  private readonly byWsKey = new Map(inject(SYNCABLE_STORES).map((s) => [s.wsKey, s]));

  private socket: WebSocket | null = null;
  private pingTimer: ReturnType<typeof setInterval> | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private wantConnected = false;

  private readonly _connected = signal(false);
  readonly connected = this._connected.asReadonly();

  constructor() {
    effect(() => {
      if (this.tokenStore.isAuthenticated()) this.connect();
      else this.disconnect();
    });
  }

  /**
   * Relance manuelle de la connexion (bouton "Relancer WS" page Sync, miroir
   * restartWebSocket Android) — no-op si déjà connectée ou non authentifié.
   */
  restart(): void {
    if (this.tokenStore.isAuthenticated()) this.connect();
  }

  private connect(): void {
    this.wantConnected = true;
    const token = this.tokenStore.accessToken();
    if (!token) return;
    if (
      this.socket &&
      (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)
    ) {
      return;
    }
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const url =
      `${scheme}://${window.location.host}${API_BASE_URL}/ws` +
      `?access_token=${encodeURIComponent(token)}&client=web&v=1&client_id=${encodeURIComponent(getClientId())}`;

    const sock = new WebSocket(url);
    this.socket = sock;
    sock.onopen = () => {
      if (sock !== this.socket) return;
      this._connected.set(true);
      this.startPing();
    };
    sock.onmessage = (ev: MessageEvent) => {
      if (sock === this.socket) this.onMessage(String(ev.data));
    };
    sock.onclose = () => {
      if (sock !== this.socket) return;
      this._connected.set(false);
      this.stopPing();
      this.scheduleReconnect();
    };
    sock.onerror = () => {
      /* onclose suivra et gère la reconnexion */
    };
  }

  private disconnect(): void {
    this.wantConnected = false;
    this.clearReconnect();
    this.stopPing();
    const s = this.socket;
    this.socket = null;
    this._connected.set(false);
    if (s) {
      try {
        s.close(1000, 'logout');
      } catch {
        /* ignore */
      }
    }
  }

  private onMessage(data: string): void {
    let msg: WsMessage;
    try {
      msg = JSON.parse(data) as WsMessage;
    } catch {
      return;
    }
    const type = msg.type;
    if (!type) return; // ignore client_id / pong / inconnu

    if (type.endsWith('_updated') && msg.payload) {
      const store = this.byWsKey.get(type.slice(0, -SUFFIX_LEN));
      if (store) {
        const row = { ...msg.payload, synced: true, pendingDeletion: false } as unknown as SyncRow;
        void store.bulkPutLocal([row]);
      }
    } else if (type.endsWith('_deleted') && msg.uuid) {
      const store = this.byWsKey.get(type.slice(0, -SUFFIX_LEN));
      if (store) void store.deleteLocal(msg.uuid);
    }
  }

  private scheduleReconnect(): void {
    if (!this.wantConnected || this.reconnectTimer) return;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      if (this.wantConnected) this.connect();
    }, 3000);
  }

  private clearReconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  private startPing(): void {
    this.stopPing();
    this.pingTimer = setInterval(() => {
      if (this.socket?.readyState === WebSocket.OPEN) this.socket.send('ping');
    }, 30000);
  }

  private stopPing(): void {
    if (this.pingTimer) {
      clearInterval(this.pingTimer);
      this.pingTimer = null;
    }
  }
}
