/** Equipment — forme wire (EquipmentOut serveur). Entité globale (pas de userId), écriture admin. */
export interface Equipment {
  uuid: string;
  name: string;
  updatedAt: string | null;
}

export interface LocalEquipment extends Equipment {
  synced: boolean;
  pendingDeletion: boolean;
}
