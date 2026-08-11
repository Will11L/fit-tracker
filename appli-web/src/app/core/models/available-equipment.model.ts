/** AvailableEquipment — forme wire (user-scoped : le matériel que possède l'utilisateur). */
export interface AvailableEquipment {
  uuid: string;
  userId: number;
  name: string;
  updatedAt: string | null;
}

export interface LocalAvailableEquipment extends AvailableEquipment {
  synced: boolean;
  pendingDeletion: boolean;
}
