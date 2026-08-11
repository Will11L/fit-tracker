/** Quote — forme wire (QuoteOut serveur, camelCase). */
export interface Quote {
  uuid: string;
  userId: number;
  text: string;
  author?: string | null;
  updatedAt: string | null;
}

export interface LocalQuote extends Quote {
  synced: boolean;
  pendingDeletion: boolean;
}
