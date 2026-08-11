/** Meal — forme wire (repas du journal). name user-typed, jamais traduit. date "YYYY-MM-DD". */
export interface Meal {
  uuid: string;
  userId: number;
  date: string;
  name: string;
  orderIndex: number;
  /** "HH:MM" heure reelle de ce repas (facultative). Surclasse le defaultTime du preset a l'affichage.
   *  Optionnel : les repas legacy crees avant ce champ n'ont pas la propriete (traitee comme null). */
  time?: string | null;
  /** FK meal_presets.uuid (lien stable vers la periode). null = repas ad hoc ou legacy (appariement par nom). */
  presetUuid: string | null;
  updatedAt: string | null;
}

export interface LocalMeal extends Meal {
  synced: boolean;
  pendingDeletion: boolean;
}
