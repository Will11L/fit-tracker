/** SupersetGroup — forme wire (SupersetGroupOut côté serveur, camelCase). Type A user-scoped. */
export interface SupersetGroup {
  uuid: string;
  userId: number;
  name: string;
  updatedAt: string | null;
}

/** Ligne locale (IndexedDB) = wire + métadonnées de sync (miroir des flags Room). */
export interface LocalSupersetGroup extends SupersetGroup {
  synced: boolean;
  pendingDeletion: boolean;
}
