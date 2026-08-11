/** Notification — forme wire (NotificationOut serveur). Nommée AppNotification pour éviter le global DOM `Notification`. */
export interface AppNotification {
  uuid: string;
  userId: number;
  type: string;
  level: string;
  title: string;
  body?: string | null;
  data?: Record<string, unknown> | null;
  dedupeKey?: string | null;
  createdAt: string | null;
  readAt?: string | null;
  archivedAt?: string | null;
  updatedAt: string | null;
}

export interface LocalNotification extends AppNotification {
  synced: boolean;
  pendingDeletion: boolean;
}
